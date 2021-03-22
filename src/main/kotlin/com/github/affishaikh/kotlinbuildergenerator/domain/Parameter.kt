package com.github.affishaikh.kotlinbuildergenerator.domain

import org.jetbrains.kotlin.types.KotlinType

data class Parameter(
    val name: String,
    val type: KotlinType
) {
    fun typeName() = type.toString().replace("?", "")
}
