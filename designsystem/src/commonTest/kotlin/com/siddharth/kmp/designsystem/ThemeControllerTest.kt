package com.siddharth.kmp.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeControllerTest {
    private class FakeStore(private var v: Boolean? = null) : ThemeStore {
        override fun darkOverride(): Boolean? = v
        override fun setDark(dark: Boolean) {
            v = dark
        }
    }

    @Test
    fun defaultsToDarkWhenNoStoredChoice() {
        assertTrue(ThemeController(FakeStore(null), defaultDark = true).isDark.value)
        assertFalse(ThemeController(FakeStore(null), defaultDark = false).isDark.value)
    }

    @Test
    fun storedChoiceWinsOverDefault() {
        assertFalse(ThemeController(FakeStore(false), defaultDark = true).isDark.value)
    }

    @Test
    fun toggleFlipsAndPersists() {
        val store = FakeStore(false)
        val controller = ThemeController(store, defaultDark = false)
        controller.toggle()
        assertTrue(controller.isDark.value)
        assertEquals(true, store.darkOverride())
    }
}
