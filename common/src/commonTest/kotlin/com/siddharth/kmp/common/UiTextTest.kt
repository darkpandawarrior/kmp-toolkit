package com.siddharth.kmp.common

import kotlin.test.Test
import kotlin.test.assertEquals

class UiTextTest {
    @Test
    fun ofBlankOrNullIsEmpty() {
        assertEquals(UiText.Empty, UiText.of(null))
        assertEquals(UiText.Empty, UiText.of(""))
        assertEquals(UiText.Empty, UiText.of("   "))
    }

    @Test
    fun ofValueIsDynamic() {
        assertEquals(UiText.Dynamic("hi"), UiText.of("hi"))
    }

    @Test
    fun asStringResolvesEachArm() {
        assertEquals("hi", UiText.Dynamic("hi").asString())
        assertEquals("key.name", UiText.Res("key.name", listOf("a")).asString())
        assertEquals("", UiText.Empty.asString())
    }
}
