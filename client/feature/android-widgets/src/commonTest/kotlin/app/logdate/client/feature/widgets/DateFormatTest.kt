package app.logdate.client.feature.widgets

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateFormatTest {
    @Test
    fun `formats January date correctly`() {
        assertEquals("January 1, 2025", LocalDate(2025, 1, 1).formatForDisplay())
    }

    @Test
    fun `formats February date correctly`() {
        assertEquals("February 14, 2025", LocalDate(2025, 2, 14).formatForDisplay())
    }

    @Test
    fun `formats March date correctly`() {
        assertEquals("March 24, 2025", LocalDate(2025, 3, 24).formatForDisplay())
    }

    @Test
    fun `formats December date correctly`() {
        assertEquals("December 31, 2024", LocalDate(2024, 12, 31).formatForDisplay())
    }

    @Test
    fun `formats single-digit day without padding`() {
        assertEquals("June 5, 2025", LocalDate(2025, 6, 5).formatForDisplay())
    }
}
