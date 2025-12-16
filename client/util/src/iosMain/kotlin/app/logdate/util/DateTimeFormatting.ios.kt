package app.logdate.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName

/**
 * Returns a formatted time string for this [Instant] based on the current system time zone and locale.
 *
 * For example, "3:30 p.m."
 */
actual val Instant.asTime: String
    get() {
        val date = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
        val formatter = NSDateFormatter().apply {
            dateStyle = NSDateFormatterNoStyle
            timeStyle = NSDateFormatterShortStyle
            // TODO: Verify this won't actually return a null value
            this.timeZone = NSTimeZone.timeZoneWithName(timeZone.name)!!
            locale = NSLocale.currentLocale
        }
        return formatter.stringFromDate(date)
    }

/**
 * iOS implementation of localized date formatting.
 * Uses the system locale and formatting settings.
 */
actual fun formatDateLocalized(date: LocalDate): String {
    // Create NSDate from LocalDate
    // Note: NSDate uses time interval since 1970-01-01 00:00:00 UTC
    // To create a correct NSDate from a LocalDate, we need to account for the date only
    // Create a timestamp for the start of the day (midnight)
    val startOfDay = kotlinx.datetime.LocalDate(
        date.year, date.monthNumber, date.dayOfMonth
    ).atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
    val timestamp = startOfDay.epochSeconds
    
    val nsDate = NSDate.dateWithTimeIntervalSince1970(timestamp.toDouble())
    
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterLongStyle
        timeStyle = NSDateFormatterNoStyle
        locale = NSLocale.currentLocale
    }
    
    return formatter.stringFromDate(nsDate)
}