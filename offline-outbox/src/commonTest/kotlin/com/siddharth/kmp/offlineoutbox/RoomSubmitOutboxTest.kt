package com.siddharth.kmp.offlineoutbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Fake SubmitDraftDao — no real Room instance needed to exercise RoomSubmitOutbox's logic
// (upsert/status transitions/clear), keeps the test portable across every target.
private class FakeSubmitDraftDao : SubmitDraftDao {
    private val rows = MutableStateFlow<List<SubmitDraftEntity>>(emptyList())

    override fun observeByFormKey(formKey: String): Flow<List<SubmitDraftEntity>> =
        rows.map { list -> list.filter { it.formKey == formKey }.sortedByDescending { it.createdAt } }

    override suspend fun upsert(entity: SubmitDraftEntity) {
        rows.value = rows.value.filterNot { it.formKey == entity.formKey && it.uniqueKey == entity.uniqueKey } + entity
    }

    override suspend fun updateStatus(formKey: String, uniqueKey: String, status: String, now: Long) {
        rows.value = rows.value.map {
            if (it.formKey == formKey && it.uniqueKey == uniqueKey) it.copy(status = status, updatedAt = now) else it
        }
    }

    override suspend fun updateStatusWithError(formKey: String, uniqueKey: String, status: String, error: String, now: Long) {
        rows.value = rows.value.map {
            if (it.formKey == formKey && it.uniqueKey == uniqueKey) {
                it.copy(status = status, errorMessage = error, updatedAt = now)
            } else {
                it
            }
        }
    }

    override suspend fun deleteByFormKey(formKey: String) {
        rows.value = rows.value.filterNot { it.formKey == formKey }
    }
}

class RoomSubmitOutboxTest {
    @Test
    fun enqueue_then_markSubmitted_updatesStatus() = runTest {
        val outbox = RoomSubmitOutbox(FakeSubmitDraftDao(), Json, Int.serializer())

        outbox.enqueue("form", "key1", 42)
        outbox.markSubmitted("form", "key1")

        val entry = outbox.drafts("form").first().single()
        assertEquals(DraftStatus.SUBMITTED, entry.status)
        assertEquals(42, entry.payload)
    }

    @Test
    fun markFailed_setsErrorMessage() = runTest {
        val outbox = RoomSubmitOutbox(FakeSubmitDraftDao(), Json, Int.serializer())

        outbox.enqueue("form", "key1", 7)
        outbox.markFailed("form", "key1", "network down")

        val entry = outbox.drafts("form").first().single()
        assertEquals(DraftStatus.FAILED, entry.status)
        assertEquals("network down", entry.errorMessage)
    }

    @Test
    fun clear_removesAllDraftsForFormKey() = runTest {
        val outbox = RoomSubmitOutbox(FakeSubmitDraftDao(), Json, Int.serializer())

        outbox.enqueue("form", "key1", 1)
        outbox.enqueue("form", "key2", 2)
        outbox.clear("form")

        assertTrue(outbox.drafts("form").first().isEmpty())
    }

    @Test
    fun changeBus_notify_emitsTableSet() = runTest {
        val received = mutableListOf<Set<String>>()
        val outbox = RoomSubmitOutbox(FakeSubmitDraftDao(), Json, Int.serializer())
        val job =
            launch(Dispatchers.Unconfined) {
                RoomChangeBus.changes.collect { received += it }
            }
        outbox.enqueue("form", "key1", 1)
        job.cancel()
        assertEquals(setOf("submit_drafts"), received.last())
    }
}
