package com.github.affishaikh.kotlinbuildergenerator.services

import com.github.affishaikh.kotlinbuildergenerator.domain.KotlinFileType
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.psi.KtClass

class FileService {

    fun createFile(element: KtClass, data: String) {
        val containingDirectory = element.containingFile.containingDirectory
        val psiFileFactory = PsiFileFactory.getInstance(element.project)
        val file = psiFileFactory.createFileFromText("${element.name}Builder.kt", KotlinFileType(), data)
        containingDirectory.add(file)
    }
}