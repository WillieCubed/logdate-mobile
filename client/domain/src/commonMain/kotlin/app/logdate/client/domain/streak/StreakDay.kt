package app.logdate.client.domain.streak

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The hour a streak day starts when the user has not chosen one.
 *
 * Matches the health layer's default day start, so someone who writes at 1:30 AM before bed is
 * credited for the day they are still living rather than the one that starts after they sleep.
 */
const val DEFAULT_STREAK_DAY_START_HOUR = 4

/**
 * The day this instant counts toward for streaks.
 *
 * A streak day runs from [dayStartHour] local time until the same hour the next morning. An entry
 * written before that hour belongs to the previous calendar day.
 */
fun Instant.toStreakDay(
    timeZone: TimeZone,
    dayStartHour: Int = DEFAULT_STREAK_DAY_START_HOUR,
): LocalDate {
    val local = toLocalDateTime(timeZone)
    return if (local.hour < dayStartHour.coerceIn(0, 23)) {
        local.date.minus(1, DateTimeUnit.DAY)
    } else {
        local.date
    }
}

/**
 * The instant the streak day after the one containing [after] begins.
 */
fun nextStreakDayStart(
    after: Instant,
    timeZone: TimeZone,
    dayStartHour: Int = DEFAULT_STREAK_DAY_START_HOUR,
): Instant {
    val nextDay = after.toStreakDay(timeZone, dayStartHour).plus(1, DateTimeUnit.DAY)
    return LocalDateTime(nextDay, LocalTime(dayStartHour.coerceIn(0, 23), 0)).toInstant(timeZone)
}
