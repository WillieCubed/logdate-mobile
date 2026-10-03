package app.logdate.wear.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.github.aakira.napier.Napier

/**
 * Removes what the fixed morning and evening journal prompts left on a watch.
 *
 * Builds with the prompts scheduled two repeating alarms. Alarms survive app updates, so without
 * this they would keep waking the watch at 8 AM and 9 PM to deliver a broadcast to a receiver that
 * no longer exists. Those builds also created the "Journal reminders" notification channel and
 * saved the two prompt switches.
 *
 * Each step finds nothing to remove once it has run, so calling this on every start is cheap and
 * safe. Delete this object once no installed build can still hold the alarms.
 */
object LegacyPromptAlarms {
    /** The receiver the old alarms target. The class no longer exists, so it is named by string. */
    private const val RECEIVER_CLASS_NAME = "app.logdate.wear.notification.WearPromptReceiver"

    /** The request codes of the old morning (8 AM) and evening (9 PM) alarms. */
    private val REQUEST_CODES = listOf(5001, 5002)

    private const val CHANNEL_ID = "wear_journal_prompts"

    /** The preferences file that held only the morning and evening prompt switches. */
    private const val SETTINGS_FILE = "wear_settings"

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        REQUEST_CODES.forEach { requestCode ->
            val alarm = scheduledAlarm(context, requestCode) ?: return@forEach
            alarmManager.cancel(alarm)
            alarm.cancel()
            Napier.i("Cancelled the legacy journal prompt alarm $requestCode")
        }
        NotificationManagerCompat.from(context).deleteNotificationChannel(CHANNEL_ID)
        context.deleteSharedPreferences(SETTINGS_FILE)
    }

    /**
     * The registered PendingIntent for [requestCode], or null when there is none. The system matches
     * a PendingIntent on its request code, its intent and its FLAG_IMMUTABLE bit, so all three are
     * rebuilt the way the old scheduler built them.
     */
    private fun scheduledAlarm(
        context: Context,
        requestCode: Int,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent().setClassName(context, RECEIVER_CLASS_NAME),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
}
