package app.logdate.feature.core.settings.ui

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.time.Instant

actual fun formatPasskeyLastUsed(lastUsed: Instant): String {
    if (lastUsed == Instant.DISTANT_PAST) {
        return "Unknown"
    }

    val formatter =
        NSDateFormatter().apply {
            dateStyle = NSDateFormatterMediumStyle
            timeStyle = NSDateFormatterShortStyle
        }

    val date = NSDate.dateWithTimeIntervalSince1970(lastUsed.epochSeconds.toDouble())
    return formatter.stringFromDate(date)
}
