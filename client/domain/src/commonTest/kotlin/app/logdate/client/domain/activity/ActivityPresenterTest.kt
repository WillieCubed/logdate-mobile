package app.logdate.client.domain.activity

import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityPresenterTest {
    private val start = Instant.fromEpochSeconds(1_760_000_000)
    private val agreed = MutableStateFlow(AgreedActivity())
    private val lastCapture = MutableStateFlow<Instant?>(null)
    private val storage = FakeKeyValueStorage()

    @Test
    fun `a walk that just ended is offered once with its length`() =
        runTest {
            agreed.value = AgreedActivity(lastEnded = Activity(ActivityKind.MOVING, start - 45.minutes, start - 5.minutes))
            val presenter = presenter()

            val state = presenter.state.first()

            assertEquals(ActivitySuggestion(agreed.value.lastEnded!!, minutes = 40), state.suggestion)
            assertTrue(outcomes().outcomes.first().any { it.result == SuggestionResult.SHOWN })
        }

    @Test
    fun `a dismissed or used suggestion is not offered again`() =
        runTest {
            agreed.value = AgreedActivity(lastEnded = Activity(ActivityKind.IN_VEHICLE, start - 35.minutes, start - 5.minutes))
            val presenter = presenter()
            presenter.state.first()

            presenter.dismiss()

            assertNull(presenter.state.first().suggestion)
        }

    @Test
    fun `a moment added since the activity ended takes the suggestion away`() =
        runTest {
            agreed.value = AgreedActivity(lastEnded = Activity(ActivityKind.MOVING, start - 45.minutes, start - 5.minutes))
            lastCapture.value = start - 1.minutes

            assertNull(presenter().state.first().suggestion)
        }

    @Test
    fun `a suggestion goes away two hours after the activity or when another starts`() =
        runTest {
            val walk = Activity(ActivityKind.MOVING, start - 3.hours, start - 2.hours - 1.minutes)
            agreed.value = AgreedActivity(lastEnded = walk)
            assertNull(presenter().state.first().suggestion)

            val recent = walk.copy(startedAt = start - 45.minutes, endedAt = start - 5.minutes)
            agreed.value = AgreedActivity(current = Activity(ActivityKind.IN_VEHICLE, start - 1.minutes, null), lastEnded = recent)
            assertNull(presenter().state.first().suggestion)
        }

    @Test
    fun `an activity too short to count is never offered`() =
        runTest {
            agreed.value = AgreedActivity(lastEnded = Activity(ActivityKind.MOVING, start - 12.minutes, start - 5.minutes))

            assertNull(presenter().state.first().suggestion)
        }

    @Test
    fun `moving puts voice first`() =
        runTest {
            agreed.value = AgreedActivity(current = Activity(ActivityKind.MOVING, start - 3.minutes, null))

            assertTrue(presenter().state.first().voiceFirst)
            agreed.value = AgreedActivity(current = Activity(ActivityKind.IN_VEHICLE, start - 3.minutes, null))
            assertFalse(presenter().state.first().voiceFirst)
        }

    @Test
    fun `waking up shows the morning start until the first capture`() =
        runTest {
            val night = Activity(ActivityKind.SLEEP, start - 8.hours, start - 10.minutes)
            agreed.value = AgreedActivity(lastEnded = night)
            assertEquals(night, presenter().state.first().morningStart)

            lastCapture.value = start - 2.minutes
            assertNull(presenter().state.first().morningStart)
        }

    @Test
    fun `sleep never becomes an after-activity suggestion`() =
        runTest {
            agreed.value = AgreedActivity(lastEnded = Activity(ActivityKind.SLEEP, start - 8.hours, start - 10.minutes))

            assertNull(presenter().state.first().suggestion)
        }

    private fun TestScope.now(): Instant = start + testScheduler.currentTime.milliseconds

    private fun TestScope.outcomes() =
        SuggestionOutcomes(
            storage,
            object : Clock {
                override fun now(): Instant = this@outcomes.now()
            },
        )

    private fun TestScope.presenter() =
        ActivityPresenter(
            agreed = agreed,
            outcomes = outcomes(),
            capturedSince = { since -> lastCapture.map { it != null && it >= since } },
            clock =
                object : Clock {
                    override fun now(): Instant = this@presenter.now()
                },
            ticks = flowOf(Unit),
        )
}
