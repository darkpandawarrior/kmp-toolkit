package com.siddharth.kmp.store

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ScreenDataStreamTest {
    private val now = Instant.fromEpochMilliseconds(10_000_000)
    private val ttl = 5.minutes

    @Test
    fun maps_loading_then_content_as_data_arrives() = runTest {
        val data = MutableStateFlow(StoreData<List<Int>>(data = null, fetchedAt = null))
        val conn = MutableStateFlow(Connectivity.Online)

        screenStateStream(data, conn, ttl, now = { now }).test {
            assertEquals(ScreenState.Loading, awaitItem())
            data.value = StoreData(data = listOf(1, 2), fetchedAt = now - 1.minutes)
            val content = assertIs<ScreenState.Content<List<Int>>>(awaitItem())
            assertEquals(listOf(1, 2), content.data)
            assertEquals(FreshnessBand.Fresh, content.freshness)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun connectivity_change_redecides_to_no_network() = runTest {
        val data = MutableStateFlow(StoreData<List<Int>>(data = null, fetchedAt = null))
        val conn = MutableStateFlow(Connectivity.Online)

        screenStateStream(data, conn, ttl, now = { now }).test {
            assertEquals(ScreenState.Loading, awaitItem())
            conn.value = Connectivity.Offline
            assertEquals(ScreenState.NoNetwork(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun identical_snapshots_are_suppressed_by_distinctUntilChanged() = runTest {
        val data = MutableStateFlow(StoreData(data = listOf(1), fetchedAt = now))
        val conn = MutableStateFlow(Connectivity.Online)

        screenStateStream(data, conn, ttl, now = { now }).test {
            assertIs<ScreenState.Content<List<Int>>>(awaitItem())
            data.value = StoreData(data = listOf(1), fetchedAt = now) // structurally equal → no new state
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun freshness_stream_tracks_bands_over_time() = runTest {
        val data = MutableStateFlow(StoreData(data = listOf(1), fetchedAt = now - 1.minutes))

        freshnessStream(data, ttl, now = { now }).test {
            assertEquals(FreshnessBand.Fresh, awaitItem())
            data.value = StoreData(data = listOf(1), fetchedAt = now - 30.minutes)
            assertEquals(FreshnessBand.VeryStale, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
