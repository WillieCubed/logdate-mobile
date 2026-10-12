package app.logdate.wear.reminders

import app.logdate.client.domain.reminders.ReminderTrigger
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchReminderMappingTest {
    @Test
    fun delegatedTriggerKeepsItsMeaning() {
        val cases =
            mapOf(
                ReminderTrigger.WORKOUT_ENDED to WatchReminder.WORKOUT_ENDED,
                ReminderTrigger.ARRIVED_SOMEWHERE_NEW to WatchReminder.ARRIVED_SOMEWHERE_NEW,
                ReminderTrigger.CAMPFIRE_EMBERS to WatchReminder.CAMPFIRE_EMBERS,
                ReminderTrigger.WIND_DOWN to WatchReminder.WIND_DOWN,
            )
        cases.forEach { (trigger, expected) -> assertEquals(expected, reminderForTrigger(trigger)) }
    }
}
