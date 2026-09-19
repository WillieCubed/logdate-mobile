package app.logdate.client.domain.streak

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakDayTest {
    private val newYork = TimeZone.of("America/New_York")

    @Test
    fun `an entry before the day start hour counts for the previous day`() {
        val instant = LocalDateTime(2026, 3, 15, 1, 30).toInstant(newYork)

        assertEquals(LocalDate(2026, 3, 14), instant.toStreakDay(newYork, dayStartHour = 4))
    }

    @Test
    fun `an entry at the day start hour counts for that day`() {
        val instant = LocalDateTime(2026, 3, 15, 4, 0).toInstant(newYork)

        assertEquals(LocalDate(2026, 3, 15), instant.toStreakDay(newYork, dayStartHour = 4))
    }

    @Test
    fun `an entry one minute before the day start hour counts for the previous day`() {
        val instant = LocalDateTime(2026, 3, 15, 3, 59).toInstant(newYork)

        assertEquals(LocalDate(2026, 3, 14), instant.toStreakDay(newYork, dayStartHour = 4))
    }

    @Test
    fun `a day start hour of zero uses calendar days`() {
        val instant = LocalDateTime(2026, 3, 15, 0, 30).toInstant(newYork)

        assertEquals(LocalDate(2026, 3, 15), instant.toStreakDay(newYork, dayStartHour = 0))
    }

    @Test
    fun `the default day start hour is 4 AM`() {
        val instant = LocalDateTime(2026, 3, 15, 2, 0).toInstant(newYork)

        assertEquals(LocalDate(2026, 3, 14), instant.toStreakDay(newYork))
    }

    @Test
    fun `a fixed offset time zone is applied before the boundary`() {
        val tokyo = TimeZone.of("+09:00")
        val instant = LocalDateTime(2026, 3, 15, 17, 0).toInstant(UtcOffset.ZERO)

        // 17:00 UTC is 02:00 on March 16 in UTC+9, before the 4 AM boundary.
        assertEquals(LocalDate(2026, 3, 15), instant.toStreakDay(tokyo, dayStartHour = 4))
    }

    @Test
    fun `the daylight saving transition keeps early hours on the previous day`() {
        // Clocks in New York jump from 02:00 to 03:00 on March 8, 2026.
        val instant = LocalDateTime(2026, 3, 8, 3, 30).toInstant(newYork)

        assertEquals(LocalDate(2026, 3, 7), instant.toStreakDay(newYork, dayStartHour = 4))
    }

    @Test
    fun `the next boundary after a daytime instant is tomorrow at the start hour`() {
        val now = LocalDateTime(2026, 3, 15, 10, 0).toInstant(newYork)

        assertEquals(
            LocalDateTime(2026, 3, 16, 4, 0).toInstant(newYork),
            nextStreakDayStart(now, newYork, dayStartHour = 4),
        )
    }

    @Test
    fun `the next boundary after a late night instant is later that morning`() {
        val now = LocalDateTime(2026, 3, 16, 2, 0).toInstant(newYork)

        assertEquals(
            LocalDateTime(2026, 3, 16, 4, 0).toInstant(newYork),
            nextStreakDayStart(now, newYork, dayStartHour = 4),
        )
    }

    @Test
    fun `an out of range day start hour is clamped`() {
        val instant = LocalDateTime(2026, 3, 15, 22, 30).toInstant(newYork)

        // 30 clamps to 23, so 22:30 is still part of the previous day.
        assertEquals(LocalDate(2026, 3, 14), instant.toStreakDay(newYork, dayStartHour = 30))
        assertEquals(LocalDate(2026, 3, 15), instant.toStreakDay(newYork, dayStartHour = -2))
    }
}
