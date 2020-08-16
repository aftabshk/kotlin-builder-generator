package com.github.affishaikh.kotlinbuildergenerator.action

data class SampleBuilder(
    val name: String = "",
    val age: Int = 0
) {
    fun build(): Sample {
        return Sample(
            name = name,
            age = age
        )
    }
}