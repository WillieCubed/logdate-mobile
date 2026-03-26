package app.logdate.client.domain.timeline

import app.logdate.client.domain.dayboundary.DayBoundarySettings
import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.health.LocalFirstHealthRepository
import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * A [DayBoundarySettingsRepository] with a configurable sleep-based toggle.
 * Defaults to disabled for tests that don't exercise day boundary logic.
 */
class FakeDayBoundarySettingsRepository(
    private val sleepEnabled: Boolean = false,
) : DayBoundarySettingsRepository {
    override suspend fun getSettings() = DayBoundarySettings(sleepBasedBoundariesEnabled = sleepEnabled)

    override fun observeSettings() = flowOf(DayBoundarySettings(sleepBasedBoundariesEnabled = sleepEnabled))

    override suspend fun setSleepBasedBoundariesEnabled(enabled: Boolean) {}
}

/**
 * A [LocalFirstHealthRepository] that returns configurable [DayBounds].
 *
 * Resolution order: [boundsByDate] → [bounds] → default (4 AM to next-day 4 AM).
 * Set [throwable] to simulate Health Connect failures.
 */
class FakeHealthRepository : LocalFirstHealthRepository {
    var bounds: DayBounds? = null
    var boundsByDate: Map<LocalDate, DayBounds> = emptyMap()
    var throwable: Throwable? = null

    override suspend fun getDayBoundsForDate(
        date: LocalDate,
        timeZone: TimeZone,
        sleepBasedBoundariesEnabled: Boolean,
    ): DayBounds {
        throwable?.let { throw it }
        boundsByDate[date]?.let { return it }
        bounds?.let { return it }
        val start = LocalDateTime(date, LocalTime(4, 0)).toInstant(timeZone)
        val nextDay = date.plus(1, DateTimeUnit.DAY)
        val end = LocalDateTime(nextDay, LocalTime(4, 0)).toInstant(timeZone)
        return DayBounds(start, end)
    }

    override suspend fun isHealthDataAvailable() = true

    override suspend fun getAvailableDataTypes() = emptyList<String>()

    override suspend fun hasSleepPermissions() = true

    override suspend fun requestSleepPermissions() = true

    override suspend fun getSleepSessions(
        start: Instant,
        end: Instant,
    ) = emptyList<SleepSession>()

    override suspend fun getAverageWakeUpTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? = null

    override suspend fun getAverageSleepTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? = null
}

/**
 * Creates a [GroupNotesByDayBoundsUseCase] that uses pure calendar-date grouping
 * (sleep-based boundaries disabled). Use this in tests that don't exercise day boundary logic.
 */
fun calendarDateGrouper(): GroupNotesByDayBoundsUseCase {
    val settingsRepo = FakeDayBoundarySettingsRepository(sleepEnabled = false)
    val healthRepo = FakeHealthRepository()
    return GroupNotesByDayBoundsUseCase(GetDayBoundsUseCase(healthRepo, settingsRepo), settingsRepo)
}
