package app.logdate.client.rewind

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.logdate.client.MainActivity
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.shared.model.Rewind
import io.github.aakira.napier.Napier
import app.logdate.client.notifications.R as NotificationResources

/**
 * Posts a "your weekly Rewind is ready" notification with a deep link into the
 * detail screen for the freshly generated rewind.
 */
class RewindNotificationCoordinator(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun postRewindReady(rewind: Rewind): Boolean =
        runCatching {
            val channel = LogDateNotificationChannelKey.REWIND_READY
            val title = context.getString(NotificationResources.string.notification_rewind_ready_title)
            val body =
                if (rewind.title.isNotBlank()) {
                    context.getString(NotificationResources.string.notification_rewind_ready_body, rewind.title)
                } else {
                    context.getString(NotificationResources.string.notification_rewind_ready_body_generic)
                }

            val notification =
                NotificationCompat
                    .Builder(context, channel.id)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(buildPendingIntent(rewind))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                    .build()

            notificationManager.notify(channel.notificationId ?: rewind.uid.hashCode(), notification)
        }.onFailure { error ->
            Napier.w("Failed to post rewind-ready notification", error)
        }.isSuccess

    private fun buildPendingIntent(rewind: Rewind): PendingIntent {
        val launchIntent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_REWIND_NOTIFICATION_TARGET, REWIND_NOTIFICATION_TARGET_DETAIL)
                putExtra(EXTRA_REWIND_NOTIFICATION_ID, rewind.uid.toString())
            }

        return PendingIntent.getActivity(
            context,
            rewind.uid.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
