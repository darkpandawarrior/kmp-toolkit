package com.siddharth.kmp.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FreshnessTest {
    private val now = Instant.fromEpochMilliseconds(10_000_000)
    private val ttl = 5.minutes

    @Test
    fun never_synced_no_error_is_initial() {
        assertEquals(FreshnessBand.Initial, freshnessBand(now, lastSyncedAt = null, ttl = ttl))
    }

    @Test
    fun error_is_very_stale_even_when_recent() {
        assertEquals(
            FreshnessBand.VeryStale,
            freshnessBand(now, lastSyncedAt = now - 1.minutes, ttl = ttl, lastError = RuntimeException()),
        )
    }

    @Test
    fun within_ttl_is_fresh() {
        assertEquals(FreshnessBand.Fresh, freshnessBand(now, now - 4.minutes, ttl))
    }

    @Test
    fun between_ttl_and_triple_is_stale() {
        assertEquals(FreshnessBand.Stale, freshnessBand(now, now - 12.minutes, ttl)) // 5 < 12 <= 15
    }

    @Test
    fun beyond_triple_ttl_is_very_stale() {
        assertEquals(FreshnessBand.VeryStale, freshnessBand(now, now - 30.minutes, ttl))
    }
}
