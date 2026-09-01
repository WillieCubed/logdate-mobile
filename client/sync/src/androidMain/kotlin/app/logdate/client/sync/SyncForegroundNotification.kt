package app.logdate.client.sync

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import app.logdate.client.notifications.LogDateNotificationChannelKey

/**
 * The notification that keeps a backup alive while the app is not on screen.
 *
 * A sync of several hundred entries takes minutes. Run as ordinary background work it was
 * cancelled as soon as the app left the foreground, part way through, with nothing to show for
 * it; a foreground service survives that. The notification is the price Android charges for it,
 * so it may as well say something true - how far along the backup is.
 */
internal object SyncForegroundNotification {
    private val channel = LogDateNotificationChannelKey.CLOUD_SYNC
    private val notificationId = channel.notificationId ?: 1004

    fun info(
        context: Context,
        text: String,
        completed: Int? = null,
        total: Int? = null,
    ): ForegroundInfo {
        val builder =
            NotificationCompat
                .Builder(context, channel.id)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(context.getString(R.string.sync_notification_title))
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(Notification.VISIBILITY_PRIVATE)

        if (total != null && total > 0 && completed != null) {
            builder.setProgress(total, completed, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return ForegroundInfo(
            notificationId,
            builder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }
}
