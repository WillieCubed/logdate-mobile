package app.logdate.util

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.Locale
import kotlin.time.Instant

private fun LocalDate.toJavaLocalDate(): java.time.LocalDate = java.time.LocalDate.of(year, month.number, day)

private fun TimeZone.toJavaZoneId(): java.time.ZoneId = java.time.ZoneId.of(id)

/**
 * Returns a formatted time string for this [Instant] based on the current system time zone and locale.
 *
 * For example, "3:30 p.m."
 */
actual val Instant.asTime: String
    get() {
        return java.time.Instant
            .ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())
            .atZone(TimeZone.currentSystemDefault().toJavaZoneId())
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }

/**
 * Android implementation of localized date formatting.
 * Uses the system locale and formatting settings.
 */
actual fun formatDateLocalized(date: LocalDate): String {
    val javaLocalDate = date.toJavaLocalDate()
    val formatter =
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.LONG)
            .withLocale(Locale.getDefault())
    return javaLocalDate.format(formatter)
}

actual fun getLocaleFirstDayOfWeek(): DayOfWeek {
    val calendarFirstDay = Calendar.getInstance(Locale.getDefault()).firstDayOfWeek
    return when (calendarFirstDay) {
        Calendar.MONDAY -> DayOfWeek.MONDAY
        Calendar.TUESDAY -> DayOfWeek.TUESDAY
        Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
        Calendar.THURSDAY -> DayOfWeek.THURSDAY
        Calendar.FRIDAY -> DayOfWeek.FRIDAY
        Calendar.SATURDAY -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }
}
