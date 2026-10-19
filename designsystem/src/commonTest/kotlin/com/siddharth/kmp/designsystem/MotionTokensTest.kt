package com.siddharth.kmp.designsystem

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorizedAnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Float.Companion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MotionTokensTest {
    @Test
    fun durationsMatchTheM3Spec() {
        val d = MotionTokens.Duration
        assertEquals(listOf(50, 100, 150, 200), listOf(d.SHORT1, d.SHORT2, d.SHORT3, d.SHORT4))
        assertEquals(listOf(250, 300, 350, 400), listOf(d.MEDIUM1, d.MEDIUM2, d.MEDIUM3, d.MEDIUM4))
        assertEquals(listOf(450, 500, 550, 600), listOf(d.LONG1, d.LONG2, d.LONG3, d.LONG4))
        assertEquals(
            listOf(700, 800, 900, 1000),
            listOf(d.EXTRA_LONG1, d.EXTRA_LONG2, d.EXTRA_LONG3, d.EXTRA_LONG4),
        )
    }

    @Test
    fun easingCurvesHitTheirEndpointsAndDiffer() {
        val all =
            with(MotionTokens.Easing) {
                listOf(Emphasized, EmphasizedDecelerate, EmphasizedAccelerate, Standard, StandardDecelerate, StandardAccelerate, Legacy)
            }
        all.forEach {
            assertEquals(0f, it.transform(0f), 1e-4f)
            assertEquals(1f, it.transform(1f), 1e-4f)
        }
        // Decelerate is front-loaded, accelerate back-loaded: pins the control points apart.
        assertTrue(MotionTokens.Easing.EmphasizedDecelerate.transform(0.5f) > 0.5f)
        assertTrue(MotionTokens.Easing.EmphasizedAccelerate.transform(0.5f) < 0.5f)
    }

    @Test
    fun reducedMotionCollapsesToSnap() {
        assertIs<SnapSpec<Float>>(motionTween<Float>(reducedMotion = true))
        assertIs<SnapSpec<Float>>(motionSpatialSpring<Float>(reducedMotion = true))
        assertIs<SnapSpec<Float>>(motionEffectsSpring<Float>(reducedMotion = true))
    }

    @Test
    fun normalMotionKeepsTheRequestedDuration() {
        val spec = motionTween<Float>(reducedMotion = false, duration = MotionTokens.Duration.LONG2)
        assertIs<TweenSpec<Float>>(spec)
        assertEquals(500, spec.durationMillis)
    }
}
