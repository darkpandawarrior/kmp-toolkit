package com.siddharth.kmp.location.algo

/**
 * Read and write recorded GPS traces, so a real drive becomes a regression test.
 *
 * Every claim about a mileage algorithm — "this is more accurate", "this threshold helps",
 * "the new version didn't regress" — is unfalsifiable until the same drive can be replayed
 * through two algorithms and the numbers compared. A recorded trace plus a trusted reference
 * distance is the only thing that turns tuning from taste into measurement.
 *
 * **Why CSV and not JSON.** `:location` has zero dependencies, and it must stay that way to keep
 * compiling for wasmJs. That leaves hand-rolling a parser, and a hand-rolled JSON parser is a
 * genuine source of bugs for no benefit here: a GPS trace is a table. CSV is a few lines of
 * `split`, trivially diffable in git, and openable in any spreadsheet when someone wants to eyeball
 * a bad drive. If richer structure is ever needed, serialize at the app edge where dependencies
 * are allowed.
 *
 * ponytail: positional columns with a header line, no quoting or escaping support. Fields are
 * numbers, a boolean and a short provider token — none of which contain commas. If a provider
 * name ever does, quote it at the source; do not grow a CSV dialect in here.
 */
public object TraceCodec {
    public const val HEADER: String =
        "timeMs,lat,lng,accuracyM,speedMps,bearingDeg,altitudeM,isMock,odometerM,provider"

    /**
     * Column positions in the CSV row, in HEADER order. Named rather than bare so a change to the
     * format is one edit here instead of nine unlabelled integers spread across the parser.
     */
    private const val COL_TIME_MS = 0
    private const val COL_LAT = 1
    private const val COL_LNG = 2
    private const val COL_ACCURACY_M = 3
    private const val COL_SPEED_MPS = 4
    private const val COL_BEARING_DEG = 5
    private const val COL_ALTITUDE_M = 6
    private const val COL_IS_MOCK = 7
    private const val COL_ODOMETER_M = 8
    private const val COL_PROVIDER = 9

    /** timeMs, lat, lng and accuracyM are mandatory; everything after them is optional. */
    private const val REQUIRED_COLUMNS = 4

    /**
     * Serialize one fix. Absent optional values are written as empty fields, not as `0`, because
     * "no speed reported" and "stationary" are different facts and conflating them silently
     * changes how a jitter gate behaves.
     */
    public fun encode(fix: Fix): String =
        buildString {
            append(fix.timeMs)
            append(',')
            append(fix.lat)
            append(',')
            append(fix.lng)
            append(',')
            append(fix.accuracyM)
            append(',')
            append(fix.speedMps ?: "")
            append(',')
            append(fix.bearingDeg ?: "")
            append(',')
            append(fix.altitudeM ?: "")
            append(',')
            append(if (fix.isMock) "1" else "0")
            append(',')
            append(fix.odometerM ?: "")
            append(',')
            append(fix.provider ?: "")
        }

    public fun encodeAll(fixes: List<Fix>): String = (listOf(HEADER) + fixes.map(::encode)).joinToString("\n")

    /**
     * Parse one CSV row into a [Fix], or throw with the line number and the offending text.
     *
     * Failing loudly is deliberate. A trace file that silently drops a malformed row produces a
     * benchmark result that is quietly wrong, which is worse than no benchmark: it looks like
     * evidence.
     */

    public fun decode(
        line: String,
        lineNumber: Int = -1,
    ): Fix {
        val f = line.split(',')
        require(f.size >= REQUIRED_COLUMNS) {
            "trace line $lineNumber: expected at least $REQUIRED_COLUMNS columns, got ${f.size} in '$line'"
        }

        fun req(
            i: Int,
            name: String,
        ): Double =
            f[i].trim().toDoubleOrNull()
                ?: throw IllegalArgumentException("trace line $lineNumber: '$name' is not a number: '${f[i]}'")

        fun opt(i: Int): Double? =
            f
                .getOrNull(i)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.toDoubleOrNull()

        val timeMs =
            f[COL_TIME_MS].trim().toLongOrNull()
                ?: throw IllegalArgumentException(
                    "trace line $lineNumber: 'timeMs' is not a Long: '${f[COL_TIME_MS]}'",
                )

        return Fix(
            lat = req(COL_LAT, "lat"),
            lng = req(COL_LNG, "lng"),
            timeMs = timeMs,
            accuracyM = req(COL_ACCURACY_M, "accuracyM"),
            speedMps = opt(COL_SPEED_MPS),
            bearingDeg = opt(COL_BEARING_DEG),
            altitudeM = opt(COL_ALTITUDE_M),
            isMock =
                f.getOrNull(COL_IS_MOCK)?.trim().let { it == "1" || it.equals("true", ignoreCase = true) },
            odometerM = opt(COL_ODOMETER_M),
            provider = f.getOrNull(COL_PROVIDER)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** Parse a whole file. Blank lines, `#` comments and a leading header row are skipped. */
    public fun decodeAll(text: String): List<Fix> =
        text
            .lineSequence()
            .withIndex()
            .filter { (_, l) ->
                val t = l.trim()
                t.isNotEmpty() && !t.startsWith("#") && !t.startsWith("timeMs")
            }.map { (i, l) -> decode(l, lineNumber = i + 1) }
            .toList()
}

/**
 * A recorded drive plus what it is known to have actually been.
 *
 * [truthMeters] is the whole point and the hard part: an odometer photo pair, a surveyed route, or
 * a second trusted device. It is nullable because a trace is still useful for crash/behaviour
 * regression without it — but any *accuracy* claim made from a trace with no truth value is
 * circular, so [scoreAgainstTruth] refuses rather than inventing one.
 */
public data class TraceCase(
    val name: String,
    val fixes: List<Fix>,
    val truthMeters: Double? = null,
    /** Free-form: "urban canyon", "highway", "tunnel", "parked 20min mid-trip", "walked to car first". */
    val notes: String = "",
) {
    val durationMs: Long
        get() = if (fixes.size < 2) 0L else fixes.last().timeMs - fixes.first().timeMs

    /**
     * Mean seconds between fixes — the single most important property of a trace, because nearly
     * every threshold in a GPS algorithm is implicitly tuned to a sampling rate. A profile tuned on
     * 1 s traces will behave differently at 30 s and the numbers will look inexplicable.
     */
    val meanIntervalSec: Double
        get() = if (fixes.size < 2) 0.0 else durationMs / 1000.0 / (fixes.size - 1)

    public fun scoreAgainstTruth(algorithm: MileageAlgorithm): Scorecard {
        val truth =
            truthMeters
                ?: error(
                    "TraceCase '$name' has no truthMeters, so accuracy cannot be scored against it. " +
                        "Record a reference distance (odometer photos, surveyed route, second device) " +
                        "or use AlgorithmHarness.run() for a behaviour-only check.",
                )
        return AlgorithmHarness.run(algorithm, fixes).scoreAgainst(truth)
    }
}
