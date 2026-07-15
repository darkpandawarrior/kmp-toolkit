package com.siddharth.kmp.offlineoutbox

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Room-backed [OpOutbox]. The replay semantics (dead-letter on permanent/exhausted, stop-on-transient
 * to preserve order) live here; the DAO only moves rows. Payload is an opaque string — the caller
 * serializes before [enqueue] and deserializes inside its `send`, so this stays transport-agnostic.
 */
class RoomOpOutbox(
    private val dao: OpOutboxDao,
) : OpOutbox {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun enqueue(
        type: String,
        payload: String,
    ): String {
        val id = Uuid.random().toString()
        dao.insert(
            OpOutboxEntity(
                id = id,
                type = type,
                payload = payload,
                attempts = 0,
                status = OpStatus.PENDING.name,
                lastError = null,
                createdAtMs = epochMillis(),
            ),
        )
        RoomChangeBus.notify(TABLE)
        return id
    }

    override fun pending(): Flow<List<OpEntry>> = dao.observeLive().map { rows -> rows.map { it.toEntry() } }

    override suspend fun deadLetters(): List<OpEntry> = dao.dead().map { it.toEntry() }

    override suspend fun replay(
        maxAttempts: Int,
        isPermanent: (Throwable) -> Boolean,
        send: suspend (OpEntry) -> Unit,
    ) {
        for (row in dao.pending()) {
            val result = runCatching { send(row.toEntry()) }
            if (result.isSuccess) {
                dao.delete(row.id)
                RoomChangeBus.notify(TABLE)
                continue
            }
            val error = result.exceptionOrNull()
            if ((error != null && isPermanent(error)) || row.attempts + 1 >= maxAttempts) {
                // Server rejected it (retrying changes nothing) or it exhausted transient retries —
                // dead-letter and keep draining the rest of the queue instead of stalling behind it.
                dao.markDead(row.id, error?.message)
                RoomChangeBus.notify(TABLE)
                continue
            }
            // Transient (offline / server down) — record the attempt and stop to preserve FIFO order;
            // the next trigger resumes from this same op.
            dao.recordFailure(row.id, error?.message)
            RoomChangeBus.notify(TABLE)
            return
        }
    }

    override suspend fun requeue(id: String) {
        dao.requeue(id)
        RoomChangeBus.notify(TABLE)
    }

    private fun OpOutboxEntity.toEntry(): OpEntry =
        OpEntry(
            id = id,
            type = type,
            payload = payload,
            attempts = attempts,
            status = OpStatus.valueOf(status),
            lastError = lastError,
            createdAtMs = createdAtMs,
        )

    private companion object {
        const val TABLE = "op_outbox"
    }
}
