package app.logdate.util

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Returns a formatted time string for this [Instant] based on the current system time zone.
 *
 * For example, "3:30 PM"
 */
actual val Instant.asTime: String
    get() {
        val localDateTime = this.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour =
            if (localDateTime.hour == 0) {
                12
            } else if (localDateTime.hour > 12) {
                localDateTime.hour - 12
            } else {
                localDateTime.hour
            }
        val minute = localDateTime.minute.toString().padStart(2, '0')
        val amPm = if (localDateTime.hour < 12) "AM" else "PM"
        return "$hour:$minute $amPm"
    }

actual fun formatDateLocalized(date: LocalDate): String {
    val format =
        LocalDate.Format {
            monthName(MonthNames.ENGLISH_FULL)
            char(' ')
            day(Padding.NONE)
            char(',')
            char(' ')
            year()
        }

    return format.format(date)
}

actual fun getLocaleFirstDayOfWeek(): DayOfWeek {
    // Default to Monday for web — no reliable browser API for locale first day of week
    return DayOfWeek.MONDAY
}
