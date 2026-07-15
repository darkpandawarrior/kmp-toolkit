package com.siddharth.kmp.store

/**
 * The write-side counterpart to [ScreenState]: the state of a single mutation (submit/update/delete) a
 * screen renders while it runs. One exhaustive type instead of `isSubmitting` + `submitError` flags.
 */
sealed interface MutationState<out R> {
    /** No mutation in flight. */
    data object Idle : MutationState<Nothing>

    /** The mutation is running. */
    data object Submitting : MutationState<Nothing>

    /** The mutation succeeded, carrying its [result]. */
    data class Success<R>(val result: R) : MutationState<R>

    /**
     * The mutation failed. [queuedOffline] is true when the write was durably enqueued for later replay
     * (an offline/transient failure) — the UI can show "saved, will sync" rather than a hard error.
     */
    data class Failed(val error: Throwable, val queuedOffline: Boolean = false) : MutationState<Nothing>
}
