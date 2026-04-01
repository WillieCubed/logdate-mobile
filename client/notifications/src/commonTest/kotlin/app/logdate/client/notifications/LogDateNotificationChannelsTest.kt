package app.logdate.client.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class LogDateNotificationChannelsTest {
    @Test
    fun phoneChannelIdsRemainUnique() {
        val channelIds = LogDateNotificationChannels.phoneChannels.map { it.id }

        assertEquals(channelIds.size, channelIds.distinct().size)
    }

    @Test
    fun notificationIdsRemainUniqueWhenPresent() {
        val notificationIds =
            LogDateNotificationChannels.phoneChannels
                .mapNotNull { it.notificationId }

        assertEquals(notificationIds.size, notificationIds.distinct().size)
    }

    @Test
    fun legacyChannelIdsDoNotOverlapActiveIds() {
        val activeIds = LogDateNotificationChannels.phoneChannels.map { it.id }.toSet()

        assertEquals(
            emptySet(),
            LogDateNotificationChannels.legacyChannelIds.intersect(activeIds),
        )
    }
}
