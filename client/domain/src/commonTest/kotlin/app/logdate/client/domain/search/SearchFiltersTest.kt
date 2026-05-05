package app.logdate.client.domain.search

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class SearchFiltersTest {
    private val tz = TimeZone.of("America/Los_Angeles")
    private val monday = DayOfWeek.MONDAY

    // Monday, 2026-05-04 14:30 PT
    private val now: Instant = LocalDateTime(2026, 5, 4, 14, 30).toInstant(tz)

    @Test
    fun `AllTime returns sentinel distant-past to distant-future window`() {
        val window = DateRangeFilter.AllTime.window(now, tz, monday)
        assertEquals(Instant.DISTANT_PAST, window.from)
        assertEquals(Instant.DISTANT_FUTURE, window.toExclusive)
    }

    @Test
    fun `Today covers from start of local day to start of next day`() {
        val window = DateRangeFilter.Today.window(now, tz, monday)
        assertEquals(LocalDate(2026, 5, 4).atStartOfDayIn(tz), window.from)
        assertEquals(LocalDate(2026, 5, 5).atStartOfDayIn(tz), window.toExclusive)
    }

    @Test
    fun `ThisWeek starts on the configured first day and is exactly seven days long`() {
        val window = DateRangeFilter.ThisWeek.window(now, tz, monday)
        assertEquals(LocalDate(2026, 5, 4).atStartOfDayIn(tz), window.from)
        assertEquals(LocalDate(2026, 5, 11).atStartOfDayIn(tz), window.toExclusive)
    }

    @Test
    fun `ThisWeek backs up to the configured first day when now is later in the week`() {
        val sunday = LocalDateTime(2026, 5, 10, 23, 0).toInstant(tz)
        val window = DateRangeFilter.ThisWeek.window(sunday, tz, monday)
        assertEquals(LocalDate(2026, 5, 4).atStartOfDayIn(tz), window.from)
        assertEquals(LocalDate(2026, 5, 11).atStartOfDayIn(tz), window.toExclusive)
    }

    @Test
    fun `ThisWeek with Sunday-start anchors on Sunday`() {
        val window = DateRangeFilter.ThisWeek.window(now, tz, DayOfWeek.SUNDAY)
        assertEquals(LocalDate(2026, 5, 3).atStartOfDayIn(tz), window.from)
        assertEquals(LocalDate(2026, 5, 10).atStartOfDayIn(tz), window.toExclusive)
    }

    @Test
    fun `ThisMonth covers the calendar month in local timezone`() {
        val window = DateRangeFilter.ThisMonth.window(now, tz, monday)
        assertEquals(LocalDate(2026, 5, 1).atStartOfDayIn(tz), window.from)
        assertEquals(LocalDate(2026, 6, 1).atStartOfDayIn(tz), window.toExclusive)
    }

    @Test
    fun `ThisYear covers Jan 1 to Jan 1 next year in local timezone`() {
        val window = DateRangeFilter.ThisYear.window(now, tz, monday)
        assertEquals(LocalDate(2026, 1, 1).atStartOfDayIn(tz), window.from)
        assertEquals(LocalDate(2027, 1, 1).atStartOfDayIn(tz), window.toExclusive)
    }

    @Test
    fun `windows are inclusive of from and exclusive of toExclusive`() {
        val window = DateRangeFilter.Today.window(now, tz, monday)
        assertTrue(window.from <= now)
        assertTrue(window.toExclusive > now)
    }
}
