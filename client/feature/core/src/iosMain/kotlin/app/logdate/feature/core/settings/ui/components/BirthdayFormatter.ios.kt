package app.logdate.feature.core.settings.ui.components

import kotlinx.datetime.LocalDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import kotlinx.datetime.number

/**
 * iOS implementation of localized date formatting.
 * Uses the iOS system's date formatting.
 */
actual fun formatDateLocalized(date: LocalDate): String {
    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterLongStyle
        timeStyle = NSDateFormatterNoStyle
        locale = NSLocale.currentLocale
        timeZone = NSTimeZone.localTimeZone
    }
    
    // Convert LocalDate to NSDate
    // First create an epoch timestamp for start of day in UTC
    val year = date.year
    val month = date.month.number
    val day = date.day
    
    // Create a date string that NSDateFormatter can parse
    val dateString = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}T00:00:00Z"
    
    // Use a temporary formatter to parse the ISO date
    val isoFormatter = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    }
    
    val nsDate = isoFormatter.dateFromString(dateString)
    
    return if (nsDate != null) {
        formatter.stringFromDate(nsDate)
    } else {
        // Fallback if conversion fails
        "${date.month.name} ${date.day}, ${date.year}"
    }
}
