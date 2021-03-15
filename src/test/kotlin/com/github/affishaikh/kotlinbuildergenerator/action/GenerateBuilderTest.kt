package com.github.affishaikh.kotlinbuildergenerator.action

import com.github.affishaikh.kotlinbuildergenerator.domain.KotlinFileType
import com.github.affishaikh.kotlinbuildergenerator.services.FileService
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import io.mockk.justRun
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class GenerateBuilderTest {
    private val generateBuilderIntention = GenerateBuilder()
    private val ideaTestFixtureFactory = IdeaTestFixtureFactory.getFixtureFactory()
    private lateinit var myFixture: CodeInsightTestFixture

    @Before
    fun setUp() {
        val projectDescriptor = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR
        val fixtureBuilder = ideaTestFixtureFactory.createLightFixtureBuilder(projectDescriptor)
        myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(
            fixtureBuilder.fixture,
            LightTempDirTestFixtureImpl(true)
        )
        myFixture.setUp()
    }

    @Test
    fun `should create the builder class`() {
        val testClass = """
            package com.github.affishaikh.kotlinbuildergenerator.domain

            data class Person(
                val name: String<caret>
            )
        """.trimIndent()

        mockkConstructor(FileService::class)
        justRun {
            anyConstructed<FileService>().createFile(any(), any())
        }
        myFixture.configureByText(KotlinFileType(), testClass)
        myFixture.launchAction(generateBuilderIntention)

        val actualBuilder = """
            package com.github.affishaikh.kotlinbuildergenerator.domain

            data class PersonBuilder(
            val name: String = ""
            ) {
            fun build(): Person {
            return Person(
            name = name
            )
            }
            }
        """.trimIndent()

        val builderSlot = slot<String>()

        verify(exactly = 1) {
            anyConstructed<FileService>().createFile(
                any(),
                capture(builderSlot)
            )
        }

        assertEquals(builderSlot.captured, actualBuilder)
    }
}
