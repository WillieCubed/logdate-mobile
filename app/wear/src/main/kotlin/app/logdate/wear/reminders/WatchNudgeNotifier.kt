package app.logdate.wear.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.logdate.client.domain.reminders.ReminderTrigger
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.wear.R
import app.logdate.wear.launch.WearLaunchIntents
import io.github.aakira.napier.Napier

/** Shows a reminder; false when it could not be shown. */
fun interface NudgeNotifier {
    fun post(reminder: WatchReminder): Boolean
}

/**
 * Posts reminders on the watch's own [CHANNEL], with a Record action that opens the app to record. There is
 * one reminder at a time: a new one replaces the last.
 */
class WatchNudgeNotifier(
    private val context: Context,
) : NudgeNotifier {
    fun post(
        trigger: ReminderTrigger,
        openMarks: Int,
    ): Boolean = post(reminderForTrigger(trigger), openMarks)

    override fun post(reminder: WatchReminder): Boolean = post(reminder, 0)

    fun post(
        reminder: WatchReminder,
        openMarks: Int,
    ): Boolean {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Napier.w("Notifications are off for LogDate on this watch, so the ${reminder.name} reminder was not shown")
            return false
        }
        return postGranted(reminder, openMarks)
    }

    private fun postGranted(
        reminder: WatchReminder,
        openMarks: Int,
    ): Boolean {
        LogDateNotificationRegistrar(context).registerChannel(CHANNEL)
        val manager = NotificationManagerCompat.from(context)
        val channel = manager.getNotificationChannel(CHANNEL.id)
        if (!manager.areNotificationsEnabled() || channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val body =
            buildString {
                append(context.getString(reminder.bodyRes))
                if (openMarks > 0) {
                    append("\n")
                    append(context.resources.getQuantityString(R.plurals.wear_reminder_open_marks, openMarks, openMarks))
                }
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL.id)
                .setSmallIcon(R.drawable.ic_glyph_record)
                .setContentTitle(context.getString(reminder.titleRes))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(WearLaunchIntents.home(context))
                .addAction(R.drawable.ic_glyph_record, context.getString(R.string.wear_reminder_record), WearLaunchIntents.record(context))
                .build()
        return try {
            manager.notify(TAG, NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            Napier.w("Notification permission changed before the watch reminder could be shown", e)
            false
        }
    }

    companion object {
        /** The tag reminders use on both devices; the watch excludes the phone's from bridging. */
        const val TAG = "logdate.reminder"
        val CHANNEL = LogDateNotificationChannelKey.MOMENT_REMINDERS
        val NOTIFICATION_ID: Int = requireNotNull(CHANNEL.notificationId)
    }
}
