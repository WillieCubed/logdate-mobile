package app.logdate.client.domain.reminders

import app.logdate.client.domain.recommendation.DefaultPlaceFamiliarityRepository
import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.domain.streak.FakeNotesRepository
import app.logdate.client.health.SleepRepository
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.streak.StreakSettingsRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class LivePhoneReminderSnapshotSourceTest {
    private var now = Instant.parse("2026-10-12T21:30:00Z")
    private var current = PhoneReminderEnvironment("local", true, false, TimeZone.UTC, 4)
    private var times = emptyList<Instant>()
    private var marks = 2
    private var afterMarks: () -> Unit = {}
    private var workout: Instant? = null
    private var healthReads = 0
    private val notes =
        object : JournalNotesRepository by FakeNotesRepository() {
            override fun observeEntryTimestamps() = flowOf(times)
        }
    private val settings =
        object : StreakSettingsRepository {
            override fun observeStreakEnabled() = flowOf(true)

            override suspend fun isStreakEnabled() = true

            override suspend fun setStreakEnabled(enabled: Boolean) = Unit

            override fun observeCachedStreak() = flowOf(0)

            override suspend fun getCachedStreak() = 0

            override suspend fun setCachedStreak(value: Int) = Unit
        }
    private val sleep =
        object : SleepRepository {
            override suspend fun hasSleepPermissions() = true

            override suspend fun requestSleepPermissions(): Boolean = error("Never request health permission in background")

            override suspend fun getSleepSessions(
                start: Instant,
                end: Instant,
            ): List<SleepSession> {
                healthReads++
                return listOf(SleepSession("night", Instant.parse("2026-10-11T23:00:00Z"), Instant.parse("2026-10-12T08:00:00Z")))
            }

            override suspend fun getAverageWakeUpTime(
                timeZone: TimeZone,
                days: Int,
            ): TimeOfDay? = error("Use sessions")

            override suspend fun getAverageSleepTime(
                timeZone: TimeZone,
                days: Int,
            ): TimeOfDay? = error("Use sessions")
        }

    private fun source(): LivePhoneReminderSnapshotSource {
        val storage = FakeKeyValueStorage()
        return LivePhoneReminderSnapshotSource(
            environment = { current },
            windDown = WindDownTimeResolver(sleep),
            journal = CampfireFadeDetector(notes, settings),
            arrivals =
                NovelPlaceArrivalDetector(storage, DefaultPlaceFamiliarityRepository(storage), {
                    error("Places are off")
                }, { error("Places are off") }),
            openMarks = { marks.also { afterMarks() } },
            workoutEndedAt = { workout },
            clock =
                object : Clock {
                    override fun now() = this@LivePhoneReminderSnapshotSourceTest.now
                },
        )
    }

    @Test
    fun `live journal and marks use learned waking hours for the reminder decision`() =
        runTest {
            times = listOf(Instant.parse("2026-10-12T10:00:00Z"))
            val snapshot = source().read()
            assertEquals("local", snapshot.audience)
            assertEquals(WakingHours(LocalTime(8, 0), LocalTime(23, 0)), snapshot.clock.wakingHours)
            assertTrue(snapshot.signals.writtenToday)
            assertEquals(
                ReminderDecision.Deliver(ReminderTrigger.WIND_DOWN, 2),
                ReminderOrchestrator.decide(snapshot.clock, snapshot.signals),
            )
        }

    @Test
    fun `later snapshots reflect resolved marks and saved entries`() =
        runTest {
            val source = source()
            assertEquals(2, source.read().signals.openMarks)
            marks = 0
            times = listOf(Instant.parse("2026-10-12T10:00:00Z"))
            val snapshot = source.read()
            assertEquals(ReminderDecision.Skip(SkipReason.NOTHING_TO_REMIND), ReminderOrchestrator.decide(snapshot.clock, snapshot.signals))
        }

    @Test
    fun `disabled reminders skip health and private signal reads`() =
        runTest {
            current = current.copy(enabled = false)
            val snapshot = source().read()
            assertFalse(snapshot.signals.enabled)
            assertEquals(0, healthReads)
            assertEquals("local", snapshot.audience)
        }

    @Test
    fun `scope or consent changes during suspended signal collection hold delivery`() =
        runTest {
            val initial = current
            val changes = listOf(initial.copy(audience = "other"), initial.copy(enabled = false), initial.copy(dayStartHour = 6))
            for (changed in changes) {
                current = initial
                afterMarks = { current = changed }
                val snapshot = source().read()
                assertFalse(snapshot.signals.enabled)
                assertEquals("", snapshot.audience)
            }
        }

    @Test
    fun `crossing a streak day while collecting signals holds stale journal data`() =
        runTest {
            now = Instant.parse("2026-10-12T03:59:59Z")
            times = listOf(Instant.parse("2026-10-12T01:00:00Z"))
            afterMarks = { now = Instant.parse("2026-10-12T04:00:00Z") }
            assertFalse(source().read().signals.enabled)
        }

    @Test
    fun `a newly completed workout reaches the prioritized decision`() =
        runTest {
            workout = Instant.parse("2026-10-12T21:20:00Z")
            val snapshot = source().read()
            assertEquals(
                ReminderDecision.Deliver(ReminderTrigger.WORKOUT_ENDED, 2),
                ReminderOrchestrator.decide(snapshot.clock, snapshot.signals),
            )
        }
}
