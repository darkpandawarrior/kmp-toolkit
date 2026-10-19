package com.siddharth.kmp.common

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTest {
    @Test
    fun indianLakhGrouping() {
        assertEquals("₹2,49,900.00", 24_990_000L.formatMoney("₹", grouping = Grouping.Indian))
        assertEquals("1,00,000.00", 10_000_000L.formatMoney(grouping = Grouping.Indian))
        assertEquals("12,34,567.89", 123_456_789L.formatMoney(grouping = Grouping.Indian))
        assertEquals("1,00,00,000.00", 1_000_000_000L.formatMoney(grouping = Grouping.Indian))
        assertEquals("999.00", 99_900L.formatMoney(grouping = Grouping.Indian))
        assertEquals("1,000.00", 100_000L.formatMoney(grouping = Grouping.Indian))
    }

    @Test
    fun westernAndNoGrouping() {
        assertEquals("\$1,234,567.89", 123_456_789L.formatMoney("$"))
        assertEquals("1234567.89", 123_456_789L.formatMoney(grouping = Grouping.None))
    }

    @Test
    fun negativesAndZero() {
        assertEquals("-₹10.50", (-1050L).formatMoney("₹"))
        assertEquals("-0.50", (-50L).formatMoney())
        assertEquals("0.00", 0L.formatMoney())
        assertEquals("-₹2,49,900.00", (-24_990_000L).formatMoney("₹", grouping = Grouping.Indian))
    }

    @Test
    fun longMinValueDoesNotOverflow() {
        assertEquals("-92,23,37,20,36,85,47,758.08", Long.MIN_VALUE.formatMoney(grouping = Grouping.Indian))
        assertEquals("-92,233,720,368,547,758.08", Long.MIN_VALUE.formatMoney())
    }

    @Test
    fun roundsHalfUpAwayFromZeroWhenShowingFewerDigits() {
        assertEquals("1", 50L.formatMoney(fractionDigits = 0))
        assertEquals("0", 49L.formatMoney(fractionDigits = 0))
        assertEquals("-1", (-50L).formatMoney(fractionDigits = 0))
        assertEquals("2", 150L.formatMoney(fractionDigits = 0))
        assertEquals("3", 250L.formatMoney(fractionDigits = 0)) // half-up, not half-even (which gives 2)
        assertEquals("0", (-49L).formatMoney(fractionDigits = 0)) // no "-0"
        assertEquals("100", 9_999L.formatMoney(fractionDigits = 0))
    }

    @Test
    fun dropZeroFractionAndOtherExponents() {
        assertEquals("₹100", 10_000L.formatMoney("₹", dropZeroFraction = true))
        assertEquals("₹100.50", 10_050L.formatMoney("₹", dropZeroFraction = true))
        assertEquals("1,234", 1_234L.formatMoney(minorDigits = 0))
        assertEquals("1.234", 1_234L.formatMoney(minorDigits = 3))
    }

    @Test
    fun wireFormatterIsUntouched() {
        assertEquals("-10.50", (-1050L).minorToDecimalString())
    }
}
