package com.siddharth.kmp.common

import kotlin.concurrent.Volatile

// Reconciled from three divergent app-local seams (Backlog #21/#26 + Mileway follow-up):
//  - HireSignal app/.../crash/CrashReporter.kt: `record(Throwable)` + `setCrumb(k,v)`, Android-only
//    (android.util.Log + Thread.setDefaultUncaughtExceptionHandler — not wasmJs-safe).
//  - PaymentsLab core/common/CrashReporter.kt: `recordException`/`log`/`setCustomKey`/`setUserId` +
//    `NapierCrashReporter`, already pure-KMP.
//  - Mileway core/platform/CrossCuttingServices.kt: adds a `setEnabled` telemetry kill switch that
//    gates reporting for consent, mirroring Crashlytics' collection-enabled flag.
// The union wins: PaymentsLab's method set + Mileway's kill switch. HireSignal's uncaught-handler
// installer is Android-specific process wiring and stays app-side, not part of this seam. Default
// impl routes through this module's own [AppLog] (already a :common dep).

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

    /**
     * Telemetry kill switch — when `false`, subsequent reports drop silently (consent gating, the
     * same way Crashlytics' collection-enabled flag works). Default no-op so existing implementers
     * that don't gate telemetry keep compiling unchanged; reporters that respect consent override it.
     */
    fun setEnabled(enabled: Boolean) {}
}

/**
 * Dependency-free default [CrashReporter] that routes everything through [AppLog] (Napier). Gives
 * the same call sites and breadcrumb discipline as a real backend, minus the upload. Honors the
 * [setEnabled] kill switch (starts enabled); when disabled, reporting calls drop silently.
 */
class NapierCrashReporter : CrashReporter {
    @Volatile
    private var enabled = true

    override fun recordException(
        throwable: Throwable,
        message: String?,
    ) {
        if (!enabled) return
        AppLog.e(message ?: "non-fatal", throwable, TAG)
    }

    override fun log(breadcrumb: String) {
        if (!enabled) return
        AppLog.d("breadcrumb: $breadcrumb", TAG)
    }

    override fun setCustomKey(
        key: String,
        value: String,
    ) {
        if (!enabled) return
        AppLog.d("key $key=$value", TAG)
    }

    override fun setUserId(id: String?) = AppLog.d("userId=$id", TAG)

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    private companion object {
        const val TAG = "CrashReporter"
    }
}
