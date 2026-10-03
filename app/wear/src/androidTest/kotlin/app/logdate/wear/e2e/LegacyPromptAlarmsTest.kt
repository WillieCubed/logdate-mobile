package app.logdate.wear.e2e

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.wear.reminders.LegacyPromptAlarms
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recreates what builds with the fixed morning and evening prompts left on a watch, then checks
 * that the cleanup removes it: two repeating alarms, their notification channel and their switches.
 *
 * The alarms are built here the way those builds built them, independently of the cleanup, so a
 * wrong request code, receiver name or flag in the cleanup fails these tests.
 */
@RunWith(AndroidJUnit4::class)
class LegacyPromptAlarmsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @After
    fun tearDown() {
        OLD_REQUEST_CODES.forEach { requestCode ->
            val alarm = oldPromptAlarm(requestCode, PendingIntent.FLAG_NO_CREATE) ?: return@forEach
            alarmManager.cancel(alarm)
            alarm.cancel()
        }
    }

    @Test
    fun the_old_morning_and_evening_alarms_are_cancelled() {
        OLD_REQUEST_CODES.forEach { requestCode ->
            val alarm = checkNotNull(oldPromptAlarm(requestCode, PendingIntent.FLAG_UPDATE_CURRENT))
            alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + AlarmManager.INTERVAL_DAY,
                AlarmManager.INTERVAL_DAY,
                alarm,
            )
        }

        LegacyPromptAlarms.cancel(context)

        OLD_REQUEST_CODES.forEach { requestCode ->
            assertNull(
                "The alarm with request code $requestCode should be gone",
                oldPromptAlarm(requestCode, PendingIntent.FLAG_NO_CREATE),
            )
        }
    }

    @Test
    fun cancelling_when_nothing_is_left_is_harmless() {
        LegacyPromptAlarms.cancel(context)
        LegacyPromptAlarms.cancel(context)
    }

    @Test
    fun the_old_journal_reminders_channel_is_deleted() {
        val notifications = NotificationManagerCompat.from(context)
        notifications.createNotificationChannel(
            NotificationChannelCompat
                .Builder(OLD_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Journal reminders")
                .build(),
        )
        assertNotNull(notifications.getNotificationChannel(OLD_CHANNEL_ID))

        LegacyPromptAlarms.cancel(context)

        assertNull(notifications.getNotificationChannel(OLD_CHANNEL_ID))
    }

    @Test
    fun the_old_prompt_switches_are_forgotten() {
        context
            .getSharedPreferences(OLD_SETTINGS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("morning_prompt", false)
            .putBoolean("evening_prompt", false)
            .commit()

        LegacyPromptAlarms.cancel(context)

        val settings = context.getSharedPreferences(OLD_SETTINGS_FILE, Context.MODE_PRIVATE)
        assertFalse(settings.contains("morning_prompt"))
        assertFalse(settings.contains("evening_prompt"))
    }

    /** The PendingIntent the old prompt scheduler used for [requestCode], looked up with [flag]. */
    private fun oldPromptAlarm(
        requestCode: Int,
        flag: Int,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent().setClassName(context, OLD_RECEIVER),
            flag or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val OLD_RECEIVER = "app.logdate.wear.notification.WearPromptReceiver"
        val OLD_REQUEST_CODES = listOf(5001, 5002)
        const val OLD_CHANNEL_ID = "wear_journal_prompts"
        const val OLD_SETTINGS_FILE = "wear_settings"
    }
}
