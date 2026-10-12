package app.logdate.client.domain.reminders

import app.logdate.client.domain.streak.FakeNotesRepository
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.streak.StreakSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CampfireFadeDetectorTest {
    private val times = MutableStateFlow<List<Instant>>(emptyList())
    private val settings = Settings()
    private val notes =
        object : JournalNotesRepository by FakeNotesRepository() {
            override fun observeEntryTimestamps() = times
        }
    private val clock = ReminderClock(Instant.parse("2026-10-12T03:59:00Z"), TimeZone.UTC, dayStartHour = 4)

    @Test
    fun `writing after midnight before day start counts in the ongoing streak day`() =
        runTest {
            times.value = listOf(Instant.parse("2026-10-12T01:00:00Z"))
            val detector = CampfireFadeDetector(notes, settings)
            assertEquals(ReminderJournalState(true, FirePhase.BURNING), detector.read(clock))
            assertEquals(
                ReminderJournalState(false, FirePhase.BURNING),
                detector.read(clock.copy(now = Instant.parse("2026-10-12T04:00:00Z"))),
            )
        }

    @Test
    fun `embers and last stretch produce the real campfire reminder`() =
        runTest {
            times.value = listOf(Instant.parse("2026-10-09T12:00:00Z"))
            val late = clock.copy(now = Instant.parse("2026-10-11T20:00:00Z"))
            val state = CampfireFadeDetector(notes, settings).read(late)
            assertEquals(ReminderJournalState(false, FirePhase.EMBERS), state)
            assertEquals(
                ReminderDecision.Deliver(ReminderTrigger.CAMPFIRE_EMBERS, 0),
                ReminderOrchestrator.decide(late, ReminderSignals(true, state.writtenToday, campfire = state.campfire)),
            )
        }

    @Test
    fun `disabled streak tracking still suppresses wind down for todays writing`() =
        runTest {
            settings.enabled.value = false
            times.value = listOf(Instant.parse("2026-10-12T02:00:00Z"))
            assertEquals(ReminderJournalState(true, null), CampfireFadeDetector(notes, settings).read(clock))
        }

    @Test
    fun `future timestamps do not count as writing and later reads reflect edits`() =
        runTest {
            times.value = listOf(Instant.parse("2026-10-12T04:00:00Z"))
            val detector = CampfireFadeDetector(notes, settings)
            assertEquals(ReminderJournalState(false, FirePhase.UNLIT), detector.read(clock))
            times.value = listOf(Instant.parse("2026-10-12T03:58:00Z"))
            assertEquals(ReminderJournalState(true, FirePhase.BURNING), detector.read(clock))
            times.value = emptyList()
            assertEquals(ReminderJournalState(false, FirePhase.UNLIT), detector.read(clock))
        }

    @Test
    fun `turning off streaks during journal read omits the fire`() =
        runTest {
            val changing =
                object : JournalNotesRepository by notes {
                    override fun observeEntryTimestamps() =
                        flow {
                            settings.enabled.value = false
                            emit(listOf(Instant.parse("2026-10-09T12:00:00Z")))
                        }
                }
            assertEquals(ReminderJournalState(false, null), CampfireFadeDetector(changing, settings).read(clock))
        }

    private class Settings : StreakSettingsRepository {
        val enabled = MutableStateFlow(true)
        private val cached = MutableStateFlow(0)

        override fun observeStreakEnabled() = enabled

        override suspend fun isStreakEnabled() = enabled.value

        override suspend fun setStreakEnabled(enabled: Boolean) {
            this.enabled.value = enabled
        }

        override fun observeCachedStreak() = cached

        override suspend fun getCachedStreak() = cached.value

        override suspend fun setCachedStreak(value: Int) {
            cached.value = value
        }
    }
}
