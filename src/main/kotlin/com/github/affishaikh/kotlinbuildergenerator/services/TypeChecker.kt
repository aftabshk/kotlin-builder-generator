package com.github.affishaikh.kotlinbuildergenerator.services

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.js.descriptorUtils.nameIfStandardType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlinx.serialization.compiler.resolve.toClassDescriptor

class TypeChecker {

    private val temporalHavingNowFunction = listOf(
        "HijrahDate", "Instant", "JapaneseDate", "LocalDate", "LocalDateTime", "LocalTime",
        "MinguoDate", "OffsetDateTime", "OffsetTime", "ThaiBuddhistDate", "Year", "YearMonth",
        "ZonedDateTime"
    )

    fun isEnumClass(parameterType: KotlinType): Boolean {
        return parameterType.toClassDescriptor?.kind?.name == "ENUM_CLASS"
    }

    fun isBigDecimal(parameterType: KotlinType): Boolean {
        return parameterType.constructor.declarationDescriptor?.name?.identifier == "BigDecimal"
    }

    fun doesNeedABuilder(it: KotlinType) =
        !isKotlinBuiltinType(it) && it.toClassDescriptor?.unsubstitutedPrimaryConstructor?.valueParameters?.isNotEmpty() ?: false

    fun isNullableInt(parameterType: KotlinType): Boolean {
        return KotlinBuiltIns.isInt(parameterType) || (parameterType.isNullable() && parameterType.nameIfStandardType.toString() == "Int")
    }

    fun isDateTimeType(parameterType: KotlinType): Boolean {
        val name = parameterType.constructor.declarationDescriptor?.name?.identifier
        return temporalHavingNowFunction.contains(name)
    }

    private fun isKotlinBuiltinType(type: KotlinType): Boolean {
        return KotlinBuiltIns.isBoolean(type) ||
            KotlinBuiltIns.isChar(type) ||
            KotlinBuiltIns.isDouble(type) ||
            KotlinBuiltIns.isFloat(type) ||
            KotlinBuiltIns.isInt(type) ||
            KotlinBuiltIns.isLong(type) ||
            KotlinBuiltIns.isShort(type) ||
            KotlinBuiltIns.isCollectionOrNullableCollection(type) ||
            KotlinBuiltIns.isNullableAny(type) ||
            KotlinBuiltIns.isString(type) ||
            KotlinBuiltIns.isListOrNullableList(type) ||
            KotlinBuiltIns.isSetOrNullableSet(type) ||
            KotlinBuiltIns.isMapOrNullableMap(type) ||
            isBigDecimal(type) ||
            isEnumClass(type)
    }
}