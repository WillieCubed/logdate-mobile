package app.logdate.wear.recording

import app.logdate.client.domain.activity.CaptureActivitySource
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.location.WearLocationCaptureCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [AudioMomentSaver]: a finished recording becomes an audio note, is discarded as too
 * short, or stays on disk when the note can't be stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioMomentSaverTest {
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val durationResolver = mockk<AudioDurationResolver>()
    private val noteHealthAnnotator = mockk<NoteHealthAnnotator>(relaxed = true)
    private val locationCaptureCoordinator = mockk<WearLocationCaptureCoordinator>()
    private val deletedFiles = mutableListOf<String>()
    private val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)

    @Before
    fun setUp() {
        coEvery { durationResolver.resolveDurationMs(any()) } returns 4_200L
        coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns null
        coEvery { notesRepository.create(any<JournalNote>()) } returns Uuid.random()
    }

    @Test
    fun `a recording becomes an audio note of the length its file reads`() =
        runTest {
            val created = slot<JournalNote>()
            coEvery { notesRepository.create(capture(created)) } returns Uuid.random()

            val result = saver().save("/files/audio_notes/a.m4a", recorderElapsed = 9.seconds)

            val note = (result as AudioSaveResult.Saved).note
            assertEquals("/files/audio_notes/a.m4a", note.mediaRef)
            assertEquals(4_200L, note.durationMs)
            assertEquals(now, note.creationTimestamp)
            assertEquals(note, created.captured)
        }

    @Test
    fun `a recording made while moving records it`() =
        runTest {
            val result = saver(activity = { CaptureActivity.MOVING }).save("/files/audio_notes/walk.m4a", recorderElapsed = 9.seconds)

            assertEquals(CaptureActivity.MOVING, (result as AudioSaveResult.Saved).note.capturedDuring)
        }

    @Test
    fun `the recorder clock counts when the file length cannot be read`() =
        runTest {
            coEvery { durationResolver.resolveDurationMs(any()) } returns null

            val result = saver().save("/files/audio_notes/b.m4a", recorderElapsed = 9.seconds)

            assertEquals(9_000L, (result as AudioSaveResult.Saved).note.durationMs)
        }

    @Test
    fun `a recording under half a second is too short and both of its files go`() =
        runTest {
            val (finished, raw) = recordingWithRawFile("blip")
            coEvery { durationResolver.resolveDurationMs(any()) } returns 300L

            val result = saver().save(finished.path, recorderElapsed = 300.milliseconds)

            assertEquals(AudioSaveResult.TooShort, result)
            assertEquals(listOf(finished.path, raw.path), deletedFiles)
            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    @Test
    fun `a saved recording loses its raw file and gets a health annotation`() =
        runTest {
            val (finished, raw) = recordingWithRawFile("saved")

            val result = saver().save(finished.path, recorderElapsed = 4.seconds)

            val note = (result as AudioSaveResult.Saved).note
            assertEquals(listOf(raw.path), deletedFiles)
            coVerify { noteHealthAnnotator.annotate(note.uid) }
        }

    @Test
    fun `a note that cannot be stored leaves both files for another try`() =
        runTest {
            val (finished, _) = recordingWithRawFile("unsaved")
            coEvery { notesRepository.create(any<JournalNote>()) } throws IllegalStateException("database closed")

            val result = saver().save(finished.path, recorderElapsed = 4.seconds)

            assertEquals(AudioSaveResult.Failed(finished.path), result)
            assertTrue(deletedFiles.isEmpty())
        }

    @Test
    fun `the place is saved with the note`() =
        runTest {
            val location = NoteLocation(coordinates = NoteCoordinates(latitude = 37.77, longitude = -122.41))
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns location

            val result = saver().save("/files/audio_notes/c.m4a", recorderElapsed = 4.seconds)

            assertEquals(location, (result as AudioSaveResult.Saved).note.location)
        }

    @Test
    fun `a slow place reading does not delay the save past three seconds`() =
        runTest {
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } coAnswers {
                delay(60_000)
                null
            }

            val result = async { saver().save("/files/audio_notes/d.m4a", recorderElapsed = 4.seconds) }
            advanceTimeBy(AudioMomentSaver.LOCATION_TIMEOUT_MS + 100)

            assertTrue(result.isCompleted)
            assertNull((result.await() as AudioSaveResult.Saved).note.location)
        }

    private fun saver(activity: CaptureActivitySource = CaptureActivitySource.None) =
        AudioMomentSaver(
            notesRepository = notesRepository,
            durationResolver = durationResolver,
            noteHealthAnnotator = noteHealthAnnotator,
            locationCaptureCoordinator = locationCaptureCoordinator,
            clock = FixedClock(now),
            deleteFile = { deletedFiles += it },
            captureActivity = activity,
        )

    /** A finished recording and the raw file a crash-safe session leaves beside it, as real files. */
    private fun recordingWithRawFile(name: String): Pair<File, File> {
        val directory = Files.createTempDirectory("saver").toFile().also { it.deleteOnExit() }
        val finished = File(directory, "recording_$name.m4a").apply { writeText("audio") }
        val raw = CrashSafeRecording.inFlightFile(finished).apply { writeText("raw audio") }
        return finished to raw
    }

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }
}
