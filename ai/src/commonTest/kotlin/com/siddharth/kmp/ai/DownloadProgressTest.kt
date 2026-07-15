package com.siddharth.kmp.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadProgressTest {
    @Test
    fun fraction_is_received_over_total() {
        val p = DownloadProgress(receivedBytes = 25, totalBytes = 100, bytesPerSec = 0, etaMs = -1)
        assertEquals(0.25f, p.fraction)
    }

    @Test
    fun fraction_is_zero_when_total_unknown() {
        assertEquals(0f, DownloadProgress(receivedBytes = 500, totalBytes = -1, bytesPerSec = 0, etaMs = -1).fraction)
    }

    @Test
    fun speed_counts_only_this_session_not_the_resumed_head_start() {
        // Resumed at 1000 bytes; 2000 more arrived in 1s → 2000 B/s (NOT 3000).
        val p = computeDownloadProgress(received = 3000, total = 10_000, elapsedMs = 1000, startOffset = 1000)
        assertEquals(2000L, p.bytesPerSec)
    }

    @Test
    fun eta_is_remaining_over_speed() {
        // 8000 bytes left at 2000 B/s → 4000 ms.
        val p = computeDownloadProgress(received = 2000, total = 10_000, elapsedMs = 1000, startOffset = 0)
        assertEquals(2000L, p.bytesPerSec)
        assertEquals(4000L, p.etaMs)
    }

    @Test
    fun eta_is_unknown_when_total_or_speed_unknown() {
        assertEquals(-1L, computeDownloadProgress(received = 500, total = -1, elapsedMs = 1000).etaMs)
        assertEquals(-1L, computeDownloadProgress(received = 500, total = 10_000, elapsedMs = 0).etaMs)
    }

    @Test
    fun fraction_clamps_when_received_exceeds_total() {
        assertTrue(DownloadProgress(receivedBytes = 120, totalBytes = 100, bytesPerSec = 0, etaMs = -1).fraction <= 1f)
    }
}
