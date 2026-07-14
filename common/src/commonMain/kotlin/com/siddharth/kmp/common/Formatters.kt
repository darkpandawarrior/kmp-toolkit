package com.siddharth.kmp.common

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

// KMP-safe formatting helpers (no JVM-only `String.format` / `java.util.Date`), absorbed from the
// openMF KMP template's `FormatNumber`/`FormatDate` approach so UI code can live in commonMain.

/** Two-digit zero-padded string for an Int (e.g. 5 -> "05"). */
fun Int.pad2(): String = if (this in 0..9) "0$this" else this.toString()

/** Decimal formatting without `String.format`. Rounds half-up to [places] digits. */
fun Double.formatDecimal(places: Int): String {
    if (places <= 0) return this.roundToLong().toString()
    var factor = 1L
    repeat(places) { factor *= 10 }
    val negative = this < 0
    val scaled = abs(this * factor).roundToLong()
    val intPart = scaled / factor
    val fracPart = (scaled % factor).toString().padStart(places, '0')
    return (if (negative && (intPart != 0L || fracPart.any { it != '0' })) "-" else "") + "$intPart.$fracPart"
}

/** Whole-number thousands-comma grouping, no `String.format` (e.g. 12345.6 -> "12,345"). */
fun Double.formatGrouped(): String {
    val whole = this.roundToLong()
    val grouped = abs(whole).toString().reversed().chunked(3).joinToString(",").reversed()
    return if (whole < 0) "-$grouped" else grouped
}

/** 24-hour time, e.g. (9, 5) -> "09:05". */
fun formatTime24h(
    hour: Int,
    minute: Int,
): String = "${hour.pad2()}:${minute.pad2()}"

/** 12-hour time with AM/PM, e.g. (13, 5) -> "1:05 PM". */
fun formatTime12h(
    hour: Int,
    minute: Int,
): String {
    val period = if (hour < 12) "AM" else "PM"
    val h12 =
        when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
    return "$h12:${minute.pad2()} $period"
}

/**
 * Renders minor currency units (e.g. paise, cents) to a `"<major>.<minor>"` decimal string with
 * [fractionDigits] digits — the single canonical minorUnits->decimal formatter for `:common` and
 * its consumers.
 *
 * Three call sites used to each roll their own: this file's [formatDecimal] used Double + HALF_UP,
 * googlepay's provider used `java.math.BigDecimal` + HALF_EVEN, and upi-intent's provider used a
 * raw Long divmod (`amountMinor / 100`, `amountMinor % 100`) that mis-formats negative amounts —
 * e.g. -1050 minor units rendered as `"-10.-50"` instead of `"-10.50"`, because Kotlin's `%` keeps
 * the dividend's sign. Since minor units are already an exact integer count of the smallest
 * currency unit there's no fractional precision to round away (the split is always exact); the
 * part that must be unified is sign handling, following the same abs+negative-flag convention as
 * [formatDecimal] so there is exactly one leading `-` and a magnitude-only fraction.
 */
fun Long.minorToDecimalString(fractionDigits: Int = 2): String {
    if (fractionDigits <= 0) return this.toString()
    var factor = 1L
    repeat(fractionDigits) { factor *= 10 }
    val negative = this < 0
    val magnitude = abs(this)
    val intPart = magnitude / factor
    val fracPart = (magnitude % factor).toString().padStart(fractionDigits, '0')
    return (if (negative) "-" else "") + "$intPart.$fracPart"
}

private val shortMonthNames =
    listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

/** Friendly date, e.g. "Jun 19, 2026". */
fun LocalDate.toFriendlyString(): String = "${shortMonthNames[month.ordinal]} $day, $year"
