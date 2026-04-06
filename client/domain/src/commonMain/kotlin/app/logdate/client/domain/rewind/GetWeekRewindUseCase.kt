package app.logdate.client.domain.rewind

import app.logdate.util.getLocaleFirstDayOfWeek
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * A use case for fetching a user's weekly rewind.
 *
 * This should be the default for retrieving the user's most weekly rewind. For rewinds that have
 * more specific time intervals, use [GetRewindUseCase].
 *
 * @param getRewindUseCase Use case for retrieving rewinds by time period
 * @param firstDayOfWeekPreference Observable stream of the user's preferred first day of the week,
 *   or null when no preference is set. When null, the device locale default is used.
 */
class GetWeekRewindUseCase(
    private val getRewindUseCase: GetRewindUseCase,
    private val firstDayOfWeekPreference: Flow<DayOfWeek?>,
) {
    /**
     * Gets the rewind for the most recent complete week.
     *
     * Targets the previous full week so there is always a complete 7-day period
     * with content available. Uses the user's preferred week start day from settings,
     * falling back to the device locale default.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<RewindQueryResult> =
        firstDayOfWeekPreference
            .map { it ?: getLocaleFirstDayOfWeek() }
            .flatMapLatest { weekStart -> getRewindForWeek(weekStart) }

    private fun getRewindForWeek(weekStart: DayOfWeek): Flow<RewindQueryResult> {
        val timezone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(timezone)

        // Start of the current week
        val startOfThisWeek =
            today.minus(
                (today.dayOfWeek.ordinal - weekStart.ordinal + 7) % 7,
                DateTimeUnit.DAY,
            )

        // Previous complete week: 7 days before this week's start → this week's start
        val startOfLastWeek = startOfThisWeek.minus(7, DateTimeUnit.DAY)
        val endOfLastWeek = startOfThisWeek

        return getRewindUseCase(
            RewindParams(
                start = startOfLastWeek.atStartOfDayIn(timezone),
                end = endOfLastWeek.atStartOfDayIn(timezone),
            ),
        )
    }
}
