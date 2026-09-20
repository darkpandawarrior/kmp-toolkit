package com.siddharth.kmp.biometric

/**
 * Why biometric authentication can or cannot run *right now*, on this device, in this app.
 *
 * This is the capability flag for [BiometricAuthenticator], and it is deliberately **not** a
 * `Boolean`. A boolean answers "can I?" and throws away the only part a caller can act on: a device
 * with no sensor needs a different fallback (PIN entry, forever) than one where the user simply has
 * not enrolled a fingerprint yet (deep-link them to Settings), and both differ from "the host app
 * never wired this up" (a bug, not a device state). The `shareText` silent-no-op is what happens
 * when that distinction is thrown away — the button did nothing and nobody could say why.
 *
 * [reason] is a plain-English sentence, safe to log and safe to show, so even the cases this
 * hierarchy does not name arrive with an explanation attached.
 */
sealed interface BiometricAvailability {
    /** Plain-English explanation of this state. Never blank. */
    val reason: String

    /** True only for [Available]; a convenience for `if`, not a replacement for [reason]. */
    val isAvailable: Boolean get() = this is Available

    /** A strong biometric is enrolled and ready; [BiometricAuthenticator.authenticate] will prompt. */
    data object Available : BiometricAvailability {
        override val reason: String = "Biometric authentication is available."
    }

    /** No biometric sensor on this device. Permanent — fall back to a non-biometric factor. */
    data object NoHardware : BiometricAvailability {
        override val reason: String = "This device has no biometric hardware."
    }

    /** Hardware exists but nothing is enrolled. Recoverable — send the user to system settings. */
    data object NoneEnrolled : BiometricAvailability {
        override val reason: String = "No biometric is enrolled on this device."
    }

    /** No device passcode/screen lock, so the OS disables biometrics entirely. Recoverable. */
    data object PasscodeNotSet : BiometricAvailability {
        override val reason: String = "No device passcode or screen lock is set, so biometrics are disabled."
    }

    /** Too many failed attempts. Temporary on both platforms; a passcode unlock clears it. */
    data object LockedOut : BiometricAvailability {
        override val reason: String = "Too many failed attempts — biometrics are locked out for now."
    }

    /**
     * The host app has not wired biometrics up yet. On Android that means no `Activity` provider was
     * installed (`BiometricAndroid.install { … }`); `BiometricPrompt` needs a `FragmentActivity` and
     * a library module cannot invent one. This is a wiring bug in the app, not a device state.
     */
    data object NotConfigured : BiometricAvailability {
        override val reason: String =
            "Biometrics are not wired up by the host app (Android: call BiometricAndroid.install { activity })."
    }

    /** Anything the platform reported that the cases above do not name; [reason] carries the detail. */
    data class Unavailable(override val reason: String) : BiometricAvailability
}

/**
 * Outcome of one [BiometricAuthenticator.authenticate] call.
 *
 * Four cases, because callers branch four ways: let them in, let them retry, offer another factor,
 * or never offer biometrics again this session.
 */
sealed interface BiometricResult {
    /** The user authenticated. */
    data object Success : BiometricResult

    /**
     * The user dismissed the prompt, or chose the fallback/cancel button, or the system took the
     * prompt away (an incoming call). Not a failure — do not show an error, just stay where you are.
     */
    data object Cancelled : BiometricResult

    /** The prompt ran and did not authenticate the user. Retrying is reasonable. */
    data class Failed(val reason: String) : BiometricResult

    /**
     * No prompt was ever shown, because [BiometricAuthenticator.canAuthenticate] would have said so.
     * Carries the same [BiometricAvailability] a pre-flight check returns, so a caller that skipped
     * the check still gets the actionable reason rather than a bare failure.
     */
    data class Unavailable(val availability: BiometricAvailability) : BiometricResult
}

/**
 * Face/Touch ID on iOS, `BiometricPrompt` on Android, one suspending call.
 *
 * Android's half needs a `FragmentActivity`, which is why `:security`'s `BiometricGuard` could never
 * move to `commonMain` — it takes the activity as a parameter. This module inverts that: the host app
 * installs an activity provider once (`BiometricAndroid.install { … }`), and everything above the
 * seam stays platform-free. `:security` keeps `BiometricGuard` as its Android-only, VAPT-surface
 * companion; this is the cross-platform door.
 *
 * Always check [canAuthenticate] before offering a biometric affordance at all. [authenticate]
 * re-checks and returns [BiometricResult.Unavailable] rather than showing nothing, so a caller that
 * forgets still gets a reason instead of silence.
 *
 * ### What the consuming app must declare
 * - **Android** — `androidx.biometric` needs no manifest permission on API 28+; the prompt host must
 *   be a `FragmentActivity` (`ComponentActivity` alone is not enough).
 * - **iOS** — `NSFaceIDUsageDescription` in `Info.plist`. Without it Face ID evaluation fails at
 *   runtime (Touch ID does not need it), which surfaces here as [BiometricResult.Failed].
 */
expect class BiometricAuthenticator() {
    /** Why biometric authentication can or cannot run right now. See [BiometricAvailability]. */
    fun canAuthenticate(): BiometricAvailability

    /**
     * Shows the system biometric prompt and suspends until the user resolves it.
     *
     * Cancelling the calling coroutine dismisses the prompt.
     *
     * @param title headline of the prompt. Android shows it verbatim; iOS has no title slot and
     *   falls back to it as the reason when [subtitle] is blank.
     * @param subtitle the "why are you asking" line. On iOS this is `localizedReason`, which the
     *   system alert always shows — pass something meaningful.
     * @param cancelLabel text of the escape hatch (Android's negative button, iOS's cancel title).
     */
    suspend fun authenticate(
        title: String,
        subtitle: String = "",
        cancelLabel: String = "Cancel",
    ): BiometricResult
}
