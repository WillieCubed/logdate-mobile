package app.logdate.client.domain.activity

import app.logdate.client.repository.journals.CaptureActivity
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CurrentCaptureActivityTest {
    private val now = Instant.fromEpochSeconds(1_760_000_000)
    private val clock =
        object : Clock {
            override fun now(): Instant = this@CurrentCaptureActivityTest.now
        }
    private val walk = Activity(ActivityKind.MOVING, now - 50.minutes, now - 10.minutes)
    private val trip = Activity(ActivityKind.IN_VEHICLE, now - 40.minutes, now - 5.minutes)

    private fun source(
        agreed: AgreedActivity,
        enabled: Boolean = true,
        outcomes: List<SuggestionOutcome> = emptyList(),
    ) = CurrentCaptureActivity(flowOf(agreed), flowOf(enabled), flowOf(outcomes), clock)

    private fun actedOn(
        activity: Activity,
        at: Instant,
        kind: SuggestionKind = SuggestionKind.AFTER_ACTIVITY,
    ) = SuggestionOutcome(kind, activity.kind, activity.startedAt, SuggestionResult.ACTED_ON, at)

    @Test
    fun `a capture during a walk or a trip records it`() =
        runTest {
            assertEquals(CaptureActivity.MOVING, source(AgreedActivity(current = walk.copy(endedAt = null))).now())
            assertEquals(CaptureActivity.IN_VEHICLE, source(AgreedActivity(current = trip.copy(endedAt = null))).now())
        }

    @Test
    fun `sleep is never recorded`() =
        runTest {
            assertNull(source(AgreedActivity(current = Activity(ActivityKind.SLEEP, now - 60.minutes, null))).now())
        }

    @Test
    fun `nothing is recorded while activity awareness is off`() =
        runTest {
            assertNull(source(AgreedActivity(current = walk.copy(endedAt = null)), enabled = false).now())
        }

    @Test
    fun `a capture started from the suggestion after a trip records the trip`() =
        runTest {
            val outcomes = listOf(actedOn(trip, at = now - 2.minutes))

            assertEquals(CaptureActivity.IN_VEHICLE, source(AgreedActivity(lastEnded = trip), outcomes = outcomes).now())
        }

    @Test
    fun `an old or dismissed suggestion records nothing`() =
        runTest {
            val old = listOf(actedOn(walk, at = now - 31.minutes))
            val dismissed =
                listOf(SuggestionOutcome(SuggestionKind.AFTER_ACTIVITY, walk.kind, walk.startedAt, SuggestionResult.DISMISSED, now))
            val morning =
                listOf(
                    actedOn(
                        Activity(ActivityKind.SLEEP, now - 480.minutes, now - 5.minutes),
                        at = now,
                        kind = SuggestionKind.MORNING_START,
                    ),
                )

            assertNull(source(AgreedActivity(lastEnded = walk), outcomes = old).now())
            assertNull(source(AgreedActivity(lastEnded = walk), outcomes = dismissed).now())
            assertNull(source(AgreedActivity(), outcomes = morning).now())
        }

    @Test
    fun `a capture does not wait for an activity that never arrives`() =
        runTest {
            val never = flow<AgreedActivity> { awaitCancellation() }

            assertNull(CurrentCaptureActivity(never, flowOf(true), clock = clock).now())
            assertNull(CurrentCaptureActivity(emptyFlow(), flowOf(true), clock = clock).now())
        }
}
