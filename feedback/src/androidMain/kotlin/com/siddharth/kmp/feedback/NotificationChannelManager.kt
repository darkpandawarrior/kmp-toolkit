package com.siddharth.kmp.feedback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * A single notification channel the host app wants created. Kept app-agnostic — the app declares its
 * own channels (ids, names, importance) and passes them to [NotificationChannelManager.createChannels];
 * this module owns none of them.
 *
 * [importance] is an `android.app.NotificationManager.IMPORTANCE_*` constant.
 */
data class NotificationChannelSpec(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
    val vibrate: Boolean = false,
)

/**
 * Registers the host app's notification channels. Idempotent — `createNotificationChannels` upserts by
 * id, so calling it again on every launch (the recommended pattern) is safe.
 */
object NotificationChannelManager {
    fun createChannels(
        context: Context,
        channels: List<NotificationChannelSpec>,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannels(
            channels.map { spec ->
                NotificationChannel(spec.id, spec.name, spec.importance).apply {
                    description = spec.description
                    if (spec.vibrate) enableVibration(true)
                }
            },
        )
    }
}
