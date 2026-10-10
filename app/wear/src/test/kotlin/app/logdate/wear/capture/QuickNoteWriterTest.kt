package app.logdate.wear.capture

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.location.WearLocationCaptureCoordinator
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class QuickNoteWriterTest {
    private val notesRepository = mockk<JournalNotesRepository>()
    private val locationCaptureCoordinator = mockk<WearLocationCaptureCoordinator>()
    private val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
    private val created = slot<JournalNote>()

    @Before
    fun setUp() {
        coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns null
        coEvery { notesRepository.create(capture(created)) } returns Uuid.random()
    }

    @Test
    fun `a feeling is saved as a mood check-in note`() =
        runTest {
            assertTrue(writer().save(Feeling.GOOD))

            val note = created.captured as JournalNote.Text
            assertEquals(MomentNoteCodec.encode(Feeling.GOOD), note.content)
            assertEquals(now, note.creationTimestamp)
        }

    @Test
    fun `the place is saved with the check-in`() =
        runTest {
            val location = NoteLocation(coordinates = NoteCoordinates(latitude = 51.5, longitude = -0.12))
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns location

            writer().save(Feeling.GREAT)

            assertEquals(location, created.captured.location)
        }

    @Test
    fun `a check-in that cannot be stored reports false`() =
        runTest {
            coEvery { notesRepository.create(any<JournalNote>()) } throws IllegalStateException("database closed")

            assertFalse(writer().save(Feeling.SAD))
        }

    private fun writer() =
        QuickNoteWriter(
            notesRepository = notesRepository,
            locationCaptureCoordinator = locationCaptureCoordinator,
            clock = FixedClock(now),
        )

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }
}
