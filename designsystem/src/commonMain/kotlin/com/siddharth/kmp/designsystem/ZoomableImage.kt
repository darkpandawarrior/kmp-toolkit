package com.siddharth.kmp.designsystem

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

/**
 * A pinch/pan/double-tap zoomable image.
 *
 * The real consumer is a full-screen receipt viewer: the user has photographed a receipt and needs
 * to read the total. Nothing in the toolkit offered that gesture layer before.
 *
 * Wrapping it here rather than letting callers apply `Modifier.zoomable` themselves keeps
 * `net.engawapg` out of app code, so a breaking upstream minor changes this file and nothing else.
 *
 * No `expect`/`actual`: zoomable 2.13.0 publishes android, iosArm64, iosSimulatorArm64, jvm **and**
 * wasmJs, verified against the published Gradle module metadata. The plan this came from called the
 * wasm publication contested and budgeted a `Modifier` no-op fallback for it, which turned out to
 * be unnecessary.
 *
 * @param maxScale ceiling on zoom. The default of 5x is generous for a photographed document and
 *   still short of the point where a typical capture turns to mush.
 */
@Composable
fun ZoomableImage(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxScale: Float = 5f,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val zoomState = rememberZoomState(maxScale = maxScale)
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.zoomable(zoomState),
    )
}
