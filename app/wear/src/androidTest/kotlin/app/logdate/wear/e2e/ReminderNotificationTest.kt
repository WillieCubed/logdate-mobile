package app.logdate.wear.e2e

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.wear.launch.WearLaunchIntents
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.reminders.WatchNudgeNotifier
import app.logdate.wear.reminders.WatchReminder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Posts a real reminder on a Wear OS emulator and follows its Record action into the app. */
@RunWith(AndroidJUnit4::class)
class ReminderNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun grantNotifications() {
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun clear() {
        notificationManager.cancel(WatchNudgeNotifier.TAG, WatchNudgeNotifier.NOTIFICATION_ID)
    }

    @Test
    fun a_reminder_appears_on_the_reminders_channel_with_record() {
        assertTrue(WatchNudgeNotifier(context).post(WatchReminder.WORKOUT_ENDED))

        val notification = posted().notification
        assertEquals(LogDateNotificationChannelKey.MOMENT_REMINDERS.id, notification.channelId)
        assertEquals("Workout finished", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(
            "Record",
            notification.actions
                .single()
                .title
                .toString(),
        )
    }

    @Test
    fun the_evening_reminder_has_its_own_copy_and_record_action() {
        assertTrue(WatchNudgeNotifier(context).post(WatchReminder.EVENING))
        val notification = posted().notification
        assertEquals("Nothing recorded today", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Record a moment before the day ends", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(
            "Record",
            notification.actions
                .single()
                .title
                .toString(),
        )
    }

    @Test
    fun record_opens_the_app_on_the_recorder_route() {
        WatchNudgeNotifier(context).post(WatchReminder.WORKOUT_ENDED)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)

        posted()
            .notification.actions
            .single()
            .actionIntent
            .send()
        val activity = instrumentation.waitForMonitorWithTimeout(monitor, TIMEOUT_MS)

        assertNotNull(activity)
        assertEquals("record", activity.intent.getStringExtra(WearLaunchRequest.EXTRA_ROUTE))
        activity.finish()
        instrumentation.removeMonitor(monitor)
    }

    @Test
    fun moment_launches_keep_distinct_identity_even_when_uuid_hashes_collide() {
        val first = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val second = Uuid.parse("00000001-0000-0000-0000-000000000000")
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(WearLaunchIntents.moment(context, first), WearLaunchIntents.moment(context, second))
    }

    private fun posted(): StatusBarNotification {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            notificationManager.activeNotifications
                .singleOrNull { it.tag == WatchNudgeNotifier.TAG && it.id == WatchNudgeNotifier.NOTIFICATION_ID }
                ?.let { return it }
            SystemClock.sleep(25)
        }
        error("The watch reminder did not appear within $TIMEOUT_MS ms")
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
