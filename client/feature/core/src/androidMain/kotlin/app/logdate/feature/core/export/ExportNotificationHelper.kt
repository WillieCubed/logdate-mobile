package app.logdate.feature.core.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager

/**
 * Helper class for managing export notifications in WorkManager.
 */
class ExportNotificationHelper(
    private val context: Context,
    private val workId: java.util.UUID
) {
    
    companion object {
        private const val CHANNEL_ID = "export_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannel()
    }
    
    fun createForegroundInfo(progress: Int, message: String): ForegroundInfo {
        // Create cancel action as recommended by the docs
        val cancelIntent = WorkManager.getInstance(context)
            .createCancelPendingIntent(workId)
            
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Exporting Data")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
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
    
    fun createCompletionInfo(filePath: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Export Complete")
            .setContentText("Data exported to: $filePath")
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
            .setContentTitle("Export Failed")
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
            "Data Export",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Exporting user data to file"
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }
}