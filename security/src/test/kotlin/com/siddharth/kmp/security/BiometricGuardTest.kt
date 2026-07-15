package com.siddharth.kmp.security

import android.content.Context
import androidx.biometric.BiometricManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Verifies the [BiometricManager.canAuthenticate] result → [BiometricGuard.Availability] mapping.
 * The static `BiometricManager.from` is mocked so this runs on the JVM without a device.
 */
class BiometricGuardTest {
    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<BiometricManager>()

    @Before
    fun setUp() {
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns manager
    }

    @After
    fun tearDown() {
        unmockkStatic(BiometricManager::class)
    }

    private fun availabilityFor(code: Int): BiometricGuard.Availability {
        every { manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) } returns code
        return BiometricGuard.checkAvailability(context)
    }

    @Test
    fun success_mapsToAvailable() {
        assertEquals(BiometricGuard.Availability.Available, availabilityFor(BiometricManager.BIOMETRIC_SUCCESS))
    }

    @Test
    fun noHardware_mapsToNoHardware() {
        assertEquals(
            BiometricGuard.Availability.NoHardware,
            availabilityFor(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE),
        )
    }

    @Test
    fun noneEnrolled_mapsToNoneEnrolled() {
        assertEquals(
            BiometricGuard.Availability.NoneEnrolled,
            availabilityFor(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
        )
    }

    @Test
    fun hwUnavailable_mapsToUnavailable() {
        assertEquals(
            BiometricGuard.Availability.Unavailable,
            availabilityFor(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE),
        )
    }
}
