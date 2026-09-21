package com.siddharth.kmp.biometric

import androidx.fragment.app.FragmentActivity

/**
 * The one wiring hook Android needs: `BiometricPrompt` hosts itself in a [FragmentActivity], and a
 * library module has no way to reach one on its own.
 *
 * Install a **provider**, not an activity. The provider is asked for the *currently resumed*
 * activity at the moment a prompt is shown, so nothing here outlives a rotation:
 *
 * ```kotlin
 * // Application.onCreate(), alongside registerActivityLifecycleCallbacks:
 * BiometricAndroid.install { currentResumedActivity as? FragmentActivity }
 * ```
 *
 * A lambda that captures one activity instance directly *will* leak it for the process lifetime —
 * return the current one from a holder your app already keeps, or clear it with [install]`(null)`
 * when that activity is destroyed.
 *
 * Until a provider is installed (or while it returns `null`), [BiometricAuthenticator.canAuthenticate]
 * reports [BiometricAvailability.NotConfigured] — an answer with a reason, not a dead button.
 */
object BiometricAndroid {
    @Volatile
    private var provider: (() -> FragmentActivity?)? = null

    /** Installs (or, with `null`, removes) the current-activity provider. */
    fun install(provider: (() -> FragmentActivity?)?) {
        this.provider = provider
    }

    internal fun activity(): FragmentActivity? = provider?.invoke()
}
