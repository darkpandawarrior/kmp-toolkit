package com.siddharth.kmp.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The offline-first write path: runs [mutation] and emits [MutationState] (Submitting → Success/Failed).
 * On a failure classified retryable by [isRetryable] (offline / 5xx / timeout), it hands [payload] to
 * [enqueueOffline] so the write is durably queued for later replay — then reports
 * `Failed(queuedOffline = true)`, letting the UI say "saved, will sync". Non-retryable failures (a 4xx
 * the server rejected) surface as `Failed(queuedOffline = false)`.
 *
 * Store-library-agnostic: [enqueueOffline] is a plain suspend callback, so an app wires it to whatever
 * durable queue it uses (e.g. `:offline-outbox`'s `OpOutbox.enqueue`) without `:store` depending on it.
 * If [enqueueOffline] is null, every failure is a plain `Failed(queuedOffline = false)`.
 */
fun <P, R> submitFlow(
    payload: P,
    mutation: suspend (P) -> R,
    isRetryable: (Throwable) -> Boolean = { false },
    enqueueOffline: (suspend (P) -> Unit)? = null,
): Flow<MutationState<R>> =
    flow {
        emit(MutationState.Submitting)
        runCatching { mutation(payload) }.fold(
            onSuccess = { emit(MutationState.Success(it)) },
            onFailure = { error ->
                if (isRetryable(error) && enqueueOffline != null) {
                    // Best-effort enqueue: if even the durable write fails, still report the queue attempt
                    // (the caller's outbox is the durability boundary, not this handler).
                    runCatching { enqueueOffline(payload) }
                    emit(MutationState.Failed(error, queuedOffline = true))
                } else {
                    emit(MutationState.Failed(error, queuedOffline = false))
                }
            },
        )
    }
