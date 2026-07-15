package com.siddharth.kmp.store

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubmitHandlerTest {
    private class Transient : RuntimeException("offline")

    private class Permanent : RuntimeException("400")

    @Test
    fun success_emits_submitting_then_success() = runTest {
        submitFlow(payload = 21, mutation = { it * 2 }).test {
            assertEquals(MutationState.Submitting, awaitItem())
            assertEquals(MutationState.Success(42), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun retryable_failure_enqueues_offline_and_flags_queued() = runTest {
        var enqueued: String? = null
        submitFlow(
            payload = "job-1",
            mutation = { throw Transient() },
            isRetryable = { it is Transient },
            enqueueOffline = { enqueued = it },
        ).test {
            assertEquals(MutationState.Submitting, awaitItem())
            val failed = assertIs<MutationState.Failed>(awaitItem())
            assertTrue(failed.queuedOffline)
            awaitComplete()
        }
        assertEquals("job-1", enqueued)
    }

    @Test
    fun non_retryable_failure_does_not_enqueue() = runTest {
        var enqueued = false
        submitFlow(
            payload = "job-2",
            mutation = { throw Permanent() },
            isRetryable = { it is Transient },
            enqueueOffline = { enqueued = true },
        ).test {
            assertEquals(MutationState.Submitting, awaitItem())
            val failed = assertIs<MutationState.Failed>(awaitItem())
            assertTrue(!failed.queuedOffline)
            awaitComplete()
        }
        assertEquals(false, enqueued)
    }

    @Test
    fun retryable_failure_without_enqueue_callback_is_plain_failure() = runTest {
        submitFlow(payload = 1, mutation = { throw Transient() }, isRetryable = { true }).test {
            assertEquals(MutationState.Submitting, awaitItem())
            assertEquals(false, assertIs<MutationState.Failed>(awaitItem()).queuedOffline)
            awaitComplete()
        }
    }
}
