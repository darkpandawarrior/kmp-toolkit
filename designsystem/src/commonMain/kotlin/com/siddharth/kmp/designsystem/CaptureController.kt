package com.siddharth.kmp.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize

/**
 * Capture a composable subtree as an [ImageBitmap], on every Compose Multiplatform target.
 *
 * This is Compose's own graphics-layer API ([rememberGraphicsLayer] / [GraphicsLayer.record] /
 * [GraphicsLayer.toImageBitmap]) wrapped in the two things a caller actually wants: a handle to
 * trigger the capture from outside the draw phase, and a modifier to mark what gets captured.
 *
 * Deliberately NOT a dependency on `dev.shreyaspatil:capturable`. That library is built on this
 * same API and handles the same cases, but it is Android-only, and this toolkit ships to Android,
 * iOS, desktop and wasm from one source set. Fifteen lines here beat an Android-only artifact.
 *
 * ```
 * val controller = rememberCaptureController()
 * Card(Modifier.capturable(controller)) { ReferralSummary(state) }
 * Button(onClick = { scope.launch { onShare(controller.captureAsImageBitmap()) } }) { ... }
 * ```
 */
@Stable
class CaptureController internal constructor(
    internal val layer: GraphicsLayer,
) {
    /**
     * Set from the draw phase, read from a coroutine. Intentionally a plain `var` and not Compose
     * state: writing snapshot state during draw invalidates the frame that is being drawn, which
     * is an endless recomposition loop. Nothing reads this during composition.
     */
    internal var hasRecorded: Boolean = false

    /**
     * The content as it was last drawn. Suspends because reading pixels back off the GPU is
     * asynchronous on every platform.
     *
     * Throws if called before the composable marked with [capturable] has drawn at least once —
     * `toImageBitmap()` on an unrecorded layer is undefined, so failing loudly beats a blank image.
     */
    suspend fun captureAsImageBitmap(): ImageBitmap {
        check(hasRecorded) {
            "capture() was called before the capturable content drew. Trigger it from a click or " +
                "a LaunchedEffect that runs after the first frame, not during composition."
        }
        return layer.toImageBitmap()
    }
}

/** Remembers a [CaptureController] bound to a graphics layer scoped to this composition. */
@Composable
fun rememberCaptureController(): CaptureController {
    val layer = rememberGraphicsLayer()
    return remember(layer) { CaptureController(layer) }
}

/**
 * Routes this subtree's drawing through [controller]'s layer, then draws the layer as normal, so
 * the content renders exactly as it would have and is simultaneously available to capture.
 */
fun Modifier.capturable(controller: CaptureController): Modifier =
    drawWithContent {
        controller.layer.record(
            density = this,
            layoutDirection = layoutDirection,
            size = IntSize(size.width.toInt(), size.height.toInt()),
        ) {
            this@drawWithContent.drawContent()
        }
        controller.hasRecorded = true
        drawLayer(controller.layer)
    }
