package com.github.affishaikh.kotlinbuildergenerator.action

import com.github.affishaikh.kotlinbuildergenerator.domain.ClassInfo
import com.github.affishaikh.kotlinbuildergenerator.domain.Parameter
import com.github.affishaikh.kotlinbuildergenerator.services.FileService
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.PackageFragmentDescriptorImpl
import org.jetbrains.kotlin.idea.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.refactoring.memberInfo.qualifiedClassNameForRendering
import org.jetbrains.kotlin.js.descriptorUtils.nameIfStandardType
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlinx.serialization.compiler.resolve.toClassDescriptor

class GenerateBuilder : SelfTargetingIntention<KtClass>(
    KtClass::class.java,
    "Generate builder"
) {
    override fun applyTo(element: KtClass, editor: Editor?) {
        val code = generateCode(element)
        createFile(element, code)
    }

    override fun isApplicableTo(element: KtClass, caretOffset: Int): Boolean {
        val numberOfProperties = element.primaryConstructor?.valueParameters?.size ?: return false
        return numberOfProperties > 0
    }

    private fun generateCode(element: KtClass): String {
        val packageName = extractPackageNameFrom(element.qualifiedClassNameForRendering())
        val classProperties = element.properties()
        val allBuilderClasses = getAllClassesThatNeedsABuilder(classProperties)
        val importStatements = getAllImportStatements(classProperties, packageName)
        val dependentBuilderCodes = listOf(ClassInfo(element.name!!, null, classProperties))
            .plus(allBuilderClasses)
            .joinToString("\n") {
                createClassFromParams(it.name, it.parameters)
            }
        return "package $packageName\n\n${joinImportStatements(importStatements)}$dependentBuilderCodes"
    }

    private fun joinImportStatements(importStatements: Set<String>): String {
        return if (importStatements.isNotEmpty())
            "${importStatements.joinToString("\n")}\n\n"
        else ""
    }

    private fun extractPackageNameFrom(qualifiedName: String): String {
        val packageFragments = qualifiedName.split(".")
        return packageFragments.take(packageFragments.size - 1).joinToString(".")
    }

    private fun KtClass.properties(): List<Parameter> {
        return this.primaryConstructor?.valueParameters?.map {
            Parameter(it.name!!, it.type()!!)
        } ?: emptyList()
    }

    private fun getAllImportStatements(properties: List<Parameter>, packageName: String): Set<String> {
        if (properties.isEmpty()) {
            return emptySet()
        }

        return properties.fold(emptySet()) { acc, prop ->
            val importStatement = if (doesNeedAImport(prop.type, packageName)) {
                setOf(createImportStatement(prop.type, prop.typeName(), packageName))
            } else {
                emptySet()
            }

            if (doesNeedABuilder(prop.type)) {
                acc.plus(importStatement.plus(getAllImportStatements(prop.type.properties(), packageName)))
            } else {
                acc.plus(importStatement)
            }
        }
    }

    private fun doesNeedAImport(type: KotlinType, packageName: String): Boolean {
        return isNotStandardType(type) && (isNotInSamePackage(type, packageName) || isEnumClass(type))
    }

    private fun isNotStandardType(type: KotlinType) = type.nameIfStandardType == null

    private fun createImportStatement(type: KotlinType, className: String, packageName: String): String {
        val importStatement = "import ${packagePrefix(type)}.$className"

        return if (isEnumClass(type) && isNotInSamePackage(type, packageName))
            "$importStatement\n$importStatement.${valueForEnum(type)}"
        else if (isEnumClass(type))
            "$importStatement.${valueForEnum(type)}"
        else importStatement
    }

    private fun isNotInSamePackage(type: KotlinType, packageName: String): Boolean {
        return packagePrefix(type).toString() != packageName
    }

    private fun packagePrefix(type: KotlinType) =
        (type.toClassDescriptor?.containingDeclaration as PackageFragmentDescriptorImpl).fqName

    private fun getAllClassesThatNeedsABuilder(parameters: List<Parameter>): List<ClassInfo> {
        if (parameters.isEmpty()) return emptyList()

        val builderClassInfo = parameters
            .filter { doesNeedABuilder(it.type) }
            .map {
                ClassInfo(it.typeName(), it.type, it.type.properties())
            }

        val newParams = builderClassInfo.flatMap {
            it.parameters
        }

        return listOf(builderClassInfo, getAllClassesThatNeedsABuilder(newParams)).flatten()
    }

    private fun KotlinType.properties(): List<Parameter> {
        return getConstructorParameters(this).map { valueParam ->
            Parameter(valueParam.name.identifier, valueParam.type)
        }
    }

    private fun doesNeedABuilder(it: KotlinType) =
        !isKotlinBuiltinType(it) && it.toClassDescriptor?.unsubstitutedPrimaryConstructor?.valueParameters?.isNotEmpty() ?: false

    private fun isKotlinBuiltinType(type: KotlinType): Boolean {
        return KotlinBuiltIns.isBoolean(type) ||
            KotlinBuiltIns.isChar(type) ||
            KotlinBuiltIns.isDouble(type) ||
            KotlinBuiltIns.isFloat(type) ||
            KotlinBuiltIns.isInt(type) ||
            KotlinBuiltIns.isLong(type) ||
            KotlinBuiltIns.isShort(type) ||
            KotlinBuiltIns.isCollectionOrNullableCollection(type) ||
            KotlinBuiltIns.isNullableAny(type) ||
            KotlinBuiltIns.isString(type) ||
            KotlinBuiltIns.isListOrNullableList(type) ||
            KotlinBuiltIns.isSetOrNullableSet(type) ||
            KotlinBuiltIns.isMapOrNullableMap(type) ||
            isBigDecimal(type) ||
            isEnumClass(type)
    }

    private fun createFile(element: KtClass, classCode: String) {
        val fileService = FileService()
        fileService.createFile(element, classCode)
    }

    private fun createClassFromParams(className: String, parameters: List<Parameter>): String {
        val classHeader = "data class ${className}Builder(\n"
        val properties = parameters.joinToString(",\n") {
            "val ${it.name}: ${it.type} = ${defaultValue(it.type)}"
        }
        val functionBody = createBuildFunction(className, parameters.map { it.name })
        return "${classHeader}${properties}\n) {\n$functionBody\n}"
    }

    private fun createBuildFunction(className: String, parameterNames: List<String>): String {
        val declaration = "fun build(): $className {\nreturn ${className}(\n"
        val params = parameterNames.joinToString(",\n") {
            "$it = $it"
        }
        return "$declaration$params\n)\n}"
    }

    private fun defaultValue(parameterType: KotlinType): String {
        return when {
            KotlinBuiltIns.isBoolean(parameterType) -> "false"
            KotlinBuiltIns.isChar(parameterType) -> "''"
            KotlinBuiltIns.isDouble(parameterType) -> "0.0"
            KotlinBuiltIns.isFloat(parameterType) -> "0.0f"
            KotlinBuiltIns.isInt(parameterType) || KotlinBuiltIns.isLong(parameterType) || KotlinBuiltIns.isShort(parameterType) -> "0"
            KotlinBuiltIns.isCollectionOrNullableCollection(parameterType) -> "arrayOf()"
            KotlinBuiltIns.isNullableAny(parameterType) -> "null"
            KotlinBuiltIns.isString(parameterType) -> "\"\""
            KotlinBuiltIns.isListOrNullableList(parameterType) -> "listOf()"
            KotlinBuiltIns.isSetOrNullableSet(parameterType) -> "setOf()"
            KotlinBuiltIns.isMapOrNullableMap(parameterType) -> "mapOf()"
            isBigDecimal(parameterType) -> "BigDecimal.ZERO"
            parameterType.isMarkedNullable -> "null"
            isEnumClass(parameterType) -> valueForEnum(parameterType)
            doesNeedABuilder(parameterType) -> "${parameterType}Builder().build()"
            hasNowFunction(parameterType) -> initiateWithNow(parameterType)
            else -> ""
        }
    }

    private fun valueForEnum(parameterType: KotlinType): String {
        val enumConstructorParams = getConstructorParameters(parameterType).map { valueParam ->
            valueParam.name.identifier
        } + listOf("name", "ordinal")

        val enumVariableNames = parameterType.memberScope.getVariableNames().map { it.toString() }
        return (enumVariableNames - enumConstructorParams).first().toString()
    }

    private fun getConstructorParameters(parameterType: KotlinType): MutableList<ValueParameterDescriptor> =
        parameterType.toClassDescriptor?.unsubstitutedPrimaryConstructor?.valueParameters!!

    private fun isBigDecimal(parameterType: KotlinType): Boolean {
        return parameterType.constructor.declarationDescriptor?.name?.identifier == "BigDecimal"
    }

    private fun isEnumClass(parameterType: KotlinType): Boolean {
        return parameterType.toClassDescriptor?.kind?.name == "ENUM_CLASS"
    }

    private fun hasNowFunction(parameterType: KotlinType): Boolean {
        val name = parameterType.constructor.declarationDescriptor?.name?.identifier
        return temporalHavingNowFunction.contains(name)
    }

    private fun initiateWithNow(parameterType: KotlinType): String {
        val temporal = parameterType.constructor.declarationDescriptor?.name?.identifier
        return """$temporal.now()"""
    }

    private val temporalHavingNowFunction = listOf(
        "HijrahDate", "Instant", "JapaneseDate", "LocalDate", "LocalDateTime", "LocalTime",
        "MinguoDate", "OffsetDateTime", "OffsetTime", "ThaiBuddhistDate", "Year", "YearMonth",
        "ZonedDateTime"
    )
}
