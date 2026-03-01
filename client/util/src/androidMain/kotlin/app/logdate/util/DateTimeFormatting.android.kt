package app.logdate.util

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private fun LocalDate.toJavaLocalDate(): java.time.LocalDate =
    java.time.LocalDate.of(year, month.number, day)

private fun TimeZone.toJavaZoneId(): java.time.ZoneId =
    java.time.ZoneId.of(id)


/**
 * Returns a formatted time string for this [Instant] based on the current system time zone and locale.
 *
 * For example, "3:30 p.m."
 */
actual val Instant.asTime: String
    get() {
        return java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())
            .atZone(TimeZone.currentSystemDefault().toJavaZoneId())
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }

/**
 * Android implementation of localized date formatting.
 * Uses the system locale and formatting settings.
 */
actual fun formatDateLocalized(date: LocalDate): String {
    val javaLocalDate = date.toJavaLocalDate()
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        .withLocale(Locale.getDefault())
    return javaLocalDate.format(formatter)
}
