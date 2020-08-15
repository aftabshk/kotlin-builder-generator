package com.github.affishaikh.kotlinbuildergenerator.ui

import com.intellij.ide.util.PackageChooserDialog
import com.intellij.openapi.project.Project

class PackageNameInputPrompt(private val project: Project) : PackageChooserDialog("Select package", project)