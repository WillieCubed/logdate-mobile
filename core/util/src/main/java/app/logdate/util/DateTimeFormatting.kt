package app.logdate.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Converts an [Instant] into a short readable form
 *
 * For example, "March 13".
 */
fun Instant.toReadableDateShort(): String {
    val localDatetime = toLocalDateTime(TimeZone.currentSystemDefault())
    return localDatetime
        .format(LocalDateTime.Format {
            monthName(MonthNames.ENGLISH_FULL)
            char(' ')
            dayOfMonth(Padding.SPACE)
            if (localDatetime.year != Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).year
            ) {
                year()
            }
        })
}

val Instant.localTime: String
    get() {
        val localDateTime = toLocalDateTime(TimeZone.currentSystemDefault()).time
        return localDateTime.format(LocalTime.Format {
            // TODO: Support 24-hour times
            amPmHour(padding = Padding.ZERO)
            char(':')
            minute()
            char(' ')
            amPmMarker("a.m.", "p.m.")
        })
    }


/**
 * Returns the week of the year for this date.
 *
 * This uses the ISO 8601 definition of week number, namely that the first week of the year is the
 * week that contains the first Thursday of the year. In other words, it is the week that contains
 * a majority (4) of its days in the new year.
 *
 * Note that this may return 53 for the last week of the year for all years that have Thursday as
 * the 1st of January and on leap years that start on Wednesday the 1st
 *
 * @link https://en.wikipedia.org/wiki/ISO_week_date
 */
val LocalDateTime.weekOfYear: Int
    get() {
        return Clock.System.now().toJavaInstant().atZone(ZoneId.systemDefault())
            .get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())
    }

val Instant.weekOfYear: Int
    get() {
        return toLocalDateTime(TimeZone.currentSystemDefault()).weekOfYear
    }