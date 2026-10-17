package app.logdate.client.domain.activity

import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SuggestionOutcomesTest {
    private var now = Instant.fromEpochSeconds(1_760_000_000)
    private val storage = FakeKeyValueStorage()
    private val clock =
        object : Clock {
            override fun now(): Instant = this@SuggestionOutcomesTest.now
        }
    private val walk = Activity(ActivityKind.MOVING, now - 50.minutes, now - 10.minutes)

    @Test
    fun `each outcome is remembered for its activity`() =
        runTest {
            val outcomes = SuggestionOutcomes(storage, clock)

            outcomes.record(SuggestionKind.AFTER_ACTIVITY, walk, SuggestionResult.SHOWN)
            outcomes.record(SuggestionKind.AFTER_ACTIVITY, walk, SuggestionResult.DISMISSED)

            assertEquals(
                listOf(SuggestionResult.SHOWN, SuggestionResult.DISMISSED),
                outcomes.outcomes
                    .first()
                    .filter { it.isFor(SuggestionKind.AFTER_ACTIVITY, walk) }
                    .map { it.result },
            )
        }

    @Test
    fun `outcomes survive a new instance`() =
        runTest {
            SuggestionOutcomes(storage, clock).record(SuggestionKind.MORNING_START, walk, SuggestionResult.ACTED_ON)

            assertEquals(
                SuggestionResult.ACTED_ON,
                SuggestionOutcomes(storage, clock)
                    .outcomes
                    .first()
                    .single()
                    .result,
            )
        }

    @Test
    fun `outcomes older than 30 days are dropped when the next one is written`() =
        runTest {
            val outcomes = SuggestionOutcomes(storage, clock)
            outcomes.record(SuggestionKind.AFTER_ACTIVITY, walk, SuggestionResult.SHOWN)

            now += 31.days
            val later = Activity(ActivityKind.IN_VEHICLE, now - 30.minutes, now - 5.minutes)
            outcomes.record(SuggestionKind.AFTER_ACTIVITY, later, SuggestionResult.SHOWN)

            assertEquals(listOf(later.startedAt), outcomes.outcomes.first().map { it.activityStartedAt })
        }

    @Test
    fun `unreadable saved outcomes start over`() =
        runTest {
            storage.putString(SuggestionOutcomes.KEY, "not json")

            assertEquals(emptyList(), SuggestionOutcomes(storage, clock).outcomes.first())
        }
}
