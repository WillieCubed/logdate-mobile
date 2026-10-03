package app.logdate.client.e2e

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.client.sync.AndroidWatchNoteNotificationPoster
import app.logdate.client.sync.WatchNoteArrivalContent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import app.logdate.client.notifications.R as NotificationResources

/**
 * A note recorded on the watch must not come back to the watch as a notification about itself,
 * and tapping it on the phone opens the note in the editor.
 */
@RunWith(AndroidJUnit4::class)
class WatchNoteArrivalNotificationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val noteId = Uuid.random()

    @Before
    fun allowNotifications() {
        LogDateNotificationRegistrar(context).registerAllPhoneChannels()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun dismiss() {
        notificationManager.cancel(AndroidWatchNoteNotificationPoster.notificationId(noteId))
    }

    @Test
    fun `a watch note notification stays on the phone and opens the editor`() {
        AndroidWatchNoteNotificationPoster(context).post(
            noteId,
            WatchNoteArrivalContent(title = "Voice note from your watch", body = "Tap to open it in the editor"),
        )

        val posted = postedNotification()
        assertEquals(LogDateNotificationChannelKey.WATCH_SYNC.id, posted.channelId)
        assertTrue(posted.flags and Notification.FLAG_LOCAL_ONLY != 0, "The watch must not be alerted about its own note")
        assertEquals(NotificationResources.drawable.ic_notification_watch, posted.smallIcon.resId)
        assertEquals("Voice note from your watch", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNotNull(posted.contentIntent, "Tapping the notification opens the note in the editor")
    }

    private fun postedNotification(): Notification {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            notificationManager.activeNotifications
                .firstOrNull { it.id == AndroidWatchNoteNotificationPoster.notificationId(noteId) }
                ?.let { return it.notification }
            SystemClock.sleep(100)
        }
        error("The watch note notification was never posted")
    }
}
