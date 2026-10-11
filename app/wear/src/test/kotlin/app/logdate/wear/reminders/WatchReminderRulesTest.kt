package app.logdate.wear.reminders

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchReminderRulesTest {
    @Test
    fun `a workout that ended within 30 minutes is due`() {
        assertTrue(
            WatchReminderRules.workoutReminderDue(
                now = berlin(10, 18),
                endedAt = berlin(10, 17, 40),
                lastReminderAt = null,
                timeZone = REMINDER_ZONE,
            ),
        )
    }

    @Test
    fun `a workout that ended over 30 minutes ago is not`() {
        assertFalse(
            WatchReminderRules.workoutReminderDue(
                now = berlin(10, 18),
                endedAt = berlin(10, 17, 20),
                lastReminderAt = null,
                timeZone = REMINDER_ZONE,
            ),
        )
    }

    @Test
    fun `a workout ending after now is not due`() {
        assertFalse(
            WatchReminderRules.workoutReminderDue(
                now = berlin(10, 18),
                endedAt = berlin(10, 18, 5),
                lastReminderAt = null,
                timeZone = REMINDER_ZONE,
            ),
        )
    }

    @Test
    fun `reminders wait for waking hours`() {
        assertFalse(WatchReminderRules.isWaking(berlin(10, 7, 29), REMINDER_ZONE))
        assertTrue(WatchReminderRules.isWaking(berlin(10, 7, 30), REMINDER_ZONE))
        assertTrue(WatchReminderRules.isWaking(berlin(10, 22, 29), REMINDER_ZONE))
        assertFalse(WatchReminderRules.isWaking(berlin(10, 22, 30), REMINDER_ZONE))
    }

    @Test
    fun `one reminder a streak day`() {
        assertTrue(WatchReminderRules.remindedToday(now = berlin(10, 18), lastReminderAt = berlin(10, 9), timeZone = REMINDER_ZONE))
        assertFalse(WatchReminderRules.remindedToday(now = berlin(10, 18), lastReminderAt = berlin(9, 21), timeZone = REMINDER_ZONE))
        assertFalse(WatchReminderRules.remindedToday(now = berlin(10, 18), lastReminderAt = berlin(10, 2), timeZone = REMINDER_ZONE))
    }

    @Test
    fun `a reminder already today stops a workout reminder`() {
        assertFalse(
            WatchReminderRules.workoutReminderDue(
                now = berlin(10, 18),
                endedAt = berlin(10, 17, 50),
                lastReminderAt = berlin(10, 9),
                timeZone = REMINDER_ZONE,
            ),
        )
    }

    @Test
    fun `exactly thirty minutes still qualifies`() {
        assertTrue(WatchReminderRules.workoutReminderDue(berlin(10, 18), berlin(10, 17, 30), null, REMINDER_ZONE))
    }

    @Test
    fun `streak day resets at four even before waking hours`() {
        assertTrue(WatchReminderRules.remindedToday(berlin(10, 3, 59), berlin(9, 21), REMINDER_ZONE))
        assertFalse(WatchReminderRules.remindedToday(berlin(10, 4), berlin(9, 21), REMINDER_ZONE))
    }

    @Test
    fun `an evening with no moments and the phone away is due`() {
        assertTrue(
            WatchReminderRules.eveningReminderDue(
                berlin(10, 20, 30),
                lastReminderAt = null,
                momentsToday = 0,
                phoneReachable = false,
                timeZone = REMINDER_ZONE,
            ),
        )
    }

    @Test
    fun `an evening with the phone in reach or a moment today is not`() {
        assertFalse(
            WatchReminderRules.eveningReminderDue(
                berlin(10, 20, 30),
                lastReminderAt = null,
                momentsToday = 0,
                phoneReachable = true,
                timeZone = REMINDER_ZONE,
            ),
        )
        assertFalse(
            WatchReminderRules.eveningReminderDue(
                berlin(10, 20, 30),
                lastReminderAt = null,
                momentsToday = 1,
                phoneReachable = false,
                timeZone = REMINDER_ZONE,
            ),
        )
        assertFalse(
            WatchReminderRules.eveningReminderDue(
                berlin(10, 20, 30),
                lastReminderAt = berlin(10, 9),
                momentsToday = 0,
                phoneReachable = false,
                timeZone = REMINDER_ZONE,
            ),
        )
    }

    @Test
    fun `before the evening or after waking hours no evening reminder is due`() {
        for (hour in listOf(8, 20, 23)) {
            assertFalse(WatchReminderRules.eveningReminderDue(berlin(10, hour), null, 0, false, REMINDER_ZONE))
        }
    }
}
