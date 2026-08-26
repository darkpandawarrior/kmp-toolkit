package com.siddharth.kmp.feedback

import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Authority the host app must declare for [shareImage] to work. See [canShareImage]. */
private const val SHARE_AUTHORITY_SUFFIX = ".kmpshare"

/**
 * Android actual: writes the PNG into the app's cache and shares it as a `content://` stream.
 *
 * The FileProvider is declared by the HOST APP, not by this module. AGP's Kotlin-Multiplatform
 * library plugin merges an androidMain manifest but does not package `androidMain/res`, so a
 * provider declared here fails at link time with `resource xml/... not found`. Shipping the
 * declaration from the app is also the conventional wiring, and it keeps the app in control of
 * which directories it exposes. [canShareImage] reports whether the app actually did it.
 *
 * Required in the app's manifest:
 * ```xml
 * <provider android:name="androidx.core.content.FileProvider"
 *     android:authorities="${applicationId}.kmpshare"
 *     android:exported="false" android:grantUriPermissions="true">
 *     <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
 *         android:resource="@xml/kmp_share_paths" />
 * </provider>
 * ```
 * with `res/xml/kmp_share_paths.xml` granting only `<cache-path name="kmp-share" path="kmp-share/" />`.
 *
 * Never throws. Every failure here is a share action, whose caller's fallback is to share text.
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
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + SHARE_AUTHORITY_SUFFIX, out)

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

/**
 * True only when a Context has been installed AND the host app actually declares the FileProvider.
 *
 * The provider is looked up rather than assumed. Without it `getUriForFile` throws, the share
 * silently does nothing, and the caller has already skipped its text fallback believing the image
 * path would work — the exact failure this function exists to prevent.
 */
actual fun canShareImage(): Boolean {
    val ctx = FeedbackAndroid.appContext ?: return false
    val authority = ctx.packageName + SHARE_AUTHORITY_SUFFIX
    return ctx.packageManager.resolveContentProvider(authority, 0) != null
}
