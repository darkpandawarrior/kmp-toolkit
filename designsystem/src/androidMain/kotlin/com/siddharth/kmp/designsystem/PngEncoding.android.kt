package com.siddharth.kmp.designsystem

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

/** Android actual: the framework [Bitmap] behind the [ImageBitmap], compressed losslessly. */
actual fun ImageBitmap.toPngBytes(): ByteArray? =
    runCatching {
        ByteArrayOutputStream().use { out ->
            // quality is ignored for PNG (it is lossless) but the parameter is not optional.
            asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }.getOrNull()
