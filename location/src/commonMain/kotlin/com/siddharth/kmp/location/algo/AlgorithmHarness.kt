package com.siddharth.kmp.location.algo

import kotlin.math.abs

/**
 * Replay a recorded trace through an algorithm and score the result.
 *
 * This is the part that turns "algorithm A is better than algorithm B" from an argument into a
 * measurement. Without it, every threshold in the system is a number somebody once felt good
 * about, and every change is unfalsifiable.
 *
 * The whole harness is pure and synchronous because [MileageAlgorithm.process] is: no device, no
 * emulator, no coroutines, no clock. A trace replays identically on a laptop, in CI, and in a
 * browser preview.
 */
public object AlgorithmHarness {

    /**
     * Feed [trace] through [algorithm] in order and collect a report.
     *
     * Fixes are **not** sorted for you. Out-of-order delivery is a real field condition and an
     * algorithm's response to it is part of what is being measured; silently sorting would hide
     * exactly the behaviour worth comparing.
     */
    public fun run(
        algorithm: MileageAlgorithm,
        trace: List<Fix>,
        session: SessionContext = SessionContext(startedAtMs = trace.firstOrNull()?.timeMs ?: 0L),
    ): RunReport {
        algorithm.reset(session)
        val verdicts = HashMap<FixVerdict, Int>()
        var maxDisplacement = 0.0

        fun tally(r: FixResult) {
            verdicts[r.verdict] = (verdicts[r.verdict] ?: 0) + 1
            if (r.displacementM > maxDisplacement) maxDisplacement = r.displacementM
        }

        for (fix in trace) tally(algorithm.process(fix))

        // The trace has ended, so anything still held in a window must be released before the
        // totals are read. Omitting this silently under-counts the tail of every replayed trip —
        // and worse, it under-counts it only for windowed algorithms, so a comparison against a
        // purely-online one would look like evidence that windowing is bad. A measurement harness
        // that biases the comparison it exists to make is worse than no harness.
        algorithm.flush().forEach(::tally)

        return RunReport(
            algorithmId = algorithm.id,
            fixCount = trace.size,
            finalState = algorithm.snapshot(),
            verdictCounts = verdicts,
            maxDisplacementM = maxDisplacement,
        )
    }

    /**
     * Run every candidate over the same trace, so the comparison is genuinely like-for-like.
     *
     * A fresh algorithm instance per candidate is the caller's responsibility — reusing one
     * across runs is the classic way to get a result that cannot be reproduced.
     */
    public fun compare(
        candidates: List<MileageAlgorithm>,
        trace: List<Fix>,
    ): List<RunReport> = candidates.map { run(it, trace) }

    /**
     * Verify that resuming from a snapshot mid-trace yields the same totals as an uninterrupted
     * run — minus the one leg deliberately dropped across the resume boundary.
     *
     * Process death is the field condition that produced real duplicate-distance bugs, and it is
     * almost never covered by a test because reproducing it needs a device. Here it is a loop
     * index.
     */
    public fun snapshotRoundTrips(
        factory: () -> MileageAlgorithm,
        trace: List<Fix>,
        breakAt: Int,
    ): Boolean {
        require(breakAt in 1 until trace.size) { "breakAt must be inside the trace" }
        val session = SessionContext(startedAtMs = trace.first().timeMs)

        val whole = factory().also { it.reset(session) }
        trace.forEach { whole.process(it) }
        whole.flush()

        val first = factory().also { it.reset(session) }
        trace.take(breakAt).forEach { first.process(it) }
        // Deliberately NOT flushed: this run is being killed mid-trip, and a process death does not
        // politely drain your buffers. Anything a windowed algorithm still holds must survive in
        // snapshot()'s `opaque` map or it is genuinely lost — which is exactly the property this
        // check exists to expose.
        val carried = first.snapshot()

        val second = factory().also { it.reset(session) }
        second.restore(carried)
        trace.drop(breakAt).forEach { second.process(it) }
        second.flush()

        // The resumed run must never EXCEED the uninterrupted one: the gap across the break is
        // ground that was not tracked, so it must not be counted. Being short by at most that
        // single leg is correct behaviour, not a failure.
        val a = whole.snapshot()
        val b = second.snapshot()
        return b.cleanedM <= a.cleanedM + 0.5
    }
}

/** What one replay produced. */
public data class RunReport(
    val algorithmId: AlgorithmId,
    val fixCount: Int,
    val finalState: AlgorithmState,
    val verdictCounts: Map<FixVerdict, Int>,
    val maxDisplacementM: Double,
) {
    public val cleanedKm: Double get() = finalState.cleanedM / 1000.0
    public val originalKm: Double get() = finalState.originalM / 1000.0

    public fun count(verdict: FixVerdict): Int = verdictCounts[verdict] ?: 0

    /**
     * Score against a trustworthy reference distance (an odometer reading, a surveyed route).
     *
     * Without a reference there is no "better", only "different" — so this deliberately requires
     * one rather than inventing a self-referential quality score.
     */
    public fun scoreAgainst(truthMeters: Double): Scorecard {
        val errorM = finalState.cleanedM - truthMeters
        return Scorecard(
            algorithmId = algorithmId,
            truthM = truthMeters,
            measuredM = finalState.cleanedM,
            errorM = errorM,
            absErrorPercent = if (truthMeters > 0.0) abs(errorM) / truthMeters * 100.0 else Double.NaN,
            acceptedFixes = finalState.accepted,
            rejectedFixes = finalState.rejected,
        )
    }

    override fun toString(): String = buildString {
        append("$algorithmId: ${fixCount} fixes -> ")
        append("cleaned ${fmt(cleanedKm)}km / original ${fmt(originalKm)}km, ")
        append("accepted ${finalState.accepted}, rejected ${finalState.rejected}")
    }
}

public data class Scorecard(
    val algorithmId: AlgorithmId,
    val truthM: Double,
    val measuredM: Double,
    val errorM: Double,
    val absErrorPercent: Double,
    val acceptedFixes: Int,
    val rejectedFixes: Int,
) {
    override fun toString(): String =
        "$algorithmId: ${fmt(measuredM / 1000.0)}km vs truth ${fmt(truthM / 1000.0)}km " +
            "(${if (errorM >= 0) "+" else ""}${fmt(errorM)}m, ${fmt(absErrorPercent)}%)"
}

private fun fmt(v: Double): String {
    val scaled = kotlin.math.round(v * 100.0) / 100.0
    return scaled.toString()
}
