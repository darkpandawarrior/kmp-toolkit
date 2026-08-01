package com.siddharth.kmp.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Pill-style page indicator for a carousel, banner or onboarding pager: the active page's dot
 * stretches into a pill, the rest stay round.
 *
 * Sizing is adaptive rather than fixed. The source this came from carried `indicatorWidth` /
 * `indicatorHeight` twice — once in `BannerDefaults` and again in `TvDimens` (28x6 on a phone rising
 * to 42x10 on a 4K panel) — because a 6dp dot is invisible from a sofa. Same rule here, derived from
 * the surface instead of duplicated per component.
 *
 * ```
 * val pager = rememberPagerState { banners.size }
 * PageIndicator(pager.currentPage, banners.size)
 * ```
 *
 * ponytail: takes `currentPage: Int` rather than a `PagerState`. `PagerState` lives in
 * `compose.foundation.pager`, and binding to it would stop this working for anything that isn't a
 * pager — an image carousel driven by a LazyRow, a step counter, a stack of onboarding cards. An Int
 * is the whole contract.
 */
@Composable
fun PageIndicator(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    if (pageCount <= 1) return

    // A dot has to grow with viewing distance: barely visible on a watch, unmissable across a room.
    val dotHeight: Dp = byFormFactor(default = 6.dp, watch = 4.dp, tv = 10.dp)
    val activeWidth: Dp = byFormFactor(default = 28.dp, watch = 14.dp, tv = 42.dp)
    val spacing: Dp = byFormFactor(default = DesignTokens.Spacing.s, watch = DesignTokens.Spacing.xs)

    Row(
        // Purely decorative: the pager's own content is what a screen reader should track.
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { page ->
            val active = page == currentPage
            val width by animateDpAsState(
                targetValue = if (active) activeWidth else dotHeight,
                animationSpec = tween(DesignTokens.Motion.FAST_MS),
                label = "pageIndicatorWidth",
            )
            Box(
                Modifier
                    .width(width)
                    .height(dotHeight)
                    // Half the height keeps a lone dot a true circle and the active pill fully round.
                    .background(
                        color = if (active) activeColor else inactiveColor,
                        shape = RoundedCornerShape(max(1f, dotHeight.value / 2f).dp),
                    ),
            )
        }
    }
}
