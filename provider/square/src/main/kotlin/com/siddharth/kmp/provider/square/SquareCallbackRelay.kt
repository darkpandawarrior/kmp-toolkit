package com.siddharth.kmp.provider.square

import com.siddharth.kmp.common.AppLog

/**
 * The result shape Square's Activity-callback SDK produces, normalized so the coroutine bridge
 * never touches `sqip.*` types directly.
 */
sealed interface SquareCallbackResult {
    data class Success(
        val nonce: String,
        val cardLastFour: String?,
    ) : SquareCallbackResult

    data object Canceled : SquareCallbackResult
}

/**
 * Process-scoped, single-slot holder bridging `CardEntry.handleActivityResult` (called from the
 * app's `MainActivity.onActivityResult`, since `CardEntry.startCardEntryActivity` uses the legacy
 * `startActivityForResult`/`onActivityResult` API, not an `ActivityResultContract`) back to the
 * suspended coroutine in `SquareGateway.pay`. Mirrors `RazorpayCallbackRelay` — same one-sheet-at-a-time
 * reasoning applies.
 */
object SquareCallbackRelay {
    @Volatile
    private var listener: ((SquareCallbackResult) -> Unit)? = null

    fun register(onResult: (SquareCallbackResult) -> Unit) {
        if (listener != null) {
            AppLog.w("overwriting a still-pending Square listener", tag = TAG)
        }
        listener = onResult
    }

    fun clear() {
        listener = null
    }

    fun emit(result: SquareCallbackResult) {
        val current = listener
        if (current == null) {
            AppLog.w("Square result arrived with no registered listener (dropped): $result", tag = TAG)
            return
        }
        current.invoke(result)
    }

    private const val TAG = "SquareCallbackRelay"
}
