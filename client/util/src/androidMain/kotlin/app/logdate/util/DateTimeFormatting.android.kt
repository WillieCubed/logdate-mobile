package app.logdate.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale


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