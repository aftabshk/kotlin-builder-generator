package com.github.affishaikh.kotlinbuildergenerator.action

import com.github.affishaikh.kotlinbuildergenerator.domain.KotlinFileType
import com.github.affishaikh.kotlinbuildergenerator.domain.Parameter
import com.github.affishaikh.kotlinbuildergenerator.ui.PackageNameInputPrompt
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.impl.PackageFragmentDescriptorImpl
import org.jetbrains.kotlin.idea.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.refactoring.memberInfo.qualifiedClassNameForRendering
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlinx.serialization.compiler.resolve.toClassDescriptor

class GenerateBuilder : SelfTargetingIntention<KtClass>(
    KtClass::class.java,
    "Generate builder"
) {
    override fun applyTo(element: KtClass, editor: Editor?) {
        val packageNameInputPrompt = PackageNameInputPrompt(element.project)
        val code = generateCode(element, packageNameInputPrompt.qualifiedName())

        createFile(element, code, packageNameInputPrompt.packageDirectory())
    }

    private fun generateCode(element: KtClass, selectedPackageName: String): String {
        val mainClassPackageName = element.qualifiedClassNameForRendering()
        val parameters = element.primaryConstructor?.valueParameters!!.map {
            Parameter(it.name!!, it.type()!!)
        }
        val mainClass = createClassFromParams(element.name!!, parameters)
        val allBuilderClasses = getAllClassesThatNeedsABuilder(parameters)
        val importStatementsForDependentClasses = getAllImportStatements(allBuilderClasses, selectedPackageName)

        val importStatements = if(mainClassPackageName == selectedPackageName) {
            importStatementsForDependentClasses
        } else {
            importStatementsForDependentClasses.plus("import $mainClassPackageName")
        }

        val dependentBuilderCodes = allBuilderClasses.joinToString("\n") {
            val params = it.type.toClassDescriptor?.unsubstitutedPrimaryConstructor?.valueParameters!!.map { valueParam ->
                Parameter(valueParam.name.identifier, valueParam.type!!)
            }
            createClassFromParams(it.typeName(), params)
        }

        return "package $selectedPackageName\n\n${importStatements.joinToString("\n")}\n\n$mainClass\n\n$dependentBuilderCodes"

    }

    private fun getAllImportStatements(parameters: List<Parameter>, packageName: String): List<String> {
        return parameters.fold(emptyList()) { importStatements, it ->
            if (!isInSamePackage(it.type, packageName)) {
                val importStatement =
                    "import ${(it.type.toClassDescriptor?.containingDeclaration as PackageFragmentDescriptorImpl).fqName}.${it.typeName()}"
                importStatements + listOf(importStatement)
            } else {
                importStatements
            }
        }
    }

    private fun isInSamePackage(type: KotlinType, packageName: String): Boolean {
        return (type.toClassDescriptor?.containingDeclaration as PackageFragmentDescriptorImpl).fqName.toString() == packageName
    }

    private fun getAllClassesThatNeedsABuilder(parameters: List<Parameter>): List<Parameter> {
        if (parameters.isEmpty()) return emptyList()

        val params = parameters.filter { doesNeedABuilder(it.type) }

        val newParams = params.map {
            it.type.toClassDescriptor?.unsubstitutedPrimaryConstructor?.valueParameters!!.map {valueParam ->
                Parameter(valueParam.name.identifier, valueParam.type)
            }
        }.flatten()

        return listOf(params, getAllClassesThatNeedsABuilder(newParams)).flatten()
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
            KotlinBuiltIns.isMapOrNullableMap(type)
    }

    override fun isApplicableTo(element: KtClass, caretOffset: Int): Boolean {
        return true
    }

    private fun createFile(element: KtClass, classCode: String, selectedDirectory: PsiDirectory) {
        val psiFileFactory = PsiFileFactory.getInstance(element.project)
        val file = psiFileFactory.createFileFromText("${element.name}Builder.kt", KotlinFileType(), classCode)
        selectedDirectory.add(file)
    }

    private fun createClassFromParams(className: String, parameters: List<Parameter>): String {
        val prefix = "data class ${className}Builder(\n"
        val params = parameters.joinToString(",\n") {
            "val ${it.name}: ${it.type} = ${defaultValue(it.type)}"
        }
        val functionBody = createBuildFunction(className, parameters.map { it.name })
        return "${prefix}${params}\n) {\n$functionBody\n}"
    }

    private fun createBuildFunction(className: String, parameterNames: List<String>): String {
        val declaration = "fun build(): ${className} {\nreturn ${className}(\n"
        val params = parameterNames.map {
            "$it = $it"
        }.joinToString(",\n")
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
            parameterType.isMarkedNullable -> "null"
            doesNeedABuilder(parameterType) -> "${parameterType}Builder().build()"
            else -> ""
        }
    }
}