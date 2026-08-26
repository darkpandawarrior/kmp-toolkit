package com.siddharth.kmp.feedback

/** Not implemented on this target yet, matching [shareText]. [canShareImage] reports it honestly. */
actual fun shareImage(
    pngBytes: ByteArray,
    fileName: String,
    text: String,
) = Unit

actual fun canShareImage(): Boolean = false
