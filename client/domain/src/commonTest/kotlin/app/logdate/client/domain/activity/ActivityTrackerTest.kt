package app.logdate.client.domain.activity

import app.logdate.client.sync.peer.state.ActivitySnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityTrackerTest {
    private val start = Instant.fromEpochSeconds(1_760_000_000)
    private val store = MemoryActivityLogStore()
    private val peer = MutableStateFlow<ActivitySnapshot?>(null)
    private val watchWorn = MutableStateFlow(true)
    private val enabled = MutableStateFlow(true)

    @Test
    fun `a walk ends on its own once its grace period passes`() =
        runTest {
            val tracker = tracker(isPhone = true)
            tracker.start(backgroundScope)
            tracker.accept(ActivityTransition(ActivityKind.MOVING, entered = true, at = now()))
            advanceTimeBy(40.minutes)
            tracker.accept(ActivityTransition(ActivityKind.MOVING, entered = false, at = now()))
            assertEquals(
                ActivityKind.MOVING,
                tracker.agreed
                    .first()
                    .current
                    ?.kind,
            )

            advanceTimeBy(3.minutes)
            runCurrent()

            val agreed = tracker.agreed.first()
            assertNull(agreed.current)
            assertEquals(Activity(ActivityKind.MOVING, start, start + 40.minutes), agreed.lastEnded)
            assertEquals(agreed.lastEnded, store.saved?.lastEnded)
        }

    @Test
    fun `a new tracker picks up the activity under way`() =
        runTest {
            tracker(isPhone = true).accept(ActivityTransition(ActivityKind.IN_VEHICLE, entered = true, at = now()))

            val restarted = tracker(isPhone = true)

            assertEquals(Activity(ActivityKind.IN_VEHICLE, start, endedAt = null), restarted.agreed.first().current)
        }

    @Test
    fun `an activity whose grace period passed while the app was stopped reads as ended`() =
        runTest {
            store.saved = ActivityLog(open = OpenActivity(ActivityKind.MOVING, start - 1.hours, leftAt = start - 20.minutes))

            val agreed = tracker(isPhone = true).agreed.first()

            assertNull(agreed.current)
            assertEquals(Activity(ActivityKind.MOVING, start - 1.hours, start - 20.minutes), agreed.lastEnded)
        }

    @Test
    fun `nothing is reported while activity awareness is off`() =
        runTest {
            enabled.value = false
            val tracker = tracker(isPhone = true)
            tracker.accept(ActivityTransition(ActivityKind.IN_VEHICLE, entered = true, at = now()))

            assertEquals(AgreedActivity(), tracker.agreed.first())
            assertEquals(
                ActivityKind.IN_VEHICLE,
                tracker.local.value.open
                    ?.kind,
            )
        }

    @Test
    fun `the phone acts on the sleep the worn watch reports`() =
        runTest {
            peer.value = ActivitySnapshot(kind = "SLEEP", startedAt = (start - 6.hours).toEpochMilliseconds())
            val tracker = tracker(isPhone = true)

            assertEquals(
                ActivityKind.SLEEP,
                tracker.agreed
                    .first()
                    .current
                    ?.kind,
            )
            watchWorn.value = false
            assertNull(tracker.agreed.first().current)
        }

    @Test
    fun `the watch acts on the drive the phone reports`() =
        runTest {
            peer.value = ActivitySnapshot(kind = "IN_VEHICLE", startedAt = (start - 10.minutes).toEpochMilliseconds())
            val tracker = tracker(isPhone = false)
            tracker.accept(ActivityTransition(ActivityKind.MOVING, entered = true, at = now()))

            assertEquals(
                ActivityKind.IN_VEHICLE,
                tracker.agreed
                    .first()
                    .current
                    ?.kind,
            )
        }

    @Test
    fun `a peer activity from a device that went quiet long ago is ignored`() =
        runTest {
            peer.value = ActivitySnapshot(kind = "IN_VEHICLE", startedAt = (start - 13.hours).toEpochMilliseconds())

            assertNull(tracker(isPhone = false).agreed.first().current)
        }

    @Test
    fun `the later of the two devices' endings is the one acted on`() =
        runTest {
            peer.value =
                ActivitySnapshot(
                    lastEndedKind = "MOVING",
                    lastStartedAt = (start - 2.hours).toEpochMilliseconds(),
                    lastEndedAt = (start - 1.hours).toEpochMilliseconds(),
                )
            store.saved = ActivityLog(lastEnded = Activity(ActivityKind.IN_VEHICLE, start - 50.minutes, start - 10.minutes))

            assertEquals(
                ActivityKind.IN_VEHICLE,
                tracker(isPhone = true)
                    .agreed
                    .first()
                    .lastEnded
                    ?.kind,
            )
        }

    private fun TestScope.now(): Instant = start + testScheduler.currentTime.milliseconds

    private fun TestScope.tracker(isPhone: Boolean) =
        ActivityTracker(
            store = store,
            peer = peer,
            watchWorn = watchWorn,
            isPhone = isPhone,
            enabled = enabled,
            clock =
                object : Clock {
                    override fun now(): Instant = this@tracker.now()
                },
        )

    private class MemoryActivityLogStore : ActivityLogStore {
        var saved: ActivityLog? = null

        override suspend fun read(): ActivityLog? = saved

        override suspend fun write(log: ActivityLog) {
            saved = log
        }
    }
}
