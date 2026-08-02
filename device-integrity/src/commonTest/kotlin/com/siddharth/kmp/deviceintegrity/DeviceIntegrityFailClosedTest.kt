package com.siddharth.kmp.deviceintegrity

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The property that matters most in a root-detection library: it must never answer "safe" when it
 * did not actually look.
 */
class DeviceIntegrityFailClosedTest {
    @Test
    fun anUninspectedReportIsCompromisedEvenThoughEveryFlagIsFalse() {
        // Exactly the shape the Android actual returns when no Context was installed.
        val report = DeviceIntegrityReport(
            rooted = false,
            emulator = false,
            debuggerAttached = false,
            signals = listOf("no Context installed"),
            inspected = false,
        )
        assertFalse(report.rooted)
        assertFalse(report.debuggerAttached)
        assertTrue(report.isCompromised, "an un-run inspection must fail closed, not report clean")
    }

    @Test
    fun aRealCleanDeviceIsNotCompromised() {
        val report = DeviceIntegrityReport(false, false, false, emptyList(), inspected = true)
        assertFalse(report.isCompromised)
    }

    @Test
    fun inspectedDefaultsToTrueSoExistingCallSitesKeepTheirMeaning() {
        // The parameter was added with a default; a report built the old way still means "measured".
        val report = DeviceIntegrityReport(false, false, false, emptyList())
        assertTrue(report.inspected)
        assertFalse(report.isCompromised)
    }

    @Test
    fun rootAndDebuggerStillGateWhenInspected() {
        assertTrue(DeviceIntegrityReport(true, false, false, emptyList()).isCompromised)
        assertTrue(DeviceIntegrityReport(false, false, true, emptyList()).isCompromised)
        // Emulator alone still does not gate — CI and QA run on emulators.
        assertFalse(DeviceIntegrityReport(false, true, false, emptyList()).isCompromised)
    }
}
