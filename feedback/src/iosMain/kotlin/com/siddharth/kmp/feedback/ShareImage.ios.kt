@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.siddharth.kmp.feedback

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create

/**
 * iOS actual: writes the PNG into the temporary directory and hands its file URL to the share sheet
 * alongside [text], mirroring what the Android actual does with a `content://` stream.
 *
 * A file URL rather than a `UIImage` so [fileName] survives into whatever the user shares to —
 * Mail attaches it by name, Files saves it by name. The temporary directory is the system's to
 * reclaim, so nothing here has to clean up after a share the app never learns the outcome of.
 *
 * Never throws. Every failure is a share action whose caller's fallback is to share text.
 */
actual fun shareImage(
    pngBytes: ByteArray,
    fileName: String,
    text: String,
) {
    // usePinned { addressOf(0) } is out of bounds on an empty array, so this guard is load-bearing.
    if (pngBytes.isEmpty()) return
    val safeName = fileName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifEmpty { "share" }
    val path = NSTemporaryDirectory() + "$safeName.png"
    val data =
        pngBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = pngBytes.size.toULong())
        }
    if (!data.writeToFile(path, atomically = true)) return
    presentActivitySheet(listOf(text, NSURL.fileURLWithPath(path)))
}

/**
 * True unconditionally. UIActivityViewController is part of UIKit — unlike Android, there is no
 * host-app wiring (no Context, no FileProvider declaration) that can be missing.
 */
actual fun canShareImage(): Boolean = true
