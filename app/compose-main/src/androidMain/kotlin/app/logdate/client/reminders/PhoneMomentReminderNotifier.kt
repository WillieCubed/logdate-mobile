package app.logdate.client.reminders

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.logdate.client.MainActivity
import app.logdate.client.capture.ACTION_CAPTURE
import app.logdate.client.capture.EXTRA_CAPTURE_MODE
import app.logdate.client.domain.reminders.ReminderTrigger
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDate
import studio.hypertext.logdate.R
import app.logdate.client.notifications.R as NotificationResources

/** Posts only; the delivery coordinator owns permission to consume the day's reminder. */
class PhoneMomentReminderNotifier(
    private val context: Context,
) {
    fun post(
        trigger: ReminderTrigger,
        openMarks: Int,
        day: LocalDate,
    ): Boolean {
        if (openMarks < 0) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        LogDateNotificationRegistrar(context).registerChannel(CHANNEL)
        if (manager.getNotificationChannel(CHANNEL.id)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val (title, message) = content(trigger)
        val body =
            buildString {
                append(context.getString(message))
                if (openMarks > 0) {
                    append("\n")
                    append(context.resources.getQuantityString(R.plurals.moment_reminder_open_marks, openMarks, openMarks))
                }
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL.id)
                .setSmallIcon(NotificationResources.drawable.ic_notification_capture)
                .setContentTitle(context.getString(title))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(capture(day, "text"))
                .addAction(
                    NotificationResources.drawable.ic_notification_capture,
                    context.getString(R.string.moment_reminder_record),
                    capture(day, "voice"),
                ).extend(NotificationCompat.WearableExtender().setBridgeTag(TAG).setDismissalId("$TAG:$day"))
                .build()
        return try {
            manager.notify(TAG, NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            Napier.w("Notification permission changed before the phone reminder could be posted", e)
            false
        }
    }

    private fun capture(
        day: LocalDate,
        mode: String,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(ACTION_CAPTURE)
                .setClass(context, MainActivity::class.java)
                .setData(Uri.parse("logdate://capture/reminder/$day/$mode"))
                .putExtra(EXTRA_CAPTURE_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun content(trigger: ReminderTrigger): Pair<Int, Int> =
        when (trigger) {
            ReminderTrigger.WORKOUT_ENDED -> R.string.moment_reminder_workout_title to R.string.moment_reminder_workout_body
            ReminderTrigger.ARRIVED_SOMEWHERE_NEW -> R.string.moment_reminder_arrival_title to R.string.moment_reminder_arrival_body
            ReminderTrigger.CAMPFIRE_EMBERS -> R.string.moment_reminder_campfire_title to R.string.moment_reminder_campfire_body
            ReminderTrigger.WIND_DOWN -> R.string.moment_reminder_wind_down_title to R.string.moment_reminder_wind_down_body
        }

    companion object {
        const val TAG = "logdate.reminder"
        val CHANNEL = LogDateNotificationChannelKey.MOMENT_REMINDERS
        val NOTIFICATION_ID: Int = requireNotNull(CHANNEL.notificationId)
    }
}
