package com.siddharth.kmp.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class DecisionEngineTest {
    private val now = Instant.fromEpochMilliseconds(10_000_000)
    private val ttl = 5.minutes

    private fun <T> decide(
        data: StoreData<T>,
        connectivity: Connectivity = Connectivity.Online,
        classify: (Throwable) -> ErrorKind = { ErrorKind.Other },
    ) = DecisionEngine.decide(data, connectivity, ttl, now, classify)

    @Test
    fun empty_and_offline_is_no_network() {
        assertEquals(ScreenState.NoNetwork(), decide(StoreData<List<Int>>(data = emptyList()), Connectivity.Offline))
    }

    @Test
    fun empty_and_captive_portal_flags_captive() {
        assertEquals(
            ScreenState.NoNetwork(isCaptivePortal = true),
            decide(StoreData<List<Int>>(data = null), Connectivity.CaptivePortal),
        )
    }

    @Test
    fun empty_online_network_error_is_no_network() {
        val s = decide(StoreData<Int>(data = null, error = RuntimeException("timeout")), classify = { ErrorKind.Network })
        assertEquals(ScreenState.NoNetwork(), s)
    }

    @Test
    fun empty_online_auth_error_is_unauthenticated() {
        val s = decide(StoreData<Int>(data = null, error = RuntimeException("401")), classify = { ErrorKind.Auth })
        assertEquals(ScreenState.Unauthenticated, s)
    }

    @Test
    fun empty_online_other_error_is_error() {
        val boom = IllegalStateException("boom")
        val s = decide(StoreData<Int>(data = null, error = boom))
        assertEquals(ScreenState.Error(boom), s)
    }

    @Test
    fun empty_online_no_error_never_fetched_is_loading() {
        assertEquals(ScreenState.Loading, decide(StoreData<Int>(data = null, fetchedAt = null)))
    }

    @Test
    fun empty_online_no_error_but_fetched_is_empty() {
        assertEquals(ScreenState.Empty, decide(StoreData<List<Int>>(data = emptyList(), fetchedAt = now)))
    }

    @Test
    fun has_data_is_content_with_freshness_and_refreshing() {
        val fetchedAt = now - 2.minutes
        val s = decide(StoreData(data = listOf(1, 2), fetchedAt = fetchedAt, isRefreshing = true))
        val content = assertIs<ScreenState.Content<List<Int>>>(s)
        assertEquals(listOf(1, 2), content.data)
        assertEquals(FreshnessBand.Fresh, content.freshness) // 2 min old, ttl 5 min
        assertEquals(true, content.isRefreshing)
    }
}
