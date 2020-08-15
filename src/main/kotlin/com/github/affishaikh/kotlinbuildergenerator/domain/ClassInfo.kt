package com.github.affishaikh.kotlinbuildergenerator.domain

import org.jetbrains.kotlin.types.KotlinType

data class ClassInfo(
    val name: String,
    val type: KotlinType?,
    val parameters: List<Parameter>
)