package com.siddharth.kmp.common

/** Digit-grouping style for [formatMoney]. */
enum class Grouping {
    /** `1249900` -> `1249900`. */
    None,

    /** Thousands: `1,249,900`. */
    Western,

    /** Indian lakh/crore: last three digits, then pairs: `12,49,900`. */
    Indian,
}

/**
 * Display formatter for money held as minor units (paise, cents): grouping, symbol and a fixed
 * fraction, e.g. `24990000L.formatMoney("₹", grouping = Grouping.Indian)` -> `₹2,49,900.00`.
 *
 * - [minorDigits] is the currency exponent of the receiver (2 for INR/USD, 0 for JPY, 3 for KWD).
 * - [fractionDigits] is how many to show, clamped to `0..minorDigits`. Showing fewer than
 *   [minorDigits] rounds **half-up** (away from zero on the magnitude: 0.5 -> 1, -0.5 -> -1), never
 *   truncates. Display only: keep ledger values exact in minor units. The wire-format formatter
 *   stays [minorToDecimalString].
 * - The sign goes before the symbol (`-₹10.50`); a value that rounds to zero shows no sign.
 * - [dropZeroFraction] hides an all-zero fraction (`₹100` instead of `₹100.00`).
 * - `Long.MIN_VALUE` is handled (the magnitude is computed unsigned).
 */
fun Long.formatMoney(
    symbol: String = "",
    minorDigits: Int = 2,
    fractionDigits: Int = minorDigits,
    grouping: Grouping = Grouping.Western,
    dropZeroFraction: Boolean = false,
): String {
    val minor = minorDigits.coerceIn(0, MAX_MINOR_DIGITS)
    val shown = fractionDigits.coerceIn(0, minor)
    val negative = this < 0
    val magnitude: ULong = if (negative) 0uL - this.toULong() else this.toULong()

    val dropFactor = pow10(minor - shown)
    var rounded = magnitude / dropFactor
    if (dropFactor > 1uL && (magnitude % dropFactor) * HALF_DENOMINATOR >= dropFactor) rounded += 1uL

    val shownFactor = pow10(shown)
    val whole = (rounded / shownFactor).toString().groupDigits(grouping)
    val frac = (rounded % shownFactor).toString().padStart(shown, '0')

    val sign = if (negative && rounded != 0uL) "-" else ""
    val fraction = if (shown == 0 || (dropZeroFraction && frac.all { it == '0' })) "" else ".$frac"
    return "$sign$symbol$whole$fraction"
}

private const val MAX_MINOR_DIGITS = 18
private const val RADIX = 10uL
private const val HALF_DENOMINATOR = 2uL
private const val GROUP = 3
private const val LAKH_GROUP = 2

private fun pow10(n: Int): ULong {
    var f = 1uL
    repeat(n) { f *= RADIX }
    return f
}

private fun String.groupDigits(grouping: Grouping): String =
    when {
        grouping == Grouping.None || length <= GROUP -> this
        grouping == Grouping.Western -> reversed().chunked(GROUP).joinToString(",").reversed()
        else -> {
            val tail = takeLast(GROUP)
            val head =
                dropLast(GROUP)
                    .reversed()
                    .chunked(LAKH_GROUP)
                    .joinToString(",")
                    .reversed()
            "$head,$tail"
        }
    }
