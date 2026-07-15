package com.siddharth.kmp.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt

/**
 * Counts up from zero to [target] on first composition — e.g. "62 gateways integrated" or a "94%"
 * success rate. [suffix] is appended verbatim after the number (e.g. "%").
 *
 * Extracted from PaymentsLab's `core/designsystem/AnimatedCounter.kt` (backlog #31). The original
 * read a `LocalReducedMotion` CompositionLocal owned by that app; [reducedMotion] is now an
 * explicit parameter for the same reason documented on [RedactionReveal].
 */
@Composable
fun AnimatedCounter(
    target: Int,
    modifier: Modifier = Modifier,
    suffix: String = "",
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    reducedMotion: Boolean = false,
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (reducedMotion) {
            animated.snapTo(target.toFloat())
        } else {
            animated.animateTo(
                target.toFloat(),
                // ponytail: canonical DesignTokens.Motion has no Easing token (Int ms only); the
                // source app's `standardEasing` alias was FastOutSlowInEasing directly.
                tween(DesignTokens.Motion.MEDIUM_MS, easing = FastOutSlowInEasing),
            )
        }
    }
    Text(text = "${animated.value.roundToInt()}$suffix", style = style, modifier = modifier)
}
