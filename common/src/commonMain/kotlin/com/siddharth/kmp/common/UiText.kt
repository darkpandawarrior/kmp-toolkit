package com.siddharth.kmp.common

/**
 * Platform-agnostic text holder so ViewModel state/effects never carry raw, pre-resolved strings —
 * the UI edge resolves a [UiText] to an actual `String` (via `stringResource` / moko-resources for
 * [Res], or directly for [Dynamic]). Domain/data layers stay locale-free.
 *
 * - [Dynamic] wraps a literal, runtime-built string.
 * - [Res] names a string-resource key (with optional [args]) resolved at the UI edge. Without a
 *   shared resource table in `commonMain`, [asString] falls back to the key.
 * - [Empty] is an explicit "no text" so a caller never has to reach for `Dynamic("")`.
 */
sealed interface UiText {
    data class Dynamic(val value: String) : UiText

    data class Res(val key: String, val args: List<String> = emptyList()) : UiText

    data object Empty : UiText

    companion object {
        /** `null`/blank → [Empty]; otherwise a [Dynamic] literal. */
        fun of(value: String?): UiText = if (value.isNullOrBlank()) Empty else Dynamic(value)
    }
}

/** Best-effort resolution with no resource table: [Dynamic] → its value, [Res] → its key, [Empty] → "". */
fun UiText.asString(): String =
    when (this) {
        is UiText.Dynamic -> value
        is UiText.Res -> key
        UiText.Empty -> ""
    }
