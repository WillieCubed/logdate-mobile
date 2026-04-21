package app.logdate.client.e2e

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.notifications.AndroidLogDateNotificationCatalog
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith

/**
 * Verifies the registration and management of Android notification channels.
 *
 * This suite ensures that the [LogDateNotificationRegistrar] correctly maps the
 * application's notification catalog to the system [NotificationManager],
 * including the creation of channel groups and the cleanup of legacy channels.
 */
@RunWith(AndroidJUnit4::class)
class LogDateNotificationRegistrarTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val notificationManager =
        context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun registerAllPhoneChannelsCreatesExpectedGroupsAndChannels() {
        LogDateNotificationRegistrar(context).registerAllPhoneChannels()

        val groupsById = notificationManager.notificationChannelGroups.associateBy { it.id }

        AndroidLogDateNotificationCatalog.groups.forEach { group ->
            assertNotNull(groupsById[group.key.id], "Missing notification group ${group.key.id}")
        }

        AndroidLogDateNotificationCatalog.phoneChannels.forEach { spec ->
            val channel = notificationManager.getNotificationChannel(spec.key.id)
            assertNotNull(channel, "Missing notification channel ${spec.key.id}")
            assertEquals(spec.importance, channel.importance)
            assertEquals(spec.key.groupKey.id, channel.group)
        }
    }

    @Test
    fun registerAllPhoneChannelsDeletesLegacyLocationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "logdate_location_detail_tracking",
                "Legacy location history",
                NotificationManager.IMPORTANCE_MIN,
            ),
        )

        LogDateNotificationRegistrar(context).registerAllPhoneChannels()

        assertNull(notificationManager.getNotificationChannel("logdate_location_detail_tracking"))
        assertNotNull(notificationManager.getNotificationChannel(LogDateNotificationChannelKey.LOCATION_HISTORY.id))
    }
}
