package com.siddharth.kmp.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Easing as ComposeEasing

/**
 * Material 3 motion tokens held as plain numbers, so the toolkit does not depend on the alpha
 * `MaterialTheme.motionScheme` API. Values are copied from androidx `MotionTokens.kt` (v0_103) and
 * `StandardMotionTokens.kt`; spec: https://m3.material.io/styles/motion/easing-and-duration/tokens-specs
 *
 * Distinct from [DesignTokens.Motion], which is the dashboard's own quick scale; use these when a
 * surface should match the M3 spec exactly.
 */
object MotionTokens {
    /** M3 duration tokens in milliseconds (`md.sys.motion.duration.*`). */
    object Duration {
        const val SHORT1 = 50
        const val SHORT2 = 100
        const val SHORT3 = 150
        const val SHORT4 = 200
        const val MEDIUM1 = 250
        const val MEDIUM2 = 300
        const val MEDIUM3 = 350
        const val MEDIUM4 = 400
        const val LONG1 = 450
        const val LONG2 = 500
        const val LONG3 = 550
        const val LONG4 = 600
        const val EXTRA_LONG1 = 700
        const val EXTRA_LONG2 = 800
        const val EXTRA_LONG3 = 900
        const val EXTRA_LONG4 = 1000
    }

    /** M3 easing curves (`md.sys.motion.easing.*`) as cubic-bezier control points. */
    object Easing {
        val Emphasized: ComposeEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val EmphasizedDecelerate: ComposeEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        val EmphasizedAccelerate: ComposeEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
        val Standard: ComposeEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val StandardDecelerate: ComposeEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
        val StandardAccelerate: ComposeEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
        val Legacy: ComposeEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    }

    /** M3 standard-scheme spring parameters (damping ratio, stiffness). */
    object Spring {
        const val SPATIAL_DEFAULT_DAMPING = 0.9f
        const val SPATIAL_DEFAULT_STIFFNESS = 700f
        const val SPATIAL_FAST_DAMPING = 0.9f
        const val SPATIAL_FAST_STIFFNESS = 1400f
        const val SPATIAL_SLOW_DAMPING = 0.9f
        const val SPATIAL_SLOW_STIFFNESS = 300f
        const val EFFECTS_DEFAULT_DAMPING = 1f
        const val EFFECTS_DEFAULT_STIFFNESS = 1600f
        const val EFFECTS_FAST_DAMPING = 1f
        const val EFFECTS_FAST_STIFFNESS = 3800f
        const val EFFECTS_SLOW_DAMPING = 1f
        const val EFFECTS_SLOW_STIFFNESS = 800f
    }
}

/**
 * A tween over [duration] ms with [easing], or [snap] when [reducedMotion] is set. Defaults to the
 * M3 medium2 duration and the emphasized curve. Non-composable form for use outside composition
 * and in tests; see the composable overload to default from [LocalReducedMotion].
 */
fun <T> motionTween(
    reducedMotion: Boolean,
    duration: Int = MotionTokens.Duration.MEDIUM2,
    easing: ComposeEasing = MotionTokens.Easing.Emphasized,
): FiniteAnimationSpec<T> = if (reducedMotion) snap() else tween(duration, easing = easing)

/** [motionTween] that follows [LocalReducedMotion]. */
@Composable
fun <T> motionTween(
    duration: Int = MotionTokens.Duration.MEDIUM2,
    easing: ComposeEasing = MotionTokens.Easing.Emphasized,
): FiniteAnimationSpec<T> = motionTween(LocalReducedMotion.current, duration, easing)

/** M3 spatial spring (movement, size); [snap] under reduced motion. Defaults to the default preset. */
fun <T> motionSpatialSpring(
    reducedMotion: Boolean,
    dampingRatio: Float = MotionTokens.Spring.SPATIAL_DEFAULT_DAMPING,
    stiffness: Float = MotionTokens.Spring.SPATIAL_DEFAULT_STIFFNESS,
): FiniteAnimationSpec<T> = if (reducedMotion) snap() else spring(dampingRatio, stiffness)

/** M3 effects spring (colour, alpha); [snap] under reduced motion. Critically damped. */
fun <T> motionEffectsSpring(
    reducedMotion: Boolean,
    dampingRatio: Float = MotionTokens.Spring.EFFECTS_DEFAULT_DAMPING,
    stiffness: Float = MotionTokens.Spring.EFFECTS_DEFAULT_STIFFNESS,
): FiniteAnimationSpec<T> = if (reducedMotion) snap() else spring(dampingRatio, stiffness)

/** [motionSpatialSpring] that follows [LocalReducedMotion]. */
@Composable
fun <T> motionSpatialSpring(
    dampingRatio: Float = MotionTokens.Spring.SPATIAL_DEFAULT_DAMPING,
    stiffness: Float = MotionTokens.Spring.SPATIAL_DEFAULT_STIFFNESS,
): FiniteAnimationSpec<T> = motionSpatialSpring(LocalReducedMotion.current, dampingRatio, stiffness)

/** [motionEffectsSpring] that follows [LocalReducedMotion]. */
@Composable
fun <T> motionEffectsSpring(
    dampingRatio: Float = MotionTokens.Spring.EFFECTS_DEFAULT_DAMPING,
    stiffness: Float = MotionTokens.Spring.EFFECTS_DEFAULT_STIFFNESS,
): FiniteAnimationSpec<T> = motionEffectsSpring(LocalReducedMotion.current, dampingRatio, stiffness)
