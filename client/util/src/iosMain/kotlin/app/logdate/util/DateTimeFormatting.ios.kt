package app.logdate.util

import kotlinx.datetime.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
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