package app.logdate.client.domain.rewind

import app.logdate.shared.model.Rewind
import app.logdate.util.getLocaleFirstDayOfWeek
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Retrieves a saved preview of the current week, or prepares its first snapshot. */
@OptIn(ExperimentalCoroutinesApi::class)
class GetCurrentWeekRewindUseCase(
    private val getRewind: (RewindParams) -> Flow<RewindQueryResult>,
    private val observeRewinds: (Instant, Instant) -> Flow<List<Rewind>>,
    private val firstDayOfWeekPreference: Flow<DayOfWeek?>,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = TimeZone::currentSystemDefault,
) {
    operator fun invoke(): Flow<RewindQueryResult> =
        firstDayOfWeekPreference
            .distinctUntilChanged()
            .flatMapLatest { firstDay ->
                flow {
                    while (true) {
                        val now = clock.now()
                        val window = currentWeekWindow(now, timeZone(), firstDay)
                        emit(window)
                        // Wake at the boundary, and periodically notice system time-zone changes.
                        delay(minOf(60_000L, (window.end - now).inWholeMilliseconds.coerceAtLeast(1)))
                    }
                }
            }.distinctUntilChanged()
            .flatMapLatest { window ->
                val cutoff = clock.now()
                // Observe the whole week so a later explicit update remains observable.
                observeRewinds(window.start, window.end)
                    .map { saved -> saved.currentSnapshot(window.start, clock.now()) }
                    .distinctUntilChanged()
                    .flatMapLatest { snapshot ->
                        if (snapshot != null) {
                            flowOf(RewindQueryResult.Success(snapshot))
                        } else {
                            getRewind(RewindParams(window.start, cutoff))
                        }
                    }
            }
}

/** Updates this week's saved story atomically, keeping its identity and attached responses. */
class RefreshCurrentWeekRewindUseCase(
    private val generate: suspend (Instant, Instant, Rewind?) -> GenerateBasicRewindResult,
    private val observeRewinds: (Instant, Instant) -> Flow<List<Rewind>>,
    private val firstDayOfWeekPreference: Flow<DayOfWeek?>,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = TimeZone::currentSystemDefault,
) {
    suspend operator fun invoke(): GenerateBasicRewindResult {
        val cutoff = clock.now()
        val window = currentWeekWindow(cutoff, timeZone(), firstDayOfWeekPreference.first())
        val existing = observeRewinds(window.start, window.end).first().currentSnapshot(window.start, cutoff)
        return generate(window.start, cutoff, existing)
    }
}

private fun currentWeekWindow(
    now: Instant,
    zone: TimeZone,
    firstDay: DayOfWeek?,
): RewindParams {
    val today = now.toLocalDateTime(zone).date
    val weekStart = firstDay ?: getLocaleFirstDayOfWeek()
    val start = today.minus((today.dayOfWeek.ordinal - weekStart.ordinal + 7) % 7, DateTimeUnit.DAY)
    return RewindParams(start.atStartOfDayIn(zone), start.plus(7, DateTimeUnit.DAY).atStartOfDayIn(zone))
}

private fun List<Rewind>.currentSnapshot(
    start: Instant,
    cutoff: Instant,
): Rewind? = filter { it.startDate == start && it.endDate in start..cutoff }.maxByOrNull { it.endDate }
