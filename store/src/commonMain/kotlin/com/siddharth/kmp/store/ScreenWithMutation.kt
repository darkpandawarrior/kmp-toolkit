package com.siddharth.kmp.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * The whole state of a screen that both DISPLAYS data and MUTATES it — the read [ScreenState] and the
 * write [MutationState] in one snapshot, plus the offline-sync status ([outboxPending] count +
 * [isSyncing]) so a "3 pending · syncing…" banner needs no extra plumbing.
 *
 * The capstone over the read ([screenStateStream]) and write ([submitFlow]) halves: a ViewModel
 * exposes one `Flow<ScreenWithMutation<T, R>>` instead of juggling several.
 */
data class ScreenWithMutation<T, R>(
    val screen: ScreenState<T>,
    val mutation: MutationState<R> = MutationState.Idle,
    /** Writes still queued in the offline outbox (wire to `OpOutbox.pending().map { it.size }`). */
    val outboxPending: Int = 0,
    /** True while the outbox is actively replaying. */
    val isSyncing: Boolean = false,
)

/**
 * Combines the read state, the write state, and the offline-sync signals into one stream. Only [screen]
 * is required; the rest default to a single benign emission, so the combined stream emits as soon as
 * the screen does. The app wires [outboxPending]/[isSyncing] to its `:offline-outbox` queue if it uses one.
 */
fun <T, R> screenWithMutationStream(
    screen: Flow<ScreenState<T>>,
    mutation: Flow<MutationState<R>> = flowOf(MutationState.Idle),
    outboxPending: Flow<Int> = flowOf(0),
    isSyncing: Flow<Boolean> = flowOf(false),
): Flow<ScreenWithMutation<T, R>> =
    combine(screen, mutation, outboxPending, isSyncing) { s, m, pending, syncing ->
        ScreenWithMutation(screen = s, mutation = m, outboxPending = pending, isSyncing = syncing)
    }.distinctUntilChanged()
