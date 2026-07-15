package com.siddharth.kmp.offlineoutbox

import kotlinx.coroutines.flow.Flow

/**
 * The SECOND outbox shape: a durable, FIFO, append-only *operation log* — distinct from
 * [SubmitOutbox], which is a keyed latest-write-wins draft store. Reach for this one when order
 * matters and every mutation must reach the server exactly once, surviving process death:
 *
 *  - **append-only** — [enqueue] logs an opaque `(type, payload)` op and returns its stable id, which
 *    doubles as an idempotency key the caller sends to the server so a re-delivered op is collapsed.
 *  - **FIFO replay** — [replay] drains oldest-first through a caller-supplied `send`. It stops at the
 *    first *transient* failure (offline / 5xx / timeout) to preserve order and let the next trigger
 *    resume, rather than reordering the queue or hammering a down server.
 *  - **dead-lettering** — an op the server *rejects* (classified permanent by `isPermanent`, e.g. a
 *    4xx) or one that exhausts `maxAttempts` transient retries is marked dead (kept, not deleted) so
 *    replay moves past it instead of getting stuck behind a poison row. Dead ops are inspectable via
 *    [deadLetters] and re-runnable via [requeue].
 *
 * Payload-agnostic by design: it stores opaque type + serialized-payload strings, and the caller owns
 * both serialization and the `send` transport — so this stays decoupled from any HTTP client or DTO.
 */
interface OpOutbox {
    /** Append an op; returns its id (= idempotency key). Triggering [replay] afterwards is the caller's job. */
    suspend fun enqueue(
        type: String,
        payload: String,
    ): String

    /** Observe live (non-dead) queue contents, oldest-first — drives a "N pending" sync indicator. */
    fun pending(): Flow<List<OpEntry>>

    /** Dead-lettered ops, for inspection or a manual-retry UI. */
    suspend fun deadLetters(): List<OpEntry>

    /**
     * Drain PENDING ops oldest-first. For each, calls [send]; on success the op is deleted. On
     * failure: if [isPermanent] returns true for the error, or the op has now failed [maxAttempts]
     * times, it is dead-lettered and replay continues; otherwise the failure is recorded and replay
     * STOPS (preserving order) — the next trigger resumes from the same op.
     */
    suspend fun replay(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        isPermanent: (Throwable) -> Boolean = { false },
        send: suspend (OpEntry) -> Unit,
    )

    /** Requeue a dead-lettered op (reset attempts + status to PENDING) for a manual retry. */
    suspend fun requeue(id: String)

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 10
    }
}

/** One logged operation. [id] is the idempotency key; [attempts] counts transient failures so far. */
data class OpEntry(
    val id: String,
    val type: String,
    val payload: String,
    val attempts: Int,
    val status: OpStatus,
    val lastError: String?,
    val createdAtMs: Long,
)

enum class OpStatus { PENDING, DEAD }
