package app.logdate.wear.v2.memories

import app.logdate.client.domain.recommendation.MemoryRecallData
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class OnThisDayFinderTest {
    private val today = LocalDate(2026, 10, 10)
    private val lastYear = LocalDate(2025, 10, 10)
    private val notesRepository = mockk<JournalNotesRepository>()

    @Test
    fun `the first voice note of the day opens it`() =
        runTest {
            val breakfast = text("Breakfast by the window", at(8))
            val walk = voice(at(12))
            val dinner = text("Dinner with Sam", at(19))
            coEvery { notesRepository.getNotesForDay(lastYear) } returns listOf(dinner, walk, breakfast)

            val found = finder(MemoryRecallData(lastYear, "Breakfast by the window")).find(today)

            assertEquals(OnThisDay(lastYear, yearsAgo = 1, leadMoment = walk, momentCount = 3, summary = "Breakfast by the window"), found)
        }

    @Test
    fun `without a voice note the first text opens it`() =
        runTest {
            val breakfast = text("Breakfast by the window", at(8))
            val dinner = text("Dinner with Sam", at(19))
            coEvery { notesRepository.getNotesForDay(lastYear) } returns listOf(dinner, breakfast)

            assertEquals(breakfast, finder(MemoryRecallData(lastYear, "")).find(today)?.leadMoment)
        }

    @Test
    fun `a day of only check-ins and photos has nothing to show`() =
        runTest {
            val checkIn = text("#mood:good Feeling good", at(9))
            val photo = JournalNote.Image(mediaRef = "p.jpg", creationTimestamp = at(10), lastUpdated = at(10))
            coEvery { notesRepository.getNotesForDay(lastYear) } returns listOf(checkIn, photo)

            assertNull(finder(MemoryRecallData(lastYear, "")).find(today))
        }

    @Test
    fun `a recalled summary that is a check-in is not shown`() =
        runTest {
            val checkIn = text("#mood:good Feeling good", at(7))
            val breakfast = text("Breakfast by the window", at(8))
            coEvery { notesRepository.getNotesForDay(lastYear) } returns listOf(checkIn, breakfast)

            val found = finder(MemoryRecallData(lastYear, "#mood:good Feeling good")).find(today)

            assertEquals("", found?.summary)
            assertEquals(breakfast, found?.leadMoment)
        }

    @Test
    fun `no recalled day means no card`() =
        runTest {
            assertNull(finder(null).find(today))
            coVerify(exactly = 0) { notesRepository.getNotesForDay(any()) }
        }

    @Test
    fun `years are counted from today`() =
        runTest {
            val threeYearsAgo = LocalDate(2023, 10, 11)
            coEvery { notesRepository.getNotesForDay(threeYearsAgo) } returns listOf(text("Moved in", at(10)))

            assertEquals(3, finder(MemoryRecallData(threeYearsAgo, "Moved in")).find(today)?.yearsAgo)
        }

    private fun finder(recalled: MemoryRecallData?) = OnThisDayFinder(recall = { recalled }, notesRepository = notesRepository)

    private fun at(hour: Int): Instant = LocalDateTime(2025, 10, 10, hour, 0).toInstant(TimeZone.UTC)

    private fun text(
        content: String,
        at: Instant,
    ) = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = content)

    private fun voice(at: Instant) = JournalNote.Audio(mediaRef = "v.m4a", durationMs = 30_000L, creationTimestamp = at, lastUpdated = at)
}
