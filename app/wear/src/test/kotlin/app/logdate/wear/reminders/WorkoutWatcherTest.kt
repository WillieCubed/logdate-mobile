package app.logdate.wear.reminders

import androidx.health.services.client.data.UserActivityState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutWatcherTest {
    private val notifier = FakeNudgeNotifier()
    private val transitions = FakeTransitions()
    private val dispatcher =
        WatchReminderDispatcher(
            preferences = FakeReminderPreferences(),
            notifier = notifier,
            clock = FixedClock(berlin(10, 18)),
            timeZone = { REMINDER_ZONE },
        )
    private val watcher = WorkoutWatcher(transitions, dispatcher)

    @Test
    fun `an older passive event cannot finish a newer workout`() {
        watcher.onActivity(ActivityKind.EXERCISE, berlin(10, 17, 55))
        assertFalse(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))
        assertTrue(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 56)))
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `a listener left registered cannot act while the experience is off`() {
        val disabled = WorkoutWatcher(transitions, dispatcher, experienceEnabled = { false })
        assertFalse(disabled.onActivity(ActivityKind.EXERCISE, berlin(10, 17)))
        assertNull(transitions.last())
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `exercise then passive ends a workout at the change`() {
        assertEquals(berlin(10, 17, 50), workoutEndedAt(ActivityKind.EXERCISE, ActivityKind.PASSIVE, berlin(10, 17, 50)))
    }

    @Test
    fun `other changes end no workout`() {
        assertNull(workoutEndedAt(ActivityKind.PASSIVE, ActivityKind.EXERCISE, berlin(10, 17)))
        assertNull(workoutEndedAt(null, ActivityKind.PASSIVE, berlin(10, 17)))
        assertNull(workoutEndedAt(ActivityKind.EXERCISE, ActivityKind.ASLEEP, berlin(10, 17)))
    }

    @Test
    fun `the end of a workout reminds`() {
        assertFalse(watcher.onActivity(ActivityKind.EXERCISE, berlin(10, 17)))
        assertTrue(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))

        assertEquals(listOf(WatchReminder.WORKOUT_ENDED), notifier.posted)
    }

    @Test
    fun `an unknown reading between exercise and passive does not hide the end`() {
        watcher.onActivity(ActivityKind.EXERCISE, berlin(10, 17))
        watcher.onActivity(ActivityKind.UNKNOWN, berlin(10, 17, 30))

        assertTrue(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))
    }

    @Test
    fun `passive without exercise before it reminds of nothing`() {
        assertFalse(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `health services states map to kinds`() {
        assertEquals(ActivityKind.EXERCISE, UserActivityState.USER_ACTIVITY_EXERCISE.toActivityKind())
        assertEquals(ActivityKind.PASSIVE, UserActivityState.USER_ACTIVITY_PASSIVE.toActivityKind())
        assertEquals(ActivityKind.ASLEEP, UserActivityState.USER_ACTIVITY_ASLEEP.toActivityKind())
        assertEquals(ActivityKind.UNKNOWN, UserActivityState.USER_ACTIVITY_UNKNOWN.toActivityKind())
    }
}
