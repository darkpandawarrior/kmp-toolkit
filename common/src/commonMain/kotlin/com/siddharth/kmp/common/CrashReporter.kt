package com.siddharth.kmp.common

// Reconciled from two divergent app-local seams (Backlog #21/#26):
//  - HireSignal app/.../crash/CrashReporter.kt: `record(Throwable)` + `setCrumb(k,v)`, Android-only
//    (android.util.Log + Thread.setDefaultUncaughtExceptionHandler — not wasmJs-safe).
//  - PaymentsLab core/common/CrashReporter.kt: `recordException`/`log`/`setCustomKey`/`setUserId` +
//    `NapierCrashReporter`, already pure-KMP.
// PaymentsLab's shape wins (richer — mirrors what Crashlytics/Sentry actually expose, and already
// KMP-portable); HireSignal's uncaught-handler installer is Android-specific process wiring and
// stays app-side, not part of this seam. Default impl routes through this module's own [AppLog]
// (already a :common dep) instead of duplicating a Napier wrapper.

/**
 * Crash / non-fatal reporting behind an interface, so callers depend on the *capability* — not on
 * Firebase Crashlytics or Sentry directly. Swapping in a real backend is a one-line DI change with
 * no call-site edits.
 */
interface CrashReporter {
    /** Report a caught, non-crashing error. */
    fun recordException(
        throwable: Throwable,
        message: String? = null,
    )

    /** Leave a breadcrumb — the last N are attached to the next report. */
    fun log(breadcrumb: String)

    /** Attach a searchable key/value to subsequent reports. */
    fun setCustomKey(
        key: String,
        value: String,
    )

    /** Associate reports with a (non-PII) user/session identifier, or clear it with `null`. */
    fun setUserId(id: String?)
}

/**
 * Dependency-free default [CrashReporter] that routes everything through [AppLog] (Napier). Gives
 * the same call sites and breadcrumb discipline as a real backend, minus the upload.
 */
class NapierCrashReporter : CrashReporter {
    override fun recordException(
        throwable: Throwable,
        message: String?,
    ) = AppLog.e(message ?: "non-fatal", throwable, TAG)

    override fun log(breadcrumb: String) = AppLog.d("breadcrumb: $breadcrumb", TAG)

    override fun setCustomKey(
        key: String,
        value: String,
    ) = AppLog.d("key $key=$value", TAG)

    override fun setUserId(id: String?) = AppLog.d("userId=$id", TAG)

    private companion object {
        const val TAG = "CrashReporter"
    }
}
