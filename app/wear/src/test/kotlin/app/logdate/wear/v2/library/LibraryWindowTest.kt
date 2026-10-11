package app.logdate.wear.v2.library

import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.today.TodayWindow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LibraryWindowTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val saturday =
        TodayWindow(
            date = LocalDate(2026, 10, 3),
            timeZone = zone,
            start = LocalDate(2026, 10, 3).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, 4).atStartOfDayIn(zone),
        )
    private val at = Instant.fromEpochMilliseconds(1_791_000_000_000)

    @Test
    fun `the list starts 6 days before today and the window 13 days before`() {
        assertEquals(LocalDate(2026, 9, 27).atStartOfDayIn(zone), LibraryWindow.recentStart(saturday))
        assertEquals(LocalDate(2026, 9, 20).atStartOfDayIn(zone), LibraryWindow.windowStart(saturday))
    }

    @Test
    fun `a daylight saving change does not move the window by an hour`() {
        val afterChange =
            TodayWindow(
                date = LocalDate(2026, 11, 1),
                timeZone = zone,
                start = LocalDate(2026, 11, 1).atStartOfDayIn(zone),
                end = LocalDate(2026, 11, 2).atStartOfDayIn(zone),
            )

        assertEquals(LocalDate(2026, 10, 26).atStartOfDayIn(zone), LibraryWindow.recentStart(afterChange))
    }

    @Test
    fun `voice and text moments are in the library and check-ins and photos are not`() {
        assertTrue(LibraryWindow.isLibraryMoment(JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Lunch")))
        assertTrue(LibraryWindow.isLibraryMoment(JournalNote.Audio(mediaRef = "a.m4a", creationTimestamp = at, lastUpdated = at)))
        assertFalse(LibraryWindow.isLibraryMoment(JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "#mood:ok Fine")))
        assertFalse(LibraryWindow.isLibraryMoment(JournalNote.Image(mediaRef = "p.jpg", creationTimestamp = at, lastUpdated = at)))
    }
}
