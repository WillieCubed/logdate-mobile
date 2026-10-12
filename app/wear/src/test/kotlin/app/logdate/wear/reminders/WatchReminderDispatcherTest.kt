package app.logdate.wear.reminders

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchReminderDispatcherTest {
    private val clock = FixedClock(berlin(10, 18))
    private val notifier = FakeNudgeNotifier()

    @Test
    fun failedAuthorizationKeepsTheWatchQuietWithoutCrashingTheCallback() =
        runTest {
            val dispatcher =
                WatchReminderDispatcher(
                    FakeReminderPreferences(),
                    notifier,
                    clock = clock,
                    timeZone = { REMINDER_ZONE },
                    authorize = { error("budget unreadable") },
                )
            assertFalse(dispatcher.onWorkoutEnded(berlin(10, 17, 50)))
            assertEquals(emptyList(), notifier.posted)
        }

    @Test
    fun callbackCancellationStillPropagates() =
        runTest {
            val dispatcher =
                WatchReminderDispatcher(
                    FakeReminderPreferences(),
                    notifier,
                    clock = clock,
                    timeZone = { REMINDER_ZONE },
                    authorize = { throw CancellationException("stop") },
                )
            assertFailsWith<CancellationException> { dispatcher.onWorkoutEnded(berlin(10, 17, 50)) }
            assertEquals(emptyList(), notifier.posted)
        }

    @Test
    fun authorizationRefusalStopsADueLocalReminder() =
        runTest {
            val preferences = FakeReminderPreferences()
            val dispatcher =
                WatchReminderDispatcher(
                    preferences,
                    notifier,
                    authorize = { false },
                    clock = clock,
                    timeZone = { REMINDER_ZONE },
                )
            assertFalse(dispatcher.onWorkoutEnded(berlin(10, 17, 50)))
            assertEquals(emptyList(), notifier.posted)
            assertNull(preferences.lastReminderAt())
        }

    @Test
    fun optOutWhileAuthorizingStopsTheLocalPost() =
        runTest {
            val preferences = FakeReminderPreferences()
            val dispatcher =
                WatchReminderDispatcher(preferences, notifier, authorize = {
                    preferences.setEnabled(false)
                    true
                }, clock = clock, timeZone = { REMINDER_ZONE })
            assertFalse(dispatcher.onWorkoutEnded(berlin(10, 17, 50)))
            assertEquals(emptyList(), notifier.posted)
        }

    @Test
    fun `reminders off means no reminder`() =
        runTest {
            val preferences = FakeReminderPreferences(enabled = false)

            assertFalse(dispatcher(preferences).onWorkoutEnded(berlin(10, 17, 50)))
            assertTrue(notifier.posted.isEmpty())
        }

    @Test
    fun `a due workout reminder is posted and its time saved`() =
        runTest {
            val preferences = FakeReminderPreferences()

            assertTrue(dispatcher(preferences).onWorkoutEnded(berlin(10, 17, 50)))

            assertEquals(listOf(WatchReminder.WORKOUT_ENDED), notifier.posted)
            assertEquals(clock.now, preferences.lastReminderAt())
        }

    @Test
    fun `a reminder the watch could not show is not counted`() =
        runTest {
            notifier.accepts = false
            val preferences = FakeReminderPreferences()

            assertFalse(dispatcher(preferences).onWorkoutEnded(berlin(10, 17, 50)))

            assertNull(preferences.lastReminderAt())
        }

    @Test
    fun `a second workout the same day is not reminded`() =
        runTest {
            val preferences = FakeReminderPreferences()
            val dispatcher = dispatcher(preferences)
            dispatcher.onWorkoutEnded(berlin(10, 17, 50))
            clock.now = berlin(10, 20)

            assertFalse(dispatcher.onWorkoutEnded(berlin(10, 19, 55)))
            assertEquals(1, notifier.posted.size)
        }

    @Test
    fun `an evening with no moments and no phone reminds`() =
        runTest {
            clock.now = berlin(10, 20, 30)

            assertTrue(dispatcher(FakeReminderPreferences()).onEveningCheck(phoneReachable = false, momentsToday = 0))

            assertEquals(listOf(WatchReminder.EVENING), notifier.posted)
        }

    @Test
    fun `an evening with the phone in reach is the phone's`() =
        runTest {
            clock.now = berlin(10, 20, 30)

            assertFalse(dispatcher(FakeReminderPreferences()).onEveningCheck(phoneReachable = true, momentsToday = 0))
        }

    @Test
    fun `an evening check with reminders off reminds of nothing`() =
        runTest {
            clock.now = berlin(10, 20, 30)

            assertFalse(dispatcher(FakeReminderPreferences(enabled = false)).onEveningCheck(phoneReachable = false, momentsToday = 0))
        }

    @Test
    fun `workout and evening reminders share one daily allowance`() =
        runTest {
            val preferences = FakeReminderPreferences()
            val dispatch = dispatcher(preferences)
            assertTrue(dispatch.onWorkoutEnded(berlin(10, 17, 50)))
            clock.now = berlin(10, 20, 30)
            assertFalse(dispatch.onEveningCheck(false, 0))
            assertEquals(1, notifier.posted.size)
        }

    @Test
    fun `an evening reminder also prevents a later workout nudge`() =
        runTest {
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
