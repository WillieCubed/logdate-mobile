package app.logdate.wear.reminders

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchReminderDispatcherTest {
    private val clock = FixedClock(berlin(10, 18))
    private val notifier = FakeNudgeNotifier()

    @Test
    fun `reminders off means no reminder`() {
        val preferences = FakeReminderPreferences(enabled = false)

        assertFalse(dispatcher(preferences).onWorkoutEnded(berlin(10, 17, 50)))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a due workout reminder is posted and its time saved`() {
        val preferences = FakeReminderPreferences()

        assertTrue(dispatcher(preferences).onWorkoutEnded(berlin(10, 17, 50)))

        assertEquals(listOf(WatchReminder.WORKOUT_ENDED), notifier.posted)
        assertEquals(clock.now, preferences.lastReminderAt())
    }

    @Test
    fun `a reminder the watch could not show is not counted`() {
        notifier.accepts = false
        val preferences = FakeReminderPreferences()

        assertFalse(dispatcher(preferences).onWorkoutEnded(berlin(10, 17, 50)))

        assertNull(preferences.lastReminderAt())
    }

    @Test
    fun `a second workout the same day is not reminded`() {
        val preferences = FakeReminderPreferences()
        val dispatcher = dispatcher(preferences)
        dispatcher.onWorkoutEnded(berlin(10, 17, 50))
        clock.now = berlin(10, 20)

        assertFalse(dispatcher.onWorkoutEnded(berlin(10, 19, 55)))
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `an evening with no moments and no phone reminds`() {
        clock.now = berlin(10, 20, 30)

        assertTrue(dispatcher(FakeReminderPreferences()).onEveningCheck(phoneReachable = false, momentsToday = 0))

        assertEquals(listOf(WatchReminder.EVENING), notifier.posted)
    }

    @Test
    fun `an evening with the phone in reach is the phone's`() {
        clock.now = berlin(10, 20, 30)

        assertFalse(dispatcher(FakeReminderPreferences()).onEveningCheck(phoneReachable = true, momentsToday = 0))
    }

    @Test
    fun `an evening check with reminders off reminds of nothing`() {
        clock.now = berlin(10, 20, 30)

        assertFalse(dispatcher(FakeReminderPreferences(enabled = false)).onEveningCheck(phoneReachable = false, momentsToday = 0))
    }

    @Test
    fun `workout and evening reminders share one daily allowance`() {
        val preferences = FakeReminderPreferences()
        val dispatch = dispatcher(preferences)
        assertTrue(dispatch.onWorkoutEnded(berlin(10, 17, 50)))
        clock.now = berlin(10, 20, 30)
        assertFalse(dispatch.onEveningCheck(false, 0))
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `an evening reminder also prevents a later workout nudge`() {
        clock.now = berlin(10, 20, 30)
        val dispatch = dispatcher(FakeReminderPreferences())
        assertTrue(dispatch.onEveningCheck(false, 0))
        clock.now = berlin(10, 21)
        assertFalse(dispatch.onWorkoutEnded(berlin(10, 20, 55)))
        assertEquals(1, notifier.posted.size)
    }

    private fun dispatcher(preferences: WatchReminderPreferences) =
        WatchReminderDispatcher(preferences = preferences, notifier = notifier, clock = clock, timeZone = { REMINDER_ZONE })
}
