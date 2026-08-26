package com.siddharth.kmp.feedback

import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android actual: writes the PNG into the app's cache and shares it as a `content://` stream.
 *
 * The FileProvider is declared by THIS module's manifest (authority `${applicationId}.kmpshare`)
 * and merged into the host app, so a consumer gets image sharing without editing its own manifest.
 * That is the whole reason the authority is not the conventional `.fileprovider` — colliding with
 * an authority the app already declares would fail the manifest merge at build time.
 *
 * Never throws. Every failure here is a share action, where the caller's fallback is to share text.
 */
actual fun shareImage(
    pngBytes: ByteArray,
    fileName: String,
    text: String,
) {
    val ctx = FeedbackAndroid.appContext ?: return
    runCatching {
        val safeName = fileName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifEmpty { "share" }
        val dir = File(ctx.cacheDir, "kmp-share").apply { mkdirs() }
        val out = File(dir, "$safeName.png").apply { writeBytes(pngBytes) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.kmpshare", out)

        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        ctx.startActivity(
            Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }
}

actual fun canShareImage(): Boolean = FeedbackAndroid.appContext != null
