@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.siddharth.kmp.biometric

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

// LAError codes, as raw NSInteger values. Spelled out rather than referenced through the
// LocalAuthentication bindings on purpose: the generated constant names for this enum have moved
// between Kotlin/Native releases, and these numbers are frozen Apple ABI — they cannot drift.
// <https://developer.apple.com/documentation/localauthentication/laerror>
private const val USER_CANCEL = -2L
private const val USER_FALLBACK = -3L
private const val SYSTEM_CANCEL = -4L
private const val PASSCODE_NOT_SET = -5L
private const val BIOMETRY_NOT_AVAILABLE = -6L
private const val BIOMETRY_NOT_ENROLLED = -7L
private const val BIOMETRY_LOCKOUT = -8L
private const val APP_CANCEL = -9L

/**
 * iOS actual — `LAContext` / LocalAuthentication, the Face ID + Touch ID counterpart to Android's
 * `BiometricPrompt`. Compiles and links against the simulator framework; an actual prompt needs a
 * real device (the simulator's "Matching Face" menu aside).
 *
 * A fresh [LAContext] per call, deliberately. A context caches its evaluation for a few minutes,
 * so reusing one means the second "authenticate to pay" silently succeeds without asking — exactly
 * the kind of invisible weakening this module exists to avoid.
 */
actual class BiometricAuthenticator actual constructor() {
    actual fun canAuthenticate(): BiometricAvailability =
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error.ptr)) {
                BiometricAvailability.Available
            } else {
                error.value.toAvailability()
            }
        }

    actual suspend fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
    ): BiometricResult {
        val availability = canAuthenticate()
        if (availability !is BiometricAvailability.Available) return BiometricResult.Unavailable(availability)

        val context = LAContext()
        if (cancelLabel.isNotBlank()) context.localizedCancelTitle = cancelLabel
        // iOS has no title slot — the system alert shows localizedReason and nothing else, so the
        // title is the fallback rather than being dropped on the floor.
        val localizedReason = subtitle.ifBlank { title }

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { context.invalidate() }
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = localizedReason,
            ) { success: Boolean, error: NSError? ->
                if (continuation.isActive) continuation.resume(toResult(success, error))
            }
        }
    }
}

private fun NSError?.toAvailability(): BiometricAvailability =
    when (this?.code) {
        BIOMETRY_NOT_AVAILABLE -> BiometricAvailability.NoHardware
        BIOMETRY_NOT_ENROLLED -> BiometricAvailability.NoneEnrolled
        PASSCODE_NOT_SET -> BiometricAvailability.PasscodeNotSet
        BIOMETRY_LOCKOUT -> BiometricAvailability.LockedOut
        // NSFaceIDUsageDescription missing from Info.plist lands here, as an -6 with a description
        // saying so — which is why the reason is carried through verbatim rather than flattened.
        else -> BiometricAvailability.Unavailable(
            this?.localizedDescription ?: "Biometric authentication is unavailable on this device.",
        )
    }

private fun toResult(
    success: Boolean,
    error: NSError?,
): BiometricResult =
    when {
        success -> BiometricResult.Success
        error == null -> BiometricResult.Failed("Biometric authentication did not succeed.")
        else ->
            when (error.code) {
                // USER_FALLBACK is "Enter Password" — the user chose another factor, so it is a
                // dismissal of biometrics, not a failure of them. The caller's own passcode path
                // takes over from here.
                USER_CANCEL, SYSTEM_CANCEL, APP_CANCEL, USER_FALLBACK -> BiometricResult.Cancelled
                BIOMETRY_LOCKOUT -> BiometricResult.Unavailable(BiometricAvailability.LockedOut)
                BIOMETRY_NOT_AVAILABLE -> BiometricResult.Unavailable(BiometricAvailability.NoHardware)
                BIOMETRY_NOT_ENROLLED -> BiometricResult.Unavailable(BiometricAvailability.NoneEnrolled)
                PASSCODE_NOT_SET -> BiometricResult.Unavailable(BiometricAvailability.PasscodeNotSet)
                // LAErrorAuthenticationFailed (-1) and anything newer than this list.
                else -> BiometricResult.Failed(error.localizedDescription)
            }
    }
