package com.github.affishaikh.kotlinbuildergenerator.action

import com.github.affishaikh.kotlinbuildergenerator.domain.KotlinFileType
import com.github.affishaikh.kotlinbuildergenerator.domain.Parameter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
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
        val packageName = extractPackageNameFrom(element.qualifiedClassNameForRendering())
        val parameters = element.primaryConstructor?.valueParameters!!.map {
            Parameter(it.name!!, it.type()!!)
        }

        val packageStatement = "package ${packageName}\n\n"

        val mainClass = createClassFromParams(element.name!!, parameters)

        val code = getAllClassesThatNeedsABuilder(parameters).map {
            val params = it.type.toClassDescriptor?.unsubstitutedPrimaryConstructor?.valueParameters!!.map {valueParam ->
                Parameter(valueParam.name.identifier, valueParam.type!!)
            }
            createClassFromParams(it.type.toString(), params)
        }.joinToString("\n")
        createFile(element, "$packageStatement$mainClass\n$code")
    }

    private fun getAllClassesThatNeedsABuilder(parameters: List<Parameter>): List<Parameter> {
        if (parameters.isEmpty()) return emptyList()

        val params = parameters.filter {
            !isKotlinBuiltinType(it.type) && it.type.toClassDescriptor?.unsubstitutedPrimaryConstructor?.valueParameters!!.isNotEmpty()
        }

        val newParams = params.map {
            it.type.toClassDescriptor?.unsubstitutedPrimaryConstructor?.valueParameters!!.map {valueParam ->
                Parameter(valueParam.name.identifier, valueParam.type)
            }
        }.flatten()

        return listOf(params, getAllClassesThatNeedsABuilder(newParams)).flatten()
    }

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

    private fun createFile(element: KtClass, classCode: String) {
        val containingDirectory = element.containingFile.containingDirectory
        val psiFileFactory = PsiFileFactory.getInstance(element.project)
        val file = psiFileFactory.createFileFromText("${element.name}Builder.kt", KotlinFileType(), classCode)
        containingDirectory.add(file)
    }

    private fun createClassFromParams(className: String, parameters: List<Parameter>): String {
        val prefix = "data class ${className}Builder(\n"
        val params = parameters.map {
            "val ${it.name}: ${it.type} = ${defaultValue(it.type)}"
        }.joinToString(",\n")
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
            else -> ""
        }
    }

    private fun extractPackageNameFrom(qualifiedName: String): String {
        val packageFragments = qualifiedName.split(".")
        return packageFragments.take(packageFragments.size - 1).joinToString(".")
    }
}