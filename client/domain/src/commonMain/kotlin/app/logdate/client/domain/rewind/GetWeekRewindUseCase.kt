package app.logdate.client.domain.rewind

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/**
 * A use case for fetching a user's weekly rewind.
 *
 * This should be the default for retrieving the user's most weekly rewind. For rewinds that have
 * more specific time intervals, use [GetRewindUseCase].
 */
class GetWeekRewindUseCase(
    private val getRewindUseCase: GetRewindUseCase,
) {

    /**
     * Gets the rewind for the week prior to the current date.
     *
     * For example, if today is Sunday the 20th, this will return the rewind for the week that
     * started on the previous Monday the 14th and ended on Sunday the 20th, including content on
     * Sunday the 20th.
     *
     * @param weekStart The day of the week to start the week on. If not provided, the week will start on Sunday.
     */
    operator fun invoke(
        weekStart: DayOfWeek = DayOfWeek.SUNDAY, // Grrr, why is this not a Monday?
    ): Flow<RewindQueryResult> {
        val timezone = TimeZone.Companion.currentSystemDefault()
        val now = Clock.System.todayIn(timezone)
        val startOfWeek = now.minus(
            (now.dayOfWeek.ordinal - weekStart.ordinal + 7) % 7,
            DateTimeUnit.Companion.DAY,
        )
        val endOfWeek = now

        return getRewindUseCase(
            RewindParams(
                startOfWeek.atStartOfDayIn(timezone),
                endOfWeek.atStartOfDayIn(timezone)
            )
        )
    }
}