package app.logdate.wear.v2.moments

import java.util.Locale
import kotlin.time.Duration

/**
 * [duration] as a clock reading in [locale]'s digits: "4:12" under an hour and "1:04:12" from an
 * hour up. A negative duration reads as "0:00".
 */
fun formatClockDuration(
    duration: Duration,
    locale: Locale = Locale.getDefault(),
): String {
    val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    if (hours > 0) return String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
    return String.format(locale, "%d:%02d", minutes, seconds)
}

private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L
