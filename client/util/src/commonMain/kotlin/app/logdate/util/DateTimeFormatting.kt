package app.logdate.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/**
 * Returns the current date in the system's default time zone.
 *
 * @param timeZone The time zone to use for the current date. Defaults to the system's default time zone.
 */
fun LocalDate.Companion.now(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): LocalDate = Clock.System.now().toLocalDateTime(timeZone).date

fun LocalDateTime.toReadableDateShort(): String = format(LocalDateTime.Format {
    monthName(MonthNames.ENGLISH_FULL)
    char(' ')
    dayOfMonth(Padding.SPACE)
    if (year != Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year) {
        // TODO: Add support for multiple locales
        char(',')
        char(' ')
        year()
    }
})

fun LocalDate.toReadableDateShort(): String = format(LocalDate.Format {
    monthName(MonthNames.ENGLISH_FULL)
    char(' ')
    dayOfMonth(Padding.SPACE)
    if (year != Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year) {
        // TODO: Add support for multiple locales
        char(',')
        char(' ')
        year()
    }
})

/**
 * Converts an [Instant] into a short readable form
 *
 * For example, "March 13".
 */
fun Instant.toReadableDateShort(): String {
    val localDatetime = toLocalDateTime(TimeZone.currentSystemDefault())
    return localDatetime.toReadableDateShort()
}

/**
 * Converts an [Instant] into a short readable form with time
 *
 * For example, "March 13, 3:30 p.m.".
 */
fun Instant.toReadableDateTimeShort(): String {
    val localDatetime = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDatetime.toReadableDateShort()}, ${localTime}"
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
        // Get the day of week where Monday is 1 and Sunday is 7 (ISO-8601)
        val dayOfWeek = dayOfWeek.isoDayNumber

        // Get the ordinal day of year (1-366)
        val dayOfYear = dayOfYear

        // Find the nearest Thursday (adding days if Mon-Wed, subtracting if Fri-Sun)
        val nearestThursday = dayOfYear + (4 - dayOfWeek)

        // Get the ordinal day of year for January 4th, which is always in week 1
        val january4th = LocalDateTime(year, 1, 4, 0, 0)
        val january4thDayOfWeek = january4th.dayOfWeek.isoDayNumber

        // Find the nearest Thursday to January 4th
        val january4thNearestThursday = 4 + (4 - january4thDayOfWeek)

        // Calculate the week number
        var weekNumber = ((nearestThursday - january4thNearestThursday) / 7) + 1

        // Handle edge cases for beginning and end of year
        if (weekNumber < 1) {
            // If we're before the first week of this year, we're in the last week of previous year
            val december31LastYear = LocalDateTime(year - 1, 12, 31, 0, 0)
            return december31LastYear.weekOfYear
        } else if (weekNumber > 52) {
            // Check if this year has 53 weeks
            val december31ThisYear = LocalDateTime(year, 12, 31, 0, 0)
            val december31WeekDay = december31ThisYear.dayOfWeek.isoDayNumber

            // Year has 53 weeks if December 31st is on Thursday
            // OR if December 31st is on Wednesday in a leap year
            if (weekNumber == 53 && (december31WeekDay != 4 &&
                        !(december31WeekDay == 3 && isLeapYear(year)))
            ) {
                weekNumber = 1
            }
        }

        return weekNumber
    }

/**
 * Helper function to determine if a year is a leap year
 */
private fun isLeapYear(year: Int): Boolean {
    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}

val Instant.weekOfYear: Int
    get() {
        return toLocalDateTime(TimeZone.currentSystemDefault()).weekOfYear
    }

/**
 * Returns the number of calendar days between this [Instant] and the current time.
 */
val Instant.daysUntilNow: Int
    get() {
        return this.daysUntil(Clock.System.now(), TimeZone.currentSystemDefault())
    }

/**
 * Returns the number of weeks between this [Instant] and the current time.
 *
 * Rounds down to the nearest week.
 */
fun Instant.weeksAgo(): Int {
    val now = Clock.System.now()
    val days = (now.epochSeconds - this.epochSeconds) / (60 * 60 * 24)
    return (days / 7).toInt()
}

/**
 * Returns a formatted time string for this [Instant] based on the current system time zone and locale.
 *
 * For example, "3:30 p.m."
 */
expect val Instant.asTime: String

/**
 * Formats a LocalDate according to the current locale's full date format.
 * For example: "June 23, 2025" in US locale or "23 juin 2025" in French locale.
 *
 * @param date The date to format
 * @return A localized date string in long format
 */
expect fun formatDateLocalized(date: LocalDate): String