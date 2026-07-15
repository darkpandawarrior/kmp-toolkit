package com.siddharth.kmp.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RngTest {
    @Test
    fun sameSeedProducesSameSequence() {
        val a = Rng(seed = 42L)
        val b = Rng(seed = 42L)
        val (v1, a1) = a.nextLong()
        val (v2, b1) = b.nextLong()
        assertEquals(v1, v2)
        assertEquals(a1.state, b1.state)
    }

    @Test
    fun replayingFromRngStateReproducesTheSameDraw() {
        val (_, advanced) = Rng(seed = 7L).nextLong()
        val replayed = rngFrom(advanced.state)
        assertEquals(advanced.nextLong().first, replayed.nextLong().first)
    }

    @Test
    fun differentSeedsDiverge() {
        val (v1, _) = Rng(seed = 1L).nextLong()
        val (v2, _) = Rng(seed = 2L).nextLong()
        assertNotEquals(v1, v2)
    }

    @Test
    fun nextIntStaysInBound() {
        var rng = Rng(seed = 99L)
        repeat(50) {
            val (x, next) = rng.nextInt(10)
            assertEquals(x.coerceIn(0, 9), x)
            rng = next
        }
    }
}
