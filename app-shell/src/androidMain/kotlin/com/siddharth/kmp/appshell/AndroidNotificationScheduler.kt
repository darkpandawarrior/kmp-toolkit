package com.siddharth.kmp.appshell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Android [NotificationScheduler] backed by [NotificationManagerCompat].
 *
 * Posts to a single configurable channel — [channelId]/[channelName]/[importance] — created on
 * first use if missing. An app with its own multi-channel model (urgent/tracking/general, …) should
 * ensure those channels itself and pass its "general"-equivalent id here; this class does not assume
 * or manage any channel other than the one it's given.
 *
 * [ensurePermission] only *checks* whether notifications are enabled (POST_NOTIFICATIONS on API 33+);
 * actually requesting the runtime permission is [PermissionsProvider]'s job, since it needs an Activity.
 */
class AndroidNotificationScheduler(
    private val context: Context,
    private val channelId: String = DEFAULT_CHANNEL_ID,
    channelName: String = "General",
    importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
) : NotificationScheduler {
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager != null && manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(NotificationChannel(channelId, channelName, importance))
            }
        }
    }

    override suspend fun ensurePermission(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun notify(
        id: Int,
        title: String,
        body: String,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notification =
            NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications not granted; silently no-op.
        }
    }

    override fun cancel(id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    companion object {
        const val DEFAULT_CHANNEL_ID = "app_shell_general"
    }
}
