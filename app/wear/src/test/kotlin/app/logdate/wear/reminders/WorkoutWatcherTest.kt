package app.logdate.wear.reminders

import androidx.health.services.client.data.UserActivityState
import app.logdate.client.domain.activity.Activity
import app.logdate.client.domain.activity.ActivityLog
import app.logdate.client.domain.activity.ActivityLogStore
import app.logdate.client.domain.activity.ActivityTracker
import app.logdate.client.domain.activity.ActivityTransition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.logdate.client.domain.activity.ActivityKind as AwarenessKind

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
    fun `cold workout callbacks wait for the saved flag and retain their order`() =
        runTest {
            var enabled = false
            val read = CompletableDeferred<Unit>()
            val waking = WorkoutWatcher(transitions, dispatcher, experienceEnabled = { enabled }, awaitExperience = { read.await() })
            val exercise = async(start = CoroutineStart.UNDISPATCHED) { waking.onActivityWhenReady(ActivityKind.EXERCISE, berlin(10, 17)) }
            val ended = async(start = CoroutineStart.UNDISPATCHED) { waking.onActivityWhenReady(ActivityKind.PASSIVE, berlin(10, 17, 50)) }
            assertFalse(exercise.isCompleted)
            assertFalse(ended.isCompleted)
            assertNull(transitions.last())
            enabled = true
            read.complete(Unit)
            assertFalse(exercise.await())
            assertTrue(ended.await())
            assertEquals(listOf(WatchReminder.WORKOUT_ENDED), notifier.posted)
        }

    @Test
    fun `an older passive event cannot finish a newer workout`() =
        runTest {
            watcher.onActivity(ActivityKind.EXERCISE, berlin(10, 17, 55))
            assertFalse(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))
            assertTrue(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 56)))
            assertEquals(1, notifier.posted.size)
        }

    @Test
    fun `a listener left registered cannot act while the experience is off`() =
        runTest {
            val disabled = WorkoutWatcher(transitions, dispatcher, experienceEnabled = { false })
            assertFalse(disabled.onActivity(ActivityKind.EXERCISE, berlin(10, 17)))
            assertNull(transitions.last())
            assertTrue(notifier.posted.isEmpty())
        }

    @Test
    fun `exercise then passive ends a workout at the change`() =
        runTest {
            assertEquals(berlin(10, 17, 50), workoutEndedAt(ActivityKind.EXERCISE, ActivityKind.PASSIVE, berlin(10, 17, 50)))
        }

    @Test
    fun `other changes end no workout`() =
        runTest {
            assertNull(workoutEndedAt(ActivityKind.PASSIVE, ActivityKind.EXERCISE, berlin(10, 17)))
            assertNull(workoutEndedAt(null, ActivityKind.PASSIVE, berlin(10, 17)))
            assertNull(workoutEndedAt(ActivityKind.EXERCISE, ActivityKind.ASLEEP, berlin(10, 17)))
        }

    @Test
    fun `the end of a workout reminds`() =
        runTest {
            assertFalse(watcher.onActivity(ActivityKind.EXERCISE, berlin(10, 17)))
            assertTrue(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))

            assertEquals(listOf(WatchReminder.WORKOUT_ENDED), notifier.posted)
        }

    @Test
    fun `an unknown reading between exercise and passive does not hide the end`() =
        runTest {
            watcher.onActivity(ActivityKind.EXERCISE, berlin(10, 17))
            watcher.onActivity(ActivityKind.UNKNOWN, berlin(10, 17, 30))

            assertTrue(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))
        }

    @Test
    fun `passive without exercise before it reminds of nothing`() =
        runTest {
            assertFalse(watcher.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))
            assertTrue(notifier.posted.isEmpty())
        }

    @Test
    fun `exercise and sleep become activity transitions`() {
        val at = berlin(10, 17)

        assertEquals(
            listOf(ActivityTransition(AwarenessKind.MOVING, entered = true, at = at)),
            awarenessTransitions(ActivityKind.PASSIVE, ActivityKind.EXERCISE, at),
        )
        assertEquals(
            listOf(
                ActivityTransition(AwarenessKind.MOVING, entered = false, at = at),
                ActivityTransition(AwarenessKind.SLEEP, entered = true, at = at),
            ),
            awarenessTransitions(ActivityKind.EXERCISE, ActivityKind.ASLEEP, at),
        )
        assertEquals(emptyList(), awarenessTransitions(ActivityKind.EXERCISE, ActivityKind.UNKNOWN, at))
        assertEquals(emptyList(), awarenessTransitions(null, ActivityKind.PASSIVE, at))
    }

    @Test
    fun `with activity awareness a workout feeds the tracker and ends in the activity reminder`() =
        runTest {
            val tracker = tracker()
            val aware = WorkoutWatcher(transitions, dispatcher, tracker = tracker, awarenessEnabled = { true })

            aware.onActivity(ActivityKind.EXERCISE, berlin(10, 17))
            assertTrue(aware.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))

            assertEquals(listOf(WatchReminder.MOVING_ENDED), notifier.posted)
            // The tracker's clock reads 18:00, past the end grace, so the walk has already ended there.
            assertEquals(Activity(AwarenessKind.MOVING, berlin(10, 17), berlin(10, 17, 50)), tracker.local.value.lastEnded)
        }

    @Test
    fun `with activity awareness a workout too short to count reminds of nothing`() =
        runTest {
            val aware = WorkoutWatcher(transitions, dispatcher, tracker = tracker(), awarenessEnabled = { true })

            aware.onActivity(ActivityKind.EXERCISE, berlin(10, 17, 45))

            assertFalse(aware.onActivity(ActivityKind.PASSIVE, berlin(10, 17, 50)))
            assertTrue(notifier.posted.isEmpty())
        }

    @Test
    fun `the evening reminder waits while the person is moving`() =
        runTest {
            val moving =
                WatchReminderDispatcher(
                    preferences = FakeReminderPreferences(),
                    notifier = notifier,
                    clock = FixedClock(berlin(10, 21)),
                    timeZone = { REMINDER_ZONE },
                    currentActivity = { Activity(AwarenessKind.MOVING, berlin(10, 20, 40), endedAt = null) },
                )

            assertFalse(moving.onEveningCheck(phoneReachable = false, momentsToday = 0))
            assertTrue(notifier.posted.isEmpty())
        }

    private fun tracker() =
        ActivityTracker(
            store =
                object : ActivityLogStore {
                    var saved: ActivityLog? = null

                    override suspend fun read() = saved

                    override suspend fun write(log: ActivityLog) {
                        saved = log
                    }
                },
            peer = flowOf(null),
            watchWorn = flowOf(true),
            isPhone = false,
            enabled = flowOf(true),
            clock = FixedClock(berlin(10, 18)),
        )

    @Test
    fun `health services states map to kinds`() =
        runTest {
            assertEquals(ActivityKind.EXERCISE, UserActivityState.USER_ACTIVITY_EXERCISE.toActivityKind())
            assertEquals(ActivityKind.PASSIVE, UserActivityState.USER_ACTIVITY_PASSIVE.toActivityKind())
            assertEquals(ActivityKind.ASLEEP, UserActivityState.USER_ACTIVITY_ASLEEP.toActivityKind())
            assertEquals(ActivityKind.UNKNOWN, UserActivityState.USER_ACTIVITY_UNKNOWN.toActivityKind())
        }
}
