package com.siddharth.kmp.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.siddharth.kmp.common.AppLog

private const val TAG = "SecureScreen"

/**
 * Wraps [content] and, while [enabled], marks the host Activity's window with
 * [WindowManager.LayoutParams.FLAG_SECURE].
 *
 * `FLAG_SECURE` is exactly what banking and payment apps set on card-entry, OTP, and balance
 * screens: it blocks screenshots, blocks screen recording / casting, and blanks the window in the
 * recents (task-switcher) thumbnail — so a saved card number or CVV can't leak through a
 * screenshot, a screen-share, or the last-app preview.
 *
 * The flag is set on enter and cleared on dispose via [DisposableEffect], so navigating away from a
 * sensitive screen re-enables screenshots for the non-sensitive parts of the app. It resolves the
 * Activity by walking the [ContextWrapper] chain from [LocalView]; if no Activity is found (e.g. a
 * preview or a non-Activity host) it is a no-op and still renders [content].
 */
@Composable
fun SecureScreen(
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current

    DisposableEffect(enabled) {
        val activity = view.context.findActivity()
        if (enabled && activity != null) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
            AppLog.d("FLAG_SECURE applied to ${activity.javaClass.simpleName}", tag = TAG)
        } else if (enabled) {
            AppLog.w("SecureScreen enabled but no host Activity found; screenshots NOT blocked", tag = TAG)
        }

        onDispose {
            if (enabled && activity != null) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                AppLog.d("FLAG_SECURE cleared from ${activity.javaClass.simpleName}", tag = TAG)
            }
        }
    }

    content()
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
