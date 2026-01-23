package app.logdate.feature.core.settings.ui

import kotlinx.datetime.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970

actual fun formatPasskeyLastUsed(lastUsed: Instant): String {
    if (lastUsed == Instant.DISTANT_PAST) {
        return "Unknown"
    }

    val formatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterShortStyle
    }

    val date = NSDate.dateWithTimeIntervalSince1970(lastUsed.epochSeconds.toDouble())
    return formatter.stringFromDate(date)
}
