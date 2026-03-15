package app.logdate.wear.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import java.util.Calendar

/**
 * Schedules morning and evening journal prompt alarms via [AlarmManager].
 *
 * Morning prompt fires at 8:00 AM, evening prompt at 9:00 PM.
 * Alarms repeat daily using inexact repeating to minimize battery impact.
 */
class WearPromptScheduler(
    private val context: Context,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAll() {
        scheduleMorningPrompt()
        scheduleEveningPrompt()
        Napier.d("Journal prompt alarms scheduled")
    }

    fun cancelAll() {
        alarmManager.cancel(pendingIntent(REQUEST_MORNING))
        alarmManager.cancel(pendingIntent(REQUEST_EVENING))
        Napier.d("Journal prompt alarms cancelled")
    }

    private fun scheduleMorningPrompt() {
        val triggerTime = nextAlarmTime(hour = 8, minute = 0)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            triggerTime,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(REQUEST_MORNING),
        )
    }

    private fun scheduleEveningPrompt() {
        val triggerTime = nextAlarmTime(hour = 21, minute = 0)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            triggerTime,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(REQUEST_EVENING),
        )
    }

    private fun nextAlarmTime(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun pendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, WearPromptReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val REQUEST_MORNING = 5001
        private const val REQUEST_EVENING = 5002
    }
}
