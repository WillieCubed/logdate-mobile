package app.logdate.client.domain.timeline

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.time.ZoneId
import kotlinx.datetime.number

/**
 * Extension functions for LocalDate to work with java.time conversions
 */

/**
 * Add the specified number of days to this date.
 */
fun LocalDate.plusDays(days: Long): LocalDate {
    val javaDate = toJavaLocalDate()
    return javaDate.plusDays(days).toKotlinLocalDate()
}

/**
 * Subtract the specified number of days from this date.
 */
fun LocalDate.minusDays(days: Long): LocalDate {
    val javaDate = toJavaLocalDate()
    return javaDate.minusDays(days).toKotlinLocalDate()
}

/**
 * Convert this LocalDate to a java.time.LocalDate.
 */
fun LocalDate.toJavaLocalDate(): java.time.LocalDate {
    return java.time.LocalDate.of(year, month.number, day)
}

/**
 * Convert a java.time.LocalDate to kotlinx.datetime.LocalDate.
 */
fun java.time.LocalDate.toKotlinLocalDate(): LocalDate {
    return LocalDate(year, monthValue, dayOfMonth)
}

/**
 * Convert java.time.Instant to kotlin.time.Instant.
 */
fun java.time.Instant.toKotlinInstant(): Instant {
    return Instant.fromEpochSeconds(
        epochSecond,
        nano.toLong()
    )
}

/**
 * Convert kotlin.time.Instant to java.time.Instant.
 */
fun Instant.toJavaInstant(): java.time.Instant {
    return java.time.Instant.ofEpochSecond(
        epochSeconds,
        nanosecondsOfSecond.toLong()
    )
}
