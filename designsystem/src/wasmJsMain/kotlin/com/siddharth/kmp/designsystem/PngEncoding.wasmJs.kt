package com.siddharth.kmp.designsystem

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** Skiko actual: Compose's backing Skia bitmap encoded straight to PNG. */
actual fun ImageBitmap.toPngBytes(): ByteArray? =
    runCatching {
        Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
    }.getOrNull()
