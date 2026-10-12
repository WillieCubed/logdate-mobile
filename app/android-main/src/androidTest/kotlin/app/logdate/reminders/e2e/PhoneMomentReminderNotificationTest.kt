package app.logdate.reminders.e2e

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.MainActivity
import app.logdate.client.domain.reminders.ReminderTrigger
import app.logdate.client.reminders.PhoneMomentReminderNotifier
import app.logdate.client.resolveMainActivityNavKey
import app.logdate.feature.editor.navigation.EditorLaunchMode
import app.logdate.feature.editor.navigation.EntryEditorRoute
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PhoneMomentReminderNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val day = LocalDate(2026, 10, 12)
    private val notifier get() = KoinJavaComponent.getKoin().get<PhoneMomentReminderNotifier>()

    @Before
    fun prepare() {
        clear()
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun clear() {
        manager.cancel("logdate.reminder", 4005)
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (manager.activeNotifications.none { it.tag == "logdate.reminder" && it.id == 4005 }) return
            SystemClock.sleep(25)
        }
        error("The previous phone reminder was not removed")
    }

    @Test
    fun phoneReminderIncludesMarksAndBridgingIdentityForTheDay() {
        assertTrue(notifier.post(ReminderTrigger.CAMPFIRE_EMBERS, 3, day))
        val notification = posted()
        assertEquals("moment_reminders", notification.channelId)
        assertEquals("Keep your campfire going", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(
            notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString()
                .contains("3 marked moments to finish"),
        )
        val wearable = NotificationCompat.WearableExtender(notification)
        assertEquals("logdate.reminder", wearable.bridgeTag)
        assertEquals("logdate.reminder:2026-10-12", wearable.dismissalId)
        assertEquals(
            "Record",
            notification.actions
                .single()
                .title
                .toString(),
        )
    }

    @Test
    fun zeroMarksDoesNotInventUnfinishedMoments() {
        assertTrue(notifier.post(ReminderTrigger.ARRIVED_SOMEWHERE_NEW, 0, day))
        val notification = posted()
        assertEquals("Somewhere new", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Record a moment here", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun recordActionOpensTheInstalledVoiceEditorRoute() {
        assertTrue(notifier.post(ReminderTrigger.WORKOUT_ENDED, 0, day))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            posted()
                .actions
                .single()
                .actionIntent
                .send()
            val activity = assertNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 10_000))
            try {
                val route = assertIs<EntryEditorRoute>(resolveMainActivityNavKey(activity.intent))
                assertEquals(EditorLaunchMode.Voice, route.launchMode)
            } finally {
                activity.finish()
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun posted(): Notification {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            manager.activeNotifications.singleOrNull { it.tag == "logdate.reminder" && it.id == 4005 }?.let { return it.notification }
            SystemClock.sleep(25)
        }
        error("The phone reminder did not appear")
    }
}
