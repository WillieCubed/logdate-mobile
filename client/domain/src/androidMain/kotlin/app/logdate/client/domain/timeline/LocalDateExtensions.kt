package app.logdate.client.domain.timeline

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toKotlinInstant
import java.time.ZoneId

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
    return java.time.LocalDate.of(year, monthNumber, dayOfMonth)
}

/**
 * Convert a java.time.LocalDate to kotlinx.datetime.LocalDate.
 */
fun java.time.LocalDate.toKotlinLocalDate(): LocalDate {
    return LocalDate(year, monthValue, dayOfMonth)
}

/**
 * Convert java.time.Instant to kotlinx.datetime.Instant.
 */
fun java.time.Instant.toKotlinInstant(): Instant {
    return Instant.fromEpochSeconds(
        epochSecond,
        nano.toLong()
    )
}

/**
 * Convert kotlinx.datetime.Instant to java.time.Instant.
 */
fun Instant.toJavaInstant(): java.time.Instant {
    return java.time.Instant.ofEpochSecond(
        epochSeconds,
        nanosecondsOfSecond.toLong()
    )
}