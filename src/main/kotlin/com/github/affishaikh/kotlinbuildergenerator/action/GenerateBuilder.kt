package com.github.affishaikh.kotlinbuildergenerator.action

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.idea.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.KtClass

class GenerateBuilder: SelfTargetingIntention<KtClass>(
    KtClass::class.java,
    "Generate builder"
) {
    override fun applyTo(element: KtClass, editor: Editor?) {
        createFile(element, "")
    }

    override fun isApplicableTo(element: KtClass, caretOffset: Int): Boolean {
        return true
    }

    private fun createFile(element: KtClass, classCode: String) {
        val containingDirectory = element.containingFile.containingDirectory
        val psiFileFactory = PsiFileFactory.getInstance(element.project)
        val file = psiFileFactory.createFileFromText("${element.name}Builder.kt", KotlinFileType(), "class")
        containingDirectory.add(file)
    }
}