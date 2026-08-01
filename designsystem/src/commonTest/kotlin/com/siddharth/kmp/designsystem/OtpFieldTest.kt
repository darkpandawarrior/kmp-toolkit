package com.siddharth.kmp.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class OtpFieldTest {
    @Test
    fun sanitizeKeepsDigitsAndCaps() {
        assertEquals("123456", sanitizeOtp("123456", 6))
        assertEquals("123456", sanitizeOtp("1234567890", 6))
        assertEquals("", sanitizeOtp("abcdef", 6))
        assertEquals("", sanitizeOtp("", 6))
    }

    @Test
    fun sanitizeRecoversACodePastedFromANotification() {
        // The most common real entry path: long-press paste of "Your OTP is 123 456".
        assertEquals("123456", sanitizeOtp("Your OTP is 123 456", 6))
        assertEquals("4821", sanitizeOtp("G-4821 is your code", 4))
    }

    @Test
    fun cellIsActiveOnlyWhenTheFieldItselfHasFocus() {
        // The regression this guards: computing active purely as `index == value.length` painted a
        // focus ring on an unfocused, keyboard-closed form.
        assertEquals(OtpCellState.Active, otpCellState(3, "123", fieldFocused = true, isError = false))
        assertEquals(OtpCellState.Empty, otpCellState(3, "123", fieldFocused = false, isError = false))
    }

    @Test
    fun filledAndEmptyCellsResolveByPosition() {
        val v = "12"
        assertEquals(OtpCellState.Filled, otpCellState(0, v, fieldFocused = true, isError = false))
        assertEquals(OtpCellState.Filled, otpCellState(1, v, fieldFocused = true, isError = false))
        assertEquals(OtpCellState.Active, otpCellState(2, v, fieldFocused = true, isError = false))
        assertEquals(OtpCellState.Empty, otpCellState(3, v, fieldFocused = true, isError = false))
    }

    @Test
    fun errorOutranksEveryOtherState() {
        (0 until 6).forEach { i ->
            assertEquals(OtpCellState.Error, otpCellState(i, "123", fieldFocused = true, isError = true))
        }
    }

    @Test
    fun aFullCodeLeavesNoActiveCell() {
        // value.length == length is out of range, so no cell claims the caret once the code is complete.
        val states = (0 until 6).map { otpCellState(it, "123456", fieldFocused = true, isError = false) }
        assertEquals(List(6) { OtpCellState.Filled }, states)
    }
}
