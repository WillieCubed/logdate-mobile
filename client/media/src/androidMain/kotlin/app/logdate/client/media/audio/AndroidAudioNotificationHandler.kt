package app.logdate.client.media.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.logdate.client.media.R
import io.github.aakira.napier.Napier
import kotlin.time.Clock

/**
 * Handler for audio recording notifications on Android.
 *
 * Creates and manages foreground notification required for audio recording.
 */
class AndroidAudioNotificationHandler(
    private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "audio_recording_channel"
        const val NOTIFICATION_ID = 1001

        // Action constants for the notification buttons
        const val ACTION_PAUSE = "app.logdate.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "app.logdate.action.RESUME_RECORDING"
        const val ACTION_STOP = "app.logdate.action.STOP_RECORDING"
    }

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    init {
        createNotificationChannel()
    }

    /**
     * Creates notification channel for Android O+
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.audio_recording_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.audio_recording_notification_text)
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
        Napier.d("Created notification channel for audio recording")
    }

    /**
     * Creates a PendingIntent for notification actions
     */
    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            this.action = action
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getService(context, action.hashCode(), intent, flags)
    }

    /**
     * Creates a main activity PendingIntent to return to the editor
     */
    private fun createMainActivityIntent(): PendingIntent {
        val packageName = context.packageName
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getActivity(context, 0, launchIntent, flags)
    }

    /**
     * Creates a foreground notification for audio recording
     */
    fun createRecordingNotification(
        isRecording: Boolean = true,
        startTimeMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Notification {
        val titleRes = if (isRecording) {
            R.string.audio_recording_notification_title
        } else {
            R.string.audio_recording_notification_title_paused
        }
        val textRes = if (isRecording) {
            R.string.audio_recording_notification_text
        } else {
            R.string.audio_recording_notification_text_paused
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(textRes))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(startTimeMillis)
            .setContentIntent(createMainActivityIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)

        // Add action buttons based on current state
        if (isRecording) {
            // Add pause button when recording
            builder.addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.audio_recording_action_pause),
                createActionIntent(ACTION_PAUSE)
            )
        } else {
            // Add resume button when paused
            builder.addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.audio_recording_action_resume),
                createActionIntent(ACTION_RESUME)
            )
        }

        // Always add stop button
        builder.addAction(
            android.R.drawable.ic_delete,  // Using an alternative icon as a fallback
            context.getString(R.string.audio_recording_action_stop),
            createActionIntent(ACTION_STOP)
        )

        return builder.build()
    }

    /**
     * Shows the recording notification
     */
    fun showRecordingNotification() {
        try {
            val notification = createRecordingNotification()
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Napier.e("Failed to show recording notification", e)
        }
    }

    /**
     * Dismisses the recording notification
     */
    fun dismissRecordingNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Napier.e("Failed to dismiss recording notification", e)
        }
    }

    /**
     * Updates an existing notification with new content
     */
    fun updateNotification(notification: Notification) {
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Napier.e("Failed to update recording notification", e)
        }
    }

    /**
     * Updates notification to reflect current recording state
     */
    fun updateRecordingNotification(isRecording: Boolean, startTimeMillis: Long) {
        try {
            val notification = createRecordingNotification(isRecording, startTimeMillis)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Napier.e("Failed to update recording notification", e)
        }
    }
}
