package com.github.affishaikh.kotlinbuildergenerator.action

import com.github.affishaikh.kotlinbuildergenerator.domain.ClassInfo
import com.github.affishaikh.kotlinbuildergenerator.domain.KotlinFileType
import com.github.affishaikh.kotlinbuildergenerator.domain.Parameter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
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
        val packageName = extractPackageNameFrom(element.qualifiedClassNameForRendering())
        val code = generateCode(element, packageName)

        createFile(element, code)
    }

    private fun generateCode(element: KtClass, selectedPackageName: String): String {
        val rootClassPackageName = element.qualifiedClassNameForRendering()
        val classProperties = element.properties()
        val allBuilderClasses = getAllClassesThatNeedsABuilder(classProperties)
        val importStatements = getAllImportStatements(classProperties, selectedPackageName)
            .plus(importStatementForRootClass(rootClassPackageName, selectedPackageName))
        val dependentBuilderCodes = listOf(ClassInfo(element.name!!, null, classProperties))
            .plus(allBuilderClasses)
            .joinToString("\n") {
                createClassFromParams(it.name, it.parameters)
            }
        return "package $selectedPackageName\n\n${importStatements.joinToString("\n")}\n\n$dependentBuilderCodes"
    }

    private fun importStatementForRootClass(rootClassPackageName: String, selectedPackageName: String): Set<String> {
        return if (rootClassPackageName == selectedPackageName) {
            emptySet()
        } else {
            setOf("import $rootClassPackageName")
        }
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
                setOf(createImportStatement(prop.type, prop.typeName()))
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
        return type.nameIfStandardType == null && !isInSamePackage(type, packageName)
    }

    private fun createImportStatement(type: KotlinType, name: String): String =
        "import ${(type.toClassDescriptor?.containingDeclaration as PackageFragmentDescriptorImpl).fqName}.$name"

    private fun isInSamePackage(type: KotlinType, packageName: String): Boolean {
        return (type.toClassDescriptor?.containingDeclaration as PackageFragmentDescriptorImpl).fqName.toString() == packageName
    }

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
        return this.toClassDescriptor?.unsubstitutedPrimaryConstructor?.valueParameters!!.map { valueParam ->
            Parameter(valueParam.name.identifier, valueParam.type)
        }
    }

    private fun doesNeedABuilder(it: KotlinType) =
        !isKotlinBuiltinType(it) && it.toClassDescriptor?.unsubstitutedPrimaryConstructor?.valueParameters!!.isNotEmpty()

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

    override fun isApplicableTo(element: KtClass, caretOffset: Int): Boolean {
        val numberOfProperties = element.primaryConstructor?.valueParameters?.size ?: return false
        return numberOfProperties > 1
    }

    private fun createFile(element: KtClass, classCode: String) {
        val containingDirectory = element.containingFile.containingDirectory
        val psiFileFactory = PsiFileFactory.getInstance(element.project)
        val file = psiFileFactory.createFileFromText("${element.name}Builder.kt", KotlinFileType(), classCode)
        containingDirectory.add(file)
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
            isEnumClass(parameterType) -> ""
            doesNeedABuilder(parameterType) -> "${parameterType}Builder().build()"
            else -> ""
        }
    }

    private fun isBigDecimal(parameterType: KotlinType): Boolean {
        return parameterType.constructor.declarationDescriptor?.name?.identifier == "BigDecimal"
    }

    private fun isEnumClass(parameterType: KotlinType): Boolean {
        return parameterType.toClassDescriptor?.kind?.name == "ENUM_CLASS"
    }
}

