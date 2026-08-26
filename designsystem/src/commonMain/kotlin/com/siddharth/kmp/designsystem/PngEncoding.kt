package com.siddharth.kmp.designsystem

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Encodes [ImageBitmap] as PNG bytes, or returns null when the platform cannot.
 *
 * Pairs with [CaptureController]: capture gives you pixels in memory, this gives you something you
 * can write to a file or hand to a share sheet. Returns null rather than throwing, because every
 * caller of this is a share or export action where the honest fallback is "share the text instead",
 * not a crash.
 */
expect fun ImageBitmap.toPngBytes(): ByteArray?
