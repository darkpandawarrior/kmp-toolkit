package com.siddharth.kmp.biometric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [BiometricAuthenticator] itself needs a device (an Activity on Android, a Secure Enclave on iOS),
 * so what is testable in common code is the part that carries the meaning: the capability flag.
 *
 * The whole point of [BiometricAvailability] over a `Boolean` is that every state explains itself.
 * A case added later with a blank or copy-pasted reason would quietly undo that, which is exactly
 * the regression these two tests exist to catch.
 */
class BiometricAvailabilityTest {
    private val all =
        listOf(
            BiometricAvailability.Available,
            BiometricAvailability.NoHardware,
            BiometricAvailability.NoneEnrolled,
            BiometricAvailability.PasscodeNotSet,
            BiometricAvailability.LockedOut,
            BiometricAvailability.NotConfigured,
            BiometricAvailability.Unavailable("Biometric hardware is busy."),
        )

    @Test
    fun everyStateExplainsItselfAndNoTwoShareAnExplanation() {
        all.forEach { assertTrue(it.reason.isNotBlank(), "$it must carry a reason") }
        assertEquals(all.size, all.map { it.reason }.distinct().size, "each state needs its own reason: $all")
    }

    @Test
    fun onlyAvailableIsAvailable() {
        assertTrue(BiometricAvailability.Available.isAvailable)
        all
            .filter { it != BiometricAvailability.Available }
            .forEach { assertFalse(it.isAvailable, "$it must not report itself available") }
    }

    @Test
    fun unavailableCarriesThePlatformReasonVerbatim() {
        val reason = "Face ID is unavailable: NSFaceIDUsageDescription is missing."
        assertEquals(reason, BiometricAvailability.Unavailable(reason).reason)
        assertEquals(
            reason,
            (BiometricResult.Unavailable(BiometricAvailability.Unavailable(reason)).availability).reason,
            "a result must not flatten away the reason a pre-flight check would have given",
        )
    }
}
