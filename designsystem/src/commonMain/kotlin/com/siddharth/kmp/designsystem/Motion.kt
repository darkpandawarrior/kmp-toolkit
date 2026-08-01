package com.siddharth.kmp.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Screen-level enter transition: a fade with a barely-there scale-up. Deliberately plain
 * [EnterTransition] values rather than the `AnimatedContentTransitionScope<NavBackStackEntry>.() ->`
 * lambdas the idiom usually ships as — that signature drags in `androidx.navigation`, which this
 * module does not depend on and which has no Compose Multiplatform equivalent on every target.
 *
 * Works anywhere a transition is accepted:
 * ```
 * NavHost(enterTransition = { screenEnter }, exitTransition = { screenExit }) { ... }
 * AnimatedContent(target, transitionSpec = { screenEnter togetherWith screenExit }) { ... }
 * AnimatedVisibility(visible, enter = screenEnter, exit = screenExit) { ... }
 * ```
 *
 * ponytail: no separate `popEnter`/`popExit`. The source idiom declared four values where the pop
 * pair was a character-for-character copy of the push pair — a back navigation that animates
 * identically to a forward one is just the same two values used twice. Add a distinct pop pair when
 * a screen actually wants directional motion.
 */
val screenEnter: EnterTransition =
    fadeIn(tween(DesignTokens.Motion.MEDIUM_MS)) +
        scaleIn(initialScale = 0.95f, animationSpec = tween(DesignTokens.Motion.MEDIUM_MS))

/** Screen-level exit transition. Mirror of [screenEnter]. */
val screenExit: ExitTransition =
    fadeOut(tween(DesignTokens.Motion.MEDIUM_MS)) +
        scaleOut(targetScale = 0.95f, animationSpec = tween(DesignTokens.Motion.MEDIUM_MS))

/**
 * Grows this element while it holds focus, by [AdaptiveTokens.focusScale] — the "which card am I on"
 * affordance that a D-pad surface lives or dies by, since a television is read from three metres and
 * a subtle border change is invisible at that distance.
 *
 * A no-op on every non-TV surface, because `focusScale` is `1f` there: on a touch screen a growing
 * card is noise, not information. So this is safe to leave on a shared composable that renders on
 * both a phone and a TV — which is the entire point of putting it here rather than in a TV-only
 * module.
 *
 * Does **not** make the element focusable; chain it after your own `.focusable()` / `.clickable()`:
 * ```
 * Card(Modifier.focusScale().focusable().clickable { open(movie) })
 * ```
 *
 * ponytail: `graphicsLayer` takes the lambda overload so the scale is applied at draw time — the
 * animation never invalidates composition or layout, only the layer. That matters on a TV row where
 * a dozen cards animate as focus sweeps across them.
 */
@Composable
fun Modifier.focusScale(enabled: Boolean = true): Modifier {
    val target = LocalAdaptiveTokens.current.focusScale
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) target else 1f,
        animationSpec = tween(DesignTokens.Motion.FAST_MS),
        label = "focusScale",
    )
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
