package com.siddharth.kmp.appshell

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The telemetry kill switch — [LoggingAnalyticsHelper.setEnabled] must drop every call while
 * disabled and resume passing them through once re-enabled. A recording [Antilog] stands in for the
 * real Napier backend so the assertions don't depend on log output formatting.
 */
class LoggingAnalyticsHelperTest {
    private class RecordingAntilog : Antilog() {
        val messages = mutableListOf<String>()

        override fun performLog(
            priority: LogLevel,
            tag: String?,
            throwable: Throwable?,
            message: String?,
        ) {
            message?.let { messages.add(it) }
        }
    }

    private val recorder = RecordingAntilog()

    @BeforeTest
    fun setUp() {
        Napier.base(recorder)
    }

    @AfterTest
    fun tearDown() {
        Napier.takeLogarithm()
    }

    @Test
    fun disabled_analytics_sink_drops_events() {
        val sink = LoggingAnalyticsHelper()
        sink.setEnabled(false)

        sink.log(AnalyticsEvent("trip_started"))
        sink.setUserProperty("plan", "pro")

        assertTrue(recorder.messages.isEmpty())
    }

    @Test
    fun enabled_analytics_sink_passes_events_through() {
        val sink = LoggingAnalyticsHelper()

        sink.log(AnalyticsEvent("trip_started"))

        assertEquals(1, recorder.messages.size)
        assertTrue(recorder.messages.single().contains("trip_started"))
    }

    @Test
    fun re_enabling_the_analytics_sink_resumes_delivery() {
        val sink = LoggingAnalyticsHelper()
        sink.setEnabled(false)
        sink.log(AnalyticsEvent("dropped"))
        sink.setEnabled(true)
        sink.log(AnalyticsEvent("delivered"))

        assertEquals(1, recorder.messages.size)
        assertTrue(recorder.messages.single().contains("delivered"))
    }
}
