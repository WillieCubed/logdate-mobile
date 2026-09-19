package app.logdate.client.domain.streak

import app.logdate.client.health.util.LogdatePreferencesDataSource
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.streak.StreakSettingsRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Observes the user's journaling [CampfireState], or `null` when streak tracking is off.
 *
 * The fire updates as soon as an entry is saved and again whenever a new streak day begins, so no
 * screen has to ask for a refresh. Entries count toward the streak day set by the user's
 * day-start hour, or [DEFAULT_STREAK_DAY_START_HOUR] when they have not chosen one.
 *
 * A failure reading entries is logged and emits `null`, the same as having no fire to show.
 */
class ObserveCampfireUseCase(
    private val notesRepository: JournalNotesRepository,
    private val streakSettingsRepository: StreakSettingsRepository,
    private val dayBoundaryPreferences: LogdatePreferencesDataSource,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<CampfireState?> =
        streakSettingsRepository
            .observeStreakEnabled()
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (enabled) observeFire() else flowOf(null)
            }

    private fun observeFire(): Flow<CampfireState?> =
        combine(
            notesRepository.observeEntryTimestamps(),
            observeStreakToday(),
        ) { timestamps, today ->
            val zone = timeZone()
            val loggedDays = timestamps.mapTo(HashSet()) { it.toStreakDay(zone, today.dayStartHour) }
            CampfireCalculator.calculate(loggedDays = loggedDays, today = today.date)
        }.flowOn(computeDispatcher)
            .catch<CampfireState?> { error ->
                Napier.e("Failed to observe the journaling campfire", error)
                emit(null)
            }

    private data class StreakToday(
        val date: LocalDate,
        val dayStartHour: Int,
    )

    /** Emits the current streak day, then again each time the next one begins. */
    private fun observeStreakToday(): Flow<StreakToday> =
        flow {
            while (true) {
                val dayStartHour =
                    dayBoundaryPreferences.getPreferences().dayStartHour ?: DEFAULT_STREAK_DAY_START_HOUR
                val zone = timeZone()
                val now = clock.now()
                emit(StreakToday(date = now.toStreakDay(zone, dayStartHour), dayStartHour = dayStartHour))
                delay((nextStreakDayStart(now, zone, dayStartHour) - now).coerceAtLeast(MIN_TICK))
            }
        }

    private companion object {
        val MIN_TICK = 1.seconds
    }
}
