package com.siddharth.kmp.location.algo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TraceCodecTest {

    private val sample = Fix(
        lat = 12.9716,
        lng = 77.5946,
        timeMs = 1_723_000_000_000L,
        accuracyM = 7.5,
        speedMps = 12.25,
        bearingDeg = 91.0,
        altitudeM = 920.0,
        isMock = false,
        provider = "fused",
        odometerM = 45_231.0,
    )

    @Test
    fun round_trips_a_fully_populated_fix() {
        assertEquals(sample, TraceCodec.decode(TraceCodec.encode(sample)))
    }

    @Test
    fun round_trips_a_sparse_fix_keeping_absent_distinct_from_zero() {
        val sparse = Fix(lat = 1.0, lng = 2.0, timeMs = 5L, accuracyM = 3.0)
        val back = TraceCodec.decode(TraceCodec.encode(sparse))

        assertEquals(sparse, back)
        // The distinction that matters: "no speed reported" must not decode as "stationary".
        assertNull(back.speedMps, "absent speed decoded as a value")
        assertNull(back.odometerM)
        assertNull(back.provider)
    }

    @Test
    fun decodes_a_whole_file_skipping_header_blanks_and_comments() {
        val text = buildString {
            appendLine("# recorded 2026-08-09, city loop")
            appendLine(TraceCodec.HEADER)
            appendLine(TraceCodec.encode(sample))
            appendLine("")
            appendLine(TraceCodec.encode(sample.copy(timeMs = sample.timeMs + 1000)))
        }
        val fixes = TraceCodec.decodeAll(text)
        assertEquals(2, fixes.size)
        assertEquals(sample.timeMs, fixes[0].timeMs)
    }

    @Test
    fun encodeAll_output_is_readable_back_by_decodeAll() {
        val fixes = (0 until 25).map {
            sample.copy(timeMs = sample.timeMs + it * 1000L, lat = sample.lat + it * 0.0001)
        }
        assertEquals(fixes, TraceCodec.decodeAll(TraceCodec.encodeAll(fixes)))
    }

    @Test
    fun a_malformed_row_fails_loudly_with_its_line_number() {
        val bad = "${TraceCodec.HEADER}\n1000,12.97,NOT_A_NUMBER,5.0"
        val e = assertFailsWith<IllegalArgumentException> { TraceCodec.decodeAll(bad) }
        assertTrue(e.message!!.contains("line 2"), "error should name the line: ${e.message}")
        assertTrue(e.message!!.contains("lng"), "error should name the column: ${e.message}")
    }

    @Test
    fun a_truncated_row_is_rejected_rather_than_silently_defaulted() {
        assertFailsWith<IllegalArgumentException> { TraceCodec.decode("1000,12.97") }
    }

    @Test
    fun traceCase_reports_sampling_rate() {
        val fixes = (0 until 11).map { sample.copy(timeMs = sample.timeMs + it * 2_000L) }
        val case = TraceCase("2s city loop", fixes, truthMeters = 5000.0)

        assertEquals(20_000L, case.durationMs)
        assertEquals(2.0, case.meanIntervalSec, absoluteTolerance = 0.001)
    }

    @Test
    fun scoring_without_a_reference_distance_refuses_instead_of_guessing() {
        val case = TraceCase("unmeasured drive", listOf(sample), truthMeters = null)
        val e = assertFailsWith<IllegalStateException> { case.scoreAgainstTruth(PassThroughAlgorithm()) }
        assertTrue(e.message!!.contains("truthMeters"), "must explain what is missing: ${e.message}")
    }

    @Test
    fun a_recorded_trace_replays_through_an_algorithm_and_scores() {
        // A straight 10-leg run of ~111.19 m per leg ~= 1111.9 m.
        val fixes = (0 until 11).map {
            Fix(lat = 12.9 + it * 0.001, lng = 77.6, timeMs = 1000L + it * 5000L, accuracyM = 6.0)
        }
        val csv = TraceCodec.encodeAll(fixes)
        val case = TraceCase("synthetic straight line", TraceCodec.decodeAll(csv), truthMeters = 1111.9)

        val card = case.scoreAgainstTruth(PassThroughAlgorithm())
        assertTrue(card.absErrorPercent < 1.0, "expected <1% error on a clean trace, got $card")
    }
}
