package app.logdate.feature.core.settings.ui

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant

actual fun formatPasskeyLastUsed(lastUsed: Instant): String {
    if (lastUsed == Instant.DISTANT_PAST) {
        return "Unknown"
    }

    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
    return formatter.format(lastUsed.toJavaInstant())
}
