package com.siddharth.kmp.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EloTest {
    @Test
    fun equal_ratings_expect_half() {
        assertEquals(0.5, Elo.expectedScore(1200, 1200), 1e-9)
    }

    @Test
    fun stronger_player_expects_more_than_half() {
        assertTrue(Elo.expectedScore(1600, 1200) > 0.5)
        assertTrue(Elo.expectedScore(1200, 1600) < 0.5)
    }

    @Test
    fun win_is_strictly_monotonic_even_vs_far_weaker() {
        // Heavy favourite beating a weak opponent still nudges up by at least +1.
        assertTrue(Elo.step(2000, 200, won = true) >= 2001)
    }

    @Test
    fun loss_is_strictly_monotonic_even_vs_far_stronger() {
        // Underdog losing to a far-stronger opponent still drops by at least -1.
        assertTrue(Elo.step(800, 2500, won = false) <= 799)
    }

    @Test
    fun clamps_to_floor_and_ceiling() {
        assertEquals(0, Elo.step(0, 3000, won = false, floor = 0, ceiling = 4000))
        assertEquals(4000, Elo.step(4000, 0, won = true, floor = 0, ceiling = 4000))
    }

    @Test
    fun custom_k_scales_the_swing() {
        val small = Elo.step(1200, 1400, won = true, k = 8.0) - 1200
        val large = Elo.step(1200, 1400, won = true, k = 64.0) - 1200
        assertTrue(large > small)
    }
}
