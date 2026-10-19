package com.siddharth.kmp.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/**
 * Whether haptics fire at all. Independent of any sound toggle on purpose: a user who mutes sound
 * still wants the tap, and one who disables haptics still wants the sound. `true` by default.
 */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** The three semantic haptic moments an app needs; mapped to the platform's own effects. */
class Haptics internal constructor(
    private val feedback: HapticFeedback,
    private val enabled: Boolean,
) {
    /** A light selection tick: choosing a chip, a segment, a list item. */
    fun tick() = fire(HapticFeedbackType.TextHandleMove)

    /** A successful commit: a form saved, a payment confirmed. */
    fun confirm() = fire(HapticFeedbackType.Confirm)

    /** A refused action: validation failed, a destructive action was blocked. */
    fun reject() = fire(HapticFeedbackType.Reject)

    /** A heavier press: long-press, drag pick-up. */
    fun heavy() = fire(HapticFeedbackType.LongPress)

    private fun fire(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }
}

/**
 * Haptics through Compose's own [LocalHapticFeedback], so it works on Android, iOS and desktop with
 * no install step (unlike the `:feedback` module's `FeedbackAndroid.install(context)`; that module
 * stays the home of custom vibration patterns and sound). Gated by [LocalHapticsEnabled] only.
 */
@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}

/** [accessibleClickable] that also fires [Haptics.tick] on press. */
@Composable
fun Modifier.hapticClickable(
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val haptics = rememberHaptics()
    return accessibleClickable(role, onClickLabel) {
        haptics.tick()
        onClick()
    }
}
