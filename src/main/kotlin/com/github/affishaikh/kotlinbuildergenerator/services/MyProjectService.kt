package com.github.affishaikh.kotlinbuildergenerator.services

import com.intellij.openapi.project.Project
import com.github.affishaikh.kotlinbuildergenerator.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
