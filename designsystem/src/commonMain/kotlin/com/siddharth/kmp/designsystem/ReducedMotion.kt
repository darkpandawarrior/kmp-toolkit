package com.siddharth.kmp.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether decorative motion should be suppressed. `false` unless a parent supplies it, so a consumer
 * that never opts in sees exactly the behaviour it had before this local existed.
 *
 * Opt in once, at the root, with the OS setting:
 * ```
 * ProvideReducedMotion { App() }          // follows the platform
 * ProvideReducedMotion(reduced = prefs.reducedMotion) { App() }   // or your own in-app toggle
 * ```
 * [AnimatedCounter], [RedactionReveal], [StepTimeline] and [SkeletonBlock] default their
 * `reducedMotion` parameter from this local; the explicit parameter still wins.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * The platform's "reduce motion" preference. Android: `ANIMATOR_DURATION_SCALE == 0` (the "remove
 * animations" developer/accessibility switch; observed live). iOS: `UIAccessibility.isReduceMotionEnabled`
 * (observed live). Web: `prefers-reduced-motion: reduce` (read once). Desktop: `false`.
 */
@Composable
expect fun systemReducedMotion(): Boolean

/** Provides [LocalReducedMotion] to [content]; [reduced] defaults to the OS setting. */
@Composable
fun ProvideReducedMotion(
    reduced: Boolean = systemReducedMotion(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalReducedMotion provides reduced, content = content)
}

/**
 * Reduced-motion-aware [screenEnter]: no transition at all when [reducedMotion] is set. Composable
 * so it can default from [LocalReducedMotion]; hoist the result above a non-composable
 * `NavHost { enterTransition = { ... } }` lambda.
 */
@Composable
fun screenEnter(reducedMotion: Boolean = LocalReducedMotion.current): EnterTransition =
    if (reducedMotion) EnterTransition.None else screenEnter

/** Reduced-motion-aware [screenExit]. See the [screenEnter] overload. */
@Composable
fun screenExit(reducedMotion: Boolean = LocalReducedMotion.current): ExitTransition = if (reducedMotion) ExitTransition.None else screenExit
