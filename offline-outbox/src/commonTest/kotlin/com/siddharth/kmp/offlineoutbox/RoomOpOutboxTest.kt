package com.siddharth.kmp.offlineoutbox

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Fake OpOutboxDao — models the append-only log in memory (auto-incrementing seq, status/attempts
// transitions) so RoomOpOutbox's replay logic is exercised on every target without a real DB.
private class FakeOpOutboxDao : OpOutboxDao {
    private val rows = MutableStateFlow<List<OpOutboxEntity>>(emptyList())
    private var nextSeq = 1L

    override suspend fun insert(entity: OpOutboxEntity) {
        rows.value = rows.value + entity.copy(seq = nextSeq++)
    }

    override suspend fun pending(): List<OpOutboxEntity> =
        rows.value.filter { it.status == OpStatus.PENDING.name }.sortedBy { it.seq }

    override fun observeLive(): Flow<List<OpOutboxEntity>> =
        rows.map { list -> list.filter { it.status != OpStatus.DEAD.name }.sortedBy { it.seq } }

    override suspend fun dead(): List<OpOutboxEntity> =
        rows.value.filter { it.status == OpStatus.DEAD.name }.sortedBy { it.seq }

    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun recordFailure(id: String, error: String?) {
        rows.value = rows.value.map { if (it.id == id) it.copy(attempts = it.attempts + 1, lastError = error) else it }
    }

    override suspend fun markDead(id: String, error: String?) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(status = OpStatus.DEAD.name, attempts = it.attempts + 1, lastError = error) else it
        }
    }

    override suspend fun requeue(id: String) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(status = OpStatus.PENDING.name, attempts = 0, lastError = null) else it
        }
    }
}

private class Transient : RuntimeException("offline")

private class Permanent : RuntimeException("400 rejected")

class RoomOpOutboxTest {
    @Test
    fun replay_drains_oldest_first_and_deletes_on_success() = runTest {
        val outbox = RoomOpOutbox(FakeOpOutboxDao())
        outbox.enqueue("op", "A")
        outbox.enqueue("op", "B")
        outbox.enqueue("op", "C")

        val sent = mutableListOf<String>()
        outbox.replay { sent += it.payload }

        assertEquals(listOf("A", "B", "C"), sent)
        assertTrue(outbox.pending().first().isEmpty())
    }

    @Test
    fun replay_stops_at_first_transient_failure_preserving_order() = runTest {
        val outbox = RoomOpOutbox(FakeOpOutboxDao())
        outbox.enqueue("op", "A")
        outbox.enqueue("op", "B")

        val sent = mutableListOf<String>()
        outbox.replay(isPermanent = { false }) { entry ->
            sent += entry.payload
            if (entry.payload == "A") throw Transient()
        }

        // A failed transiently → replay stopped; B never attempted; both still live, A's attempts bumped.
        assertEquals(listOf("A"), sent)
        val live = outbox.pending().first()
        assertEquals(listOf("A", "B"), live.map { it.payload })
        assertEquals(1, live.first { it.payload == "A" }.attempts)
        assertTrue(outbox.deadLetters().isEmpty())
    }

    @Test
    fun replay_dead_letters_permanent_failure_and_continues() = runTest {
        val outbox = RoomOpOutbox(FakeOpOutboxDao())
        outbox.enqueue("op", "A")
        outbox.enqueue("op", "B")

        val sent = mutableListOf<String>()
        outbox.replay(isPermanent = { it is Permanent }) { entry ->
            sent += entry.payload
            if (entry.payload == "A") throw Permanent()
        }

        // A rejected → dead-lettered, replay continued to B (delivered + deleted).
        assertEquals(listOf("A", "B"), sent)
        assertEquals(listOf("A"), outbox.deadLetters().map { it.payload })
        assertTrue(outbox.pending().first().isEmpty())
    }

    @Test
    fun replay_dead_letters_after_max_attempts_exhausted() = runTest {
        val outbox = RoomOpOutbox(FakeOpOutboxDao())
        outbox.enqueue("op", "A")

        // maxAttempts=3: a persistently-transient op dies on its 3rd failure rather than looping forever.
        repeat(3) {
            outbox.replay(maxAttempts = 3, isPermanent = { false }) { throw Transient() }
        }

        assertTrue(outbox.pending().first().isEmpty())
        assertEquals(listOf("A"), outbox.deadLetters().map { it.payload })
    }

    @Test
    fun requeue_revives_a_dead_letter() = runTest {
        val outbox = RoomOpOutbox(FakeOpOutboxDao())
        val id = outbox.enqueue("op", "A")
        outbox.replay(isPermanent = { true }) { throw Permanent() }
        assertEquals(1, outbox.deadLetters().size)

        outbox.requeue(id)

        assertEquals(listOf("A"), outbox.pending().first().map { it.payload })
        assertTrue(outbox.deadLetters().isEmpty())
    }
}
