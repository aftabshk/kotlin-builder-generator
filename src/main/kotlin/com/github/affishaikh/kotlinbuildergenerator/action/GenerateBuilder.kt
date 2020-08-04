package com.github.affishaikh.kotlinbuildergenerator.action

import com.github.affishaikh.kotlinbuildergenerator.domain.KotlinFileType
import com.github.affishaikh.kotlinbuildergenerator.domain.Parameter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.idea.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtClass

class GenerateBuilder: SelfTargetingIntention<KtClass>(
    KtClass::class.java,
    "Generate builder"
) {
    override fun applyTo(element: KtClass, editor: Editor?) {
        val parameters = element.primaryConstructor?.valueParameters!!.map {
            Parameter(it.name!!, it.type().toString())
        }
        val code = createClassFromParams(element.name!!, parameters)
        createFile(element, code)
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
            "val ${it.name}: ${it.type}"
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
}