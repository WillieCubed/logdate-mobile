package app.logdate.feature.core.restore

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager

/**
 * Helper class for managing restore notifications in WorkManager.
 */
class RestoreNotificationHelper(
    private val context: Context,
    private val workId: java.util.UUID
) {

    companion object {
        private const val CHANNEL_ID = "restore_channel"
        private const val NOTIFICATION_ID = 1002
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    fun createForegroundInfo(message: String): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(context)
            .createCancelPendingIntent(workId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Restoring Data")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    fun createCompletionInfo(message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Restore Complete")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setSilent(true)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    fun createErrorInfo(errorMessage: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Restore Failed")
            .setContentText("Error: $errorMessage")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Data Restore",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Restoring user data from backup"
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
