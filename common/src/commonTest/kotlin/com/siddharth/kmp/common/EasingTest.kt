package com.siddharth.kmp.common

import kotlin.test.Test
import kotlin.test.assertEquals

class EasingTest {
    @Test
    fun lerpMidpoint() {
        assertEquals(5f, lerp(0f, 10f, 0.5f))
    }

    @Test
    fun lerpClampsTOutsideZeroToOne() {
        assertEquals(10f, lerp(0f, 10f, 1.5f))
        assertEquals(0f, lerp(0f, 10f, -0.5f))
    }

    @Test
    fun easingFunctionsAreIdentityAtBoundaries() {
        assertEquals(0f, easeInQuart(0f))
        assertEquals(1f, easeInQuart(1f))
        assertEquals(0f, easeOutCubic(0f))
        assertEquals(1f, easeOutCubic(1f))
        assertEquals(0f, easeOutBack(0f))
        assertEquals(1f, easeOutBack(1f))
    }
}
