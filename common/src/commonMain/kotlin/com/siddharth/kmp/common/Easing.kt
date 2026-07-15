package com.siddharth.kmp.common

// Ported from Kursi core/designsystem/.../moment/Primitives.kt (originals `internal`) and
// re-confirmed as a duplicate in PaymentsLab core/designsystem/ShieldPulse.kt, whose own comment
// says "ported from Kursi's Primitives.kt" (Backlog #25). Pure Float math, zero Compose dependency
// on purpose — kept in :common (not :designsystem) so non-UI callers (e.g. game/sim tick easing)
// can use it without pulling in Compose.

/** Linear interpolation, [t] clamped to [0, 1]. */
fun lerp(
    a: Float,
    b: Float,
    t: Float,
): Float = a + (b - a) * t.coerceIn(0f, 1f)

/** EaseInQuart — sharp initial acceleration. */
fun easeInQuart(t: Float): Float = t * t * t * t

/** EaseOutCubic — smooth deceleration. */
fun easeOutCubic(t: Float): Float {
    val c = t - 1f
    return 1f + c * c * c
}

/** EaseOutBack — slight overshoot settle. s=1.70158 (same constant both origin apps used). */
fun easeOutBack(t: Float): Float {
    val s = 1.70158f
    val c = t - 1f
    return c * c * ((s + 1f) * c + s) + 1f
}
