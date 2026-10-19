package com.siddharth.kmp.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * A placeholder block painted in `surfaceVariant`, shimmering unless [reducedMotion] is set, in which
 * case it is a flat static block. Decorative: it hides itself from accessibility, so pair a group of
 * skeletons with a [LoadingState]-style description on the container instead.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = DesignTokens.Shape.chip,
    reducedMotion: Boolean = LocalReducedMotion.current,
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val base0 = Modifier.clip(shape).background(base).clearAndSetSemantics { }
    if (reducedMotion) {
        Box(modifier.then(base0))
        return
    }
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "skeletonShimmer",
    )
    Box(
        modifier.then(base0).drawWithContent {
            drawContent()
            val w = size.width
            val x = -w + progress * 2f * w
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + w, size.height),
                ),
            )
        },
    )
}

/** A list-row shaped skeleton: a round avatar and [lines] text bars (the last one shorter). */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    lines: Int = 2,
    reducedMotion: Boolean = LocalReducedMotion.current,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.m),
    ) {
        SkeletonBlock(Modifier.size(40.dp), CircleShape, reducedMotion)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.s)) {
            repeat(lines.coerceAtLeast(1)) { i ->
                val last = i == lines - 1 && lines > 1
                SkeletonBlock(
                    Modifier.fillMaxWidth(if (last) 0.6f else 1f).height(12.dp),
                    reducedMotion = reducedMotion,
                )
            }
        }
    }
}

private const val SHIMMER_MS = 1200
