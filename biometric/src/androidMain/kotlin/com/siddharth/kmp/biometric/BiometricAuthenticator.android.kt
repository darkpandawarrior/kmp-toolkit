package com.siddharth.kmp.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

// Not `const`: Authenticators.BIOMETRIC_STRONG is a Java interface field, and whether it counts as
// a compile-time constant is not worth betting a build on.
private val STRONG = BiometricManager.Authenticators.BIOMETRIC_STRONG

/**
 * Android actual — `androidx.biometric.BiometricPrompt`, gated on BIOMETRIC_STRONG (Class 3).
 *
 * Class 3 rather than WEAK because the callers this exists for are payment and secrets flows; a
 * Class 2 face unlock is not the factor you want guarding a saved card token. The same constant is
 * used for the pre-flight check and for the prompt, so a device can never pass one and fail the other.
 */
actual class BiometricAuthenticator actual constructor() {
    actual fun canAuthenticate(): BiometricAvailability {
        val activity = BiometricAndroid.activity() ?: return BiometricAvailability.NotConfigured
        return when (BiometricManager.from(activity).canAuthenticate(STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NoHardware
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability.Unavailable("Biometric hardware is busy or temporarily unavailable.")
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.Unavailable("A system security update is required before biometrics can be used.")
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                BiometricAvailability.Unavailable("This Android version does not support a Class 3 (strong) biometric.")
            else ->
                BiometricAvailability.Unavailable("Biometric status could not be determined on this device.")
        }
    }

    actual suspend fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
    ): BiometricResult {
        val availability = canAuthenticate()
        if (availability !is BiometricAvailability.Available) return BiometricResult.Unavailable(availability)
        val activity = BiometricAndroid.activity() ?: return BiometricResult.Unavailable(BiometricAvailability.NotConfigured)
        // BiometricPrompt.authenticate() must be called on the main thread; callers suspend from
        // wherever they like, so hop rather than documenting a precondition nobody reads.
        return withContext(Dispatchers.Main) { prompt(activity, title, subtitle, cancelLabel) }
    }

    private suspend fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancelLabel: String,
    ): BiometricResult =
        suspendCancellableCoroutine { continuation ->
            val dialog =
                BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (continuation.isActive) continuation.resume(BiometricResult.Success)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (continuation.isActive) continuation.resume(errorCode.toResult(errString))
                        }

                        // Deliberately empty: a non-matching finger is NOT terminal — the prompt stays
                        // up and retries, and resuming here would leave a live dialog over a screen
                        // that already moved on. The terminal answer always arrives via
                        // onAuthenticationError (ERROR_LOCKOUT, ERROR_USER_CANCELED, …).
                        override fun onAuthenticationFailed() = Unit
                    },
                )
            continuation.invokeOnCancellation { activity.runOnUiThread { dialog.cancelAuthentication() } }
            dialog.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle.takeIf { it.isNotBlank() })
                    .setNegativeButtonText(cancelLabel)
                    .setAllowedAuthenticators(STRONG)
                    .build(),
            )
        }
}

private fun Int.toResult(errString: CharSequence): BiometricResult =
    when (this) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED,
        -> BiometricResult.Cancelled
        BiometricPrompt.ERROR_LOCKOUT,
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
        -> BiometricResult.Unavailable(BiometricAvailability.LockedOut)
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> BiometricResult.Unavailable(BiometricAvailability.PasscodeNotSet)
        BiometricPrompt.ERROR_HW_NOT_PRESENT -> BiometricResult.Unavailable(BiometricAvailability.NoHardware)
        BiometricPrompt.ERROR_NO_BIOMETRICS -> BiometricResult.Unavailable(BiometricAvailability.NoneEnrolled)
        else -> BiometricResult.Failed(errString.toString().ifBlank { "Biometric error $this." })
    }
