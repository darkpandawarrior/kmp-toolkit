package com.siddharth.kmp.store

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenWithMutationTest {
    @Test
    fun combines_screen_with_default_idle_mutation() = runTest {
        val screen = MutableStateFlow<ScreenState<Int>>(ScreenState.Loading)

        screenWithMutationStream<Int, Unit>(screen).test {
            val first = awaitItem()
            assertEquals(ScreenState.Loading, first.screen)
            assertEquals(MutationState.Idle, first.mutation)
            assertEquals(0, first.outboxPending)
            assertEquals(false, first.isSyncing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun mutation_and_sync_signals_flow_through() = runTest {
        val screen = MutableStateFlow<ScreenState<Int>>(ScreenState.Content(1))
        val mutation = MutableStateFlow<MutationState<String>>(MutationState.Idle)
        val pending = MutableStateFlow(0)
        val syncing = MutableStateFlow(false)

        screenWithMutationStream(screen, mutation, pending, syncing).test {
            assertEquals(MutationState.Idle, awaitItem().mutation)

            mutation.value = MutationState.Submitting
            assertEquals(MutationState.Submitting, awaitItem().mutation)

            pending.value = 3
            assertEquals(3, awaitItem().outboxPending)

            syncing.value = true
            assertEquals(true, awaitItem().isSyncing)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
