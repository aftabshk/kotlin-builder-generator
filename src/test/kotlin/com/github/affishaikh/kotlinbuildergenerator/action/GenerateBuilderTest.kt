package com.github.affishaikh.kotlinbuildergenerator.action

import com.github.affishaikh.kotlinbuildergenerator.services.FileService
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class GenerateBuilderTest {
    private val generateBuilderIntention = GenerateBuilder()
    private val ideaTestFixtureFactory = IdeaTestFixtureFactory.getFixtureFactory()
    private lateinit var fixture: CodeInsightTestFixture

    @Before
    fun setUp() {
        clearAllMocks()
        mockkConstructor(FileService::class)
        justRun {
            anyConstructed<FileService>().createFile(any(), any())
        }
        val projectDescriptor = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR
        val fixtureBuilder = ideaTestFixtureFactory.createLightFixtureBuilder(projectDescriptor)
        fixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(
            fixtureBuilder.fixture,
            LightTempDirTestFixtureImpl(true)
        )
        fixture.setUp()
    }

    @After
    fun tearDown() {
        clearAllMocks()
        fixture.tearDown()
    }

    @Test
    fun `should create the builder class`() {
        val testClass = """
            package com.github.affishaikh.kotlinbuildergenerator.domain

            data class Person(
                val name: String<caret>
            )
        """.trimIndent()

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

        verifyIntentionResults(actualBuilder, mapOf("Person.kt" to testClass))
    }

    @Test
    fun `should create the builder for a class which has another class composed in it`() {
        val testClass = """
            package com.github.affishaikh.kotlinbuildergenerator.domain

            data class Person(<caret>
                val name: String,
                val address: Address
            )

            data class Address(
                val street: String,
                val pinCode: Int 
            )
        """.trimIndent()

        val actualBuilder = """
            package com.github.affishaikh.kotlinbuildergenerator.domain

            data class PersonBuilder(
            val name: String = "",
            val address: Address = AddressBuilder().build()
            ) {
            fun build(): Person {
            return Person(
            name = name,
            address = address
            )
            }
            }
            data class AddressBuilder(
            val street: String = "",
            val pinCode: Int = 0
            ) {
            fun build(): Address {
            return Address(
            street = street,
            pinCode = pinCode
            )
            }
            }
        """.trimIndent()

        verifyIntentionResults(actualBuilder, mapOf("Person.kt" to testClass))
    }

    @Test
    fun `should import the Address class to create PersonBuilder`() {
        val personClass = """
            package com.github.affishaikh.kotlinbuildergenerator.domain

            import com.github.affishaikh.kotlinbuildergenerator.action.Address

            data class Person(<caret>
                val name: String,
                val address: Address
            )
        """.trimIndent()

        val addressClass = """
            package com.github.affishaikh.kotlinbuildergenerator.action

            data class Address(
                val street: String,
                val pinCode: Int
            )
        """.trimIndent()

        val actualBuilder = """
            package com.github.affishaikh.kotlinbuildergenerator.domain

            import com.github.affishaikh.kotlinbuildergenerator.action.Address

            data class PersonBuilder(
            val name: String = "",
            val address: Address = AddressBuilder().build()
            ) {
            fun build(): Person {
            return Person(
            name = name,
            address = address
            )
            }
            }
            data class AddressBuilder(
            val street: String = "",
            val pinCode: Int = 0
            ) {
            fun build(): Address {
            return Address(
            street = street,
            pinCode = pinCode
            )
            }
            }
        """.trimIndent()

        verifyIntentionResults(actualBuilder, mapOf("Address.kt" to addressClass, "Person.kt" to personClass))
    }

    @Test
    fun `should not create duplicate data classes`() {
        val customerClass = """
            package com.github.affishaikh.kotlinbuildergenerator.domain

            data class Customer(<caret>
                val personalDetails: PersonalDetails,
                val nomineeDetails: NomineeDetails
            )

            data class PersonalDetails(
                val name: String
            )

            data class NomineeDetails(
                val personalDetails: PersonalDetails
            )
        """.trimIndent()

        val actualBuilder = """
            package com.github.affishaikh.kotlinbuildergenerator.domain

            data class CustomerBuilder(
            val personalDetails: PersonalDetails = PersonalDetailsBuilder().build(),
            val nomineeDetails: NomineeDetails = NomineeDetailsBuilder().build()
            ) {
            fun build(): Customer {
            return Customer(
            personalDetails = personalDetails,
            nomineeDetails = nomineeDetails
            )
            }
            }
            data class PersonalDetailsBuilder(
            val name: String = ""
            ) {
            fun build(): PersonalDetails {
            return PersonalDetails(
            name = name
            )
            }
            }
            data class NomineeDetailsBuilder(
            val personalDetails: PersonalDetails = PersonalDetailsBuilder().build()
            ) {
            fun build(): NomineeDetails {
            return NomineeDetails(
            personalDetails = personalDetails
            )
            }
            }
        """.trimIndent()

        verifyIntentionResults(actualBuilder, mapOf("Customer.kt" to customerClass))
    }

    @Test
    fun `should create builder with default value for a property having an ENUM type`() {
        val customerClass = """
            package com.github.affishaikh.kotlinbuildergenerator.domain

            data class Customer(<caret>
                val name: String,
                val gender: Gender
            )

            enum class Gender {
                MALE,
                FEMALE
            }
        """.trimIndent()

        val actualBuilder = """
            package com.github.affishaikh.kotlinbuildergenerator.domain

            import com.github.affishaikh.kotlinbuildergenerator.domain.Gender.MALE

            data class CustomerBuilder(
            val name: String = "",
            val gender: Gender = MALE
            ) {
            fun build(): Customer {
            return Customer(
            name = name,
            gender = gender
            )
            }
            }
        """.trimIndent()

        verifyIntentionResults(actualBuilder, mapOf("Customer.kt" to customerClass))
    }

    private fun verifyIntentionResults(actualBuilder: String, testClasses: Map<String, String>) {
        testClasses.map {
            fixture.configureByText(it.key, it.value)
        }
        fixture.launchAction(generateBuilderIntention)
        val builderSlot = slot<String>()

        verify(exactly = 1) {
            anyConstructed<FileService>().createFile(any(), capture(builderSlot))
        }

        assertEquals(actualBuilder, builderSlot.captured)
    }
}
