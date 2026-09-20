package app.logdate.client.domain.streak

import app.logdate.client.health.util.LogdatePreferencesDataSource
import app.logdate.client.health.util.UserPreferences
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.streak.StreakSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveCampfireUseCaseTest {
    private val zone = TimeZone.UTC
    private val start = LocalDateTime(2026, 3, 15, 12, 0).toInstant(zone)

    private val timestamps = MutableStateFlow<List<Instant>>(emptyList())
    private val streakSettings = FakeStreakSettings()
    private var dayStartHour: Int? = null

    private fun TestScope.createUseCase(entryTimestamps: Flow<List<Instant>> = timestamps): ObserveCampfireUseCase {
        val scheduler = testScheduler
        val repository =
            object : JournalNotesRepository by FakeNotesRepository() {
                override fun observeEntryTimestamps(): Flow<List<Instant>> = entryTimestamps
            }
        val preferences =
            object : LogdatePreferencesDataSource {
                override suspend fun getPreferences() = UserPreferences(dayStartHour = dayStartHour)
            }
        val clock =
            object : Clock {
                override fun now(): Instant = start + scheduler.currentTime.milliseconds
            }
        return ObserveCampfireUseCase(
            notesRepository = repository,
            streakSettingsRepository = streakSettings,
            dayBoundaryPreferences = preferences,
            clock = clock,
            timeZone = { zone },
            computeDispatcher = UnconfinedTestDispatcher(scheduler),
        )
    }

    private fun TestScope.collectStates(useCase: ObserveCampfireUseCase): List<CampfireState?> {
        val states = mutableListOf<CampfireState?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase().toList(states)
        }
        runCurrent()
        return states
    }

    @Test
    fun `tracking turned off emits no fire`() =
        runTest {
            streakSettings.enabled.value = false
            val states = collectStates(createUseCase())

            assertNull(states.last())
        }

    @Test
    fun `turning tracking back on emits the fire`() =
        runTest {
            streakSettings.enabled.value = false
            val states = collectStates(createUseCase())

            streakSettings.enabled.value = true
            runCurrent()

            assertEquals(FirePhase.UNLIT, states.last()?.phase)
        }

    @Test
    fun `a newly saved entry lights the fire right away`() =
        runTest {
            val states = collectStates(createUseCase())
            assertEquals(FirePhase.UNLIT, states.last()?.phase)

            timestamps.value = listOf(start)
            runCurrent()

            val state = states.last()
            assertEquals(FirePhase.BURNING, state?.phase)
            assertTrue(state?.loggedToday == true)
        }

    @Test
    fun `the fire burns down as the days roll over`() =
        runTest {
            timestamps.value = listOf(start)
            val states = collectStates(createUseCase())
            assertTrue(states.last()?.loggedToday == true)

            advanceTimeBy(1.days)
            runCurrent()
            assertEquals(FirePhase.BURNING, states.last()?.phase)
            assertFalse(states.last()?.loggedToday == true)

            advanceTimeBy(1.days)
            runCurrent()
            assertEquals(FirePhase.EMBERS, states.last()?.phase)

            advanceTimeBy(1.days)
            runCurrent()
            assertEquals(FirePhase.OUT, states.last()?.phase)
            assertEquals(1, states.last()?.longestRunDays)
        }

    @Test
    fun `a late night entry counts for the day before the default boundary`() =
        runTest {
            timestamps.value = listOf(LocalDateTime(2026, 3, 15, 2, 0).toInstant(zone))
            val states = collectStates(createUseCase())

            assertEquals(FirePhase.BURNING, states.last()?.phase)
            assertFalse(states.last()?.loggedToday == true)
        }

    @Test
    fun `the configured day start hour moves the boundary`() =
        runTest {
            dayStartHour = 0
            timestamps.value = listOf(LocalDateTime(2026, 3, 15, 2, 0).toInstant(zone))
            val states = collectStates(createUseCase())

            assertTrue(states.last()?.loggedToday == true)
        }

    @Test
    fun `a failure reading entries emits no fire`() =
        runTest {
            val failing = flow<List<Instant>> { throw IllegalStateException("database closed") }
            val states = collectStates(createUseCase(entryTimestamps = failing))

            assertNull(states.last())
        }

    @Test
    fun `the fire comes back after a failed read`() =
        runTest {
            var attempts = 0
            val flaky =
                flow {
                    if (attempts++ == 0) throw IllegalStateException("database locked")
                    emitAll(timestamps)
                }
            timestamps.value = listOf(start)
            val states = collectStates(createUseCase(entryTimestamps = flaky))
            assertNull(states.last())

            advanceTimeBy(31.seconds)
            runCurrent()

            assertEquals(FirePhase.BURNING, states.last()?.phase)
        }

    @Test
    fun `a new day start hour applies within minutes`() =
        runTest {
            timestamps.value = listOf(LocalDateTime(2026, 3, 15, 2, 0).toInstant(zone))
            val states = collectStates(createUseCase())
            assertFalse(states.last()?.loggedToday == true)

            dayStartHour = 0
            advanceTimeBy(16.minutes)
            runCurrent()

            assertTrue(states.last()?.loggedToday == true)
        }

    private class FakeStreakSettings : StreakSettingsRepository {
        val enabled = MutableStateFlow(true)
        private val cached = MutableStateFlow(0)

        override fun observeStreakEnabled(): Flow<Boolean> = enabled

        override suspend fun isStreakEnabled(): Boolean = enabled.value

        override suspend fun setStreakEnabled(enabled: Boolean) {
            this.enabled.value = enabled
        }

        override fun observeCachedStreak(): Flow<Int> = cached

        override suspend fun getCachedStreak(): Int = cached.value

        override suspend fun setCachedStreak(value: Int) {
            cached.value = value
        }
    }
}
