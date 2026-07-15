package com.siddharth.kmp.common

import kotlin.test.Test

class CrashReporterTest {
    // Smoke test: NapierCrashReporter has no antilog installed in commonTest, so every call is a
    // safe no-op — this only guards that none of the four methods throw.
    @Test
    fun napierCrashReporterMethodsDoNotThrowWithoutAnAntilogInstalled() {
        val reporter: CrashReporter = NapierCrashReporter()
        reporter.recordException(IllegalStateException("boom"))
        reporter.recordException(IllegalStateException("boom"), "with message")
        reporter.log("breadcrumb")
        reporter.setCustomKey("gateway", "stripe")
        reporter.setUserId("user-123")
        reporter.setUserId(null)
    }
}
