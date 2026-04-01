package app.logdate.feature.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import app.logdate.client.feature.core.R

/**
 * Shared base for export and restore notification helpers.
 *
 * Captures the superset behavior: progress bar, immediate foreground
 * service display, silent notifications across all states, and a cancel
 * action during progress.
 */
abstract class DataTransferNotificationHelper(
    protected val context: Context,
    protected val workId: java.util.UUID,
) {
    protected abstract val channelId: String
    protected abstract val notificationId: Int

    @get:StringRes
    protected abstract val progressTitleResId: Int

    @get:StringRes
    protected abstract val completeTitleResId: Int

    @get:StringRes
    protected abstract val failedTitleResId: Int

    protected val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Builds a foreground-service notification with a progress bar and cancel action.
     *
     * @param progress Current progress percentage (0-100). When 0, the bar is indeterminate.
     * @param message  Localized status text shown below the title.
     */
    open fun createForegroundInfo(
        progress: Int,
        message: String,
    ): ForegroundInfo {
        val cancelIntent =
            WorkManager
                .getInstance(context)
                .createCancelPendingIntent(workId)

        val notification =
            NotificationCompat
                .Builder(context, channelId)
                .setContentTitle(context.getString(progressTitleResId))
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, progress == 0)
                .setOngoing(true)
                .setSilent(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(
                    android.R.drawable.ic_delete,
                    context.getString(R.string.notification_action_cancel),
                    cancelIntent,
                ).build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    /**
     * Builds a completion notification. Auto-cancels on tap.
     *
     * @param contentText Localized description of the completed operation.
     */
    open fun createCompletionInfo(contentText: String): ForegroundInfo {
        val notification =
            NotificationCompat
                .Builder(context, channelId)
                .setContentTitle(context.getString(completeTitleResId))
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setSilent(true)
                .build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    /**
     * Builds an error notification. Auto-cancels on tap, stays silent.
     */
    fun createErrorInfo(errorMessage: String): ForegroundInfo {
        val notification =
            NotificationCompat
                .Builder(context, channelId)
                .setContentTitle(context.getString(failedTitleResId))
                .setContentText(context.getString(R.string.error_with_message, errorMessage))
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .setSilent(true)
                .build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }
}
