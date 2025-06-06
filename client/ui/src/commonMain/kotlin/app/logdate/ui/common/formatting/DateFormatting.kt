package app.logdate.ui.common.formatting

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/**
 * Returns a relative date string for this [LocalDate].
 *
 * This function assumes that the date is in the current time zone, using the following rules to
 * produce a formatted a local date:
 * - If the date is today, return "Today"
 * - If the date is yesterday, return "Yesterday"
 * - If the date is within the last week, return the day of the week (e.g. "last Tuesday")
 * - If the date is after the last week but otherwise within the current year, return the month and day (e.g. "May 12")
 * - If the date is in a different year, return the full date (e.g. "May 12, 2021")
 */
fun LocalDate.asRelativeDate(): String {
    // TODO: Localize date strings
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    when {
        this == today -> return "Today"
        this == today.minus(1, DateTimeUnit.DAY) -> return "Yesterday"
    }

    val weekAgo = today.minus(7, DateTimeUnit.DAY)

    return when {
        this > weekAgo -> {
            val dayName = dayOfWeek.asText()
            // TODO: Extract formatting strings to resources
            if (this.dayOfWeek > today.dayOfWeek) "last $dayName" else dayName
        }

        this.year == today.year -> "${month.asText()} $dayOfMonth"
        else -> "${month.asText()} $dayOfMonth, $year"
    }
}

// TODO: Localize months
private fun Month.asText(): String = when (this) {
    Month.JANUARY -> "January"
    Month.FEBRUARY -> "February"
    Month.MARCH -> "March"
    Month.APRIL -> "April"
    Month.MAY -> "May"
    Month.JUNE -> "June"
    Month.JULY -> "July"
    Month.AUGUST -> "August"
    Month.SEPTEMBER -> "September"
    Month.OCTOBER -> "October"
    Month.NOVEMBER -> "November"
    Month.DECEMBER -> "December"
    else -> error("Unknown month: $this")
}

// TODO: Localize days of the week
private fun DayOfWeek.asText(): String = when (this) {
    DayOfWeek.MONDAY -> "Monday"
    DayOfWeek.TUESDAY -> "Tuesday"
    DayOfWeek.WEDNESDAY -> "Wednesday"
    DayOfWeek.THURSDAY -> "Thursday"
    DayOfWeek.FRIDAY -> "Friday"
    DayOfWeek.SATURDAY -> "Saturday"
    DayOfWeek.SUNDAY -> "Sunday"
    else -> error("Unknown day of week: $this")
}