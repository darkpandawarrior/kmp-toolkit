package com.siddharth.kmp.feedback

/**
 * Shares an image with accompanying [text] through the platform share sheet.
 *
 * Mirrors [shareText] deliberately, including its honesty about coverage: Android is implemented,
 * the other targets are no-ops for now, exactly as [shareText] already is. A caller that needs a
 * guaranteed share should call [shareText] when [shareImage] is unavailable rather than assume
 * this landed — see `canShareImage`.
 *
 * @param pngBytes the encoded image (see `ImageBitmap.toPngBytes()` in :designsystem).
 * @param fileName base name without extension; sanitised by the platform actual.
 */
expect fun shareImage(
    pngBytes: ByteArray,
    fileName: String,
    text: String,
)

/**
 * Whether [shareImage] does anything on this platform *right now*. Android reports false until the
 * host app has called `FeedbackAndroid.install(context)`, because the share needs a Context.
 *
 * Callers branch on this instead of calling [shareImage] hopefully and showing nothing: a share
 * button that silently does nothing is worse than one that shares text.
 */
expect fun canShareImage(): Boolean
