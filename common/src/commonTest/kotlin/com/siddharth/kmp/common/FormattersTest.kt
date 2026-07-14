package com.siddharth.kmp.common

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test
    fun minorToDecimalStringFormatsPositiveAmounts() {
        assertEquals("10.50", 1050L.minorToDecimalString())
        assertEquals("9.99", 999L.minorToDecimalString())
        assertEquals("0.00", 0L.minorToDecimalString())
    }

    @Test
    fun minorToDecimalStringHandlesNegativeAmountsCorrectly() {
        // Regression: upi-intent's old manual divmod (`amountMinor / 100`, `amountMinor % 100`)
        // produced "-10.-50" for -1050, because Kotlin's `%` keeps the dividend's sign. The
        // canonical formatter must emit exactly one leading '-' with a magnitude-only fraction.
        assertEquals("-10.50", (-1050L).minorToDecimalString())
    }

    @Test
    fun minorToDecimalStringRespectsCustomFractionDigits() {
        assertEquals("1050", 1050L.minorToDecimalString(0))
        assertEquals("1.050", 1050L.minorToDecimalString(3))
    }
}
