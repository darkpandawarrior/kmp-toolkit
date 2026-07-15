package com.siddharth.kmp.store

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * How stale cached data is, on a time axis only — network state is deliberately NOT an input (that's a
 * separate "am I online" concern). Drives a per-card freshness indicator ("Updated 2 min ago" → "Refresh
 * now" → "Retry").
 */
enum class FreshnessBand {
    /** Never fetched and no error — hide the indicator. */
    Initial,

    /** `age <= ttl`, no error — up to date. */
    Fresh,

    /** `ttl < age <= 3·ttl`, no error — suggest a refresh. */
    Stale,

    /** `age > 3·ttl`, or a last error is present — urge a retry. */
    VeryStale,
}

/**
 * Pure staleness computation. First match wins:
 * 1. `lastError != null` → [FreshnessBand.VeryStale]
 * 2. `lastSyncedAt == null` → [FreshnessBand.Initial]
 * 3. `age <= ttl` → [FreshnessBand.Fresh]
 * 4. `age <= 3·ttl` → [FreshnessBand.Stale]
 * 5. else → [FreshnessBand.VeryStale]
 */
@OptIn(ExperimentalTime::class)
fun freshnessBand(
    now: Instant,
    lastSyncedAt: Instant?,
    ttl: Duration,
    lastError: Throwable? = null,
): FreshnessBand =
    when {
        lastError != null -> FreshnessBand.VeryStale
        lastSyncedAt == null -> FreshnessBand.Initial
        else -> {
            val age = now - lastSyncedAt
            when {
                age <= ttl -> FreshnessBand.Fresh
                age <= ttl * 3 -> FreshnessBand.Stale
                else -> FreshnessBand.VeryStale
            }
        }
    }
