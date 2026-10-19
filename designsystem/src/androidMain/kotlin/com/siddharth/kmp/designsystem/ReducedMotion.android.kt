package com.siddharth.kmp.designsystem

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * `ANIMATOR_DURATION_SCALE == 0` ("Remove animations"), observed live. ponytail: Android has no
 * dedicated reduce-motion flag below the newest releases, and this scale is the switch TalkBack and
 * Settings > Accessibility > "Remove animations" both write, so it is the portable proxy.
 */
@Composable
actual fun systemReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember(context) { mutableStateOf(animationsRemoved(context)) }
    DisposableEffect(context) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced = animationsRemoved(context)
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun animationsRemoved(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
