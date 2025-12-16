package app.logdate.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Returns a formatted time string for this [Instant] based on the current system time zone.
 *
 * For example, "3:30 PM"
 */
actual val Instant.asTime: String
    get() {
        val localDateTime = this.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = if (localDateTime.hour == 0) 12 else if (localDateTime.hour > 12) localDateTime.hour - 12 else localDateTime.hour
        val minute = localDateTime.minute.toString().padStart(2, '0')
        val amPm = if (localDateTime.hour < 12) "AM" else "PM"
        return "$hour:$minute $amPm"
    }

/**
 * Wasm/JS implementation of localized date formatting.
 * Uses the Intl.DateTimeFormat API for proper localization.
 */
@JsExport
actual fun formatDateLocalized(date: LocalDate): String {
    // JavaScript date expects a zero-based month (January is 0)
    val jsMonth = date.monthNumber - 1
    val jsDate = js("new Date(date.year, jsMonth, date.dayOfMonth)")
    
    // Use Intl.DateTimeFormat for proper localization
    // This will use the browser's locale settings
    val formattedDate = js("new Intl.DateTimeFormat(undefined, { dateStyle: 'long' }).format(jsDate)")
    
    return formattedDate.toString()
}