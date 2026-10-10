package app.logdate.wear.recording

import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.capture.Feeling
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [AfterSaveController]: the seconds after a save, what Undo does in them, and the five feelings. */
@OptIn(ExperimentalCoroutinesApi::class)
class AfterSaveControllerTest {
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val removedNotes = mutableListOf<Uuid>()
    private val deletedFiles = mutableListOf<String>()
    private val savedFeelings = mutableListOf<Feeling>()
    private var feelingSaves = true
    private val noteId = Uuid.random()

    @Test
    fun `a window opens for the saved note and closes after five seconds`() =
        runTest {
            val controller = controller()

            controller.open(noteId)
            advanceTimeBy(AfterSaveController.UNDO_WINDOW_MS - 1)
            runCurrent()
            assertEquals(AfterSaveWindow(noteId), controller.window.value)

            advanceTimeBy(2)
            runCurrent()
            assertNull(controller.window.value)
        }

    @Test
    fun `opening again restarts the five seconds`() =
        runTest {
            val controller = controller()
            controller.open(noteId)
            advanceTimeBy(4_000)
            val second = Uuid.random()

            controller.open(second)
            advanceTimeBy(4_000)
            runCurrent()

            assertEquals(AfterSaveWindow(second), controller.window.value)
        }

    @Test
    fun `closing ends the window and leaves the note alone`() =
        runTest {
            val controller = controller()
            controller.open(noteId)

            controller.close()

            assertNull(controller.window.value)
            coVerify(exactly = 0) { notesRepository.removeById(any()) }
        }

    @Test
    fun `undo removes the note and its audio and tells the phone`() =
        runTest {
            coEvery { notesRepository.getNoteById(noteId) } returnsMany listOf(audio("/files/audio_notes/u.m4a"), null)
            val controller = controller()
            controller.open(noteId)

            assertTrue(controller.undo(noteId))

            coVerify { notesRepository.removeById(noteId) }
            assertEquals(listOf("/files/audio_notes/u.m4a"), deletedFiles)
            assertEquals(listOf(noteId), removedNotes)
            assertNull(controller.window.value)
        }

    @Test
    fun `undo leaves the audio while a note still refers to it`() =
        runTest {
            coEvery { notesRepository.getNoteById(noteId) } returns audio("/files/audio_notes/shared.m4a")

            assertTrue(controller().undo(noteId))

            assertTrue(deletedFiles.isEmpty())
        }

    @Test
    fun `a removal that fails reports false and tells the phone nothing`() =
        runTest {
            coEvery { notesRepository.removeById(noteId) } throws IllegalStateException("database closed")

            assertFalse(controller().undo(noteId))

            assertTrue(removedNotes.isEmpty())
        }

    @Test
    fun `undo deletes the file a portable media reference names`() =
        runTest {
            coEvery { notesRepository.getNoteById(noteId) } returnsMany
                listOf(audio("logdate-media://recordings/recording_u.m4a"), null)

            assertTrue(controller(mediaFiles = mediaFiles()).undo(noteId))

            assertEquals(listOf("/files/audio_notes/recording_u.m4a"), deletedFiles)
            assertEquals(listOf(noteId), removedNotes)
        }

    @Test
    fun `undo deletes a path stored before portable references`() =
        runTest {
            coEvery { notesRepository.getNoteById(noteId) } returnsMany
                listOf(audio("/files/audio_notes/recording_old.m4a"), null)

            assertTrue(controller(mediaFiles = mediaFiles()).undo(noteId))

            assertEquals(listOf("/files/audio_notes/recording_old.m4a"), deletedFiles)
        }

    @Test
    fun `undo removes a note whose audio is not a local file and deletes nothing`() =
        runTest {
            coEvery { notesRepository.getNoteById(noteId) } returnsMany
                listOf(audio("content://media/external/audio/media/7"), null)

            assertTrue(controller(mediaFiles = mediaFiles()).undo(noteId))

            assertTrue(deletedFiles.isEmpty())
            assertEquals(listOf(noteId), removedNotes)
        }

    @Test
    fun `offered feelings go after four seconds and Undo stays until five`() =
        runTest {
            val controller = controller()

            controller.open(noteId, offerFeelings = true)
            assertEquals(AfterSaveWindow(noteId, feelingsOffered = true), controller.window.value)
            advanceTimeBy(AfterSaveController.FEELINGS_WINDOW_MS + 1)
            runCurrent()

            assertEquals(AfterSaveWindow(noteId, feelingsOffered = false), controller.window.value)
        }

    @Test
    fun `choosing a feeling saves a check-in and shows which`() =
        runTest {
            val controller = controller()
            controller.open(noteId, offerFeelings = true)

            assertTrue(controller.chooseFeeling(Feeling.GOOD))

            assertEquals(listOf(Feeling.GOOD), savedFeelings)
            assertEquals(AfterSaveWindow(noteId, feelingsOffered = false, feeling = Feeling.GOOD), controller.window.value)
        }

    @Test
    fun `a feeling that cannot be saved leaves the five feelings offered`() =
        runTest {
            feelingSaves = false
            val controller = controller()
            controller.open(noteId, offerFeelings = true)

            assertFalse(controller.chooseFeeling(Feeling.GOOD))

            assertEquals(AfterSaveWindow(noteId, feelingsOffered = true), controller.window.value)
        }

    @Test
    fun `no feeling is saved once the feelings are gone`() =
        runTest {
            val controller = controller()
            controller.open(noteId, offerFeelings = true)
            advanceTimeBy(AfterSaveController.FEELINGS_WINDOW_MS + 1)
            runCurrent()

            assertFalse(controller.chooseFeeling(Feeling.GOOD))

            assertTrue(savedFeelings.isEmpty())
        }

    private fun TestScope.controller(mediaFiles: MediaFileResolver? = null) =
        AfterSaveController(
            scope = backgroundScope,
            notesRepository = notesRepository,
            removalNotifier = { removedNotes += it },
            deleteFile = { deletedFiles += it },
            mediaFiles = mediaFiles,
            feelingSaver = { feeling ->
                if (feelingSaves) savedFeelings += feeling
                feelingSaves
            },
        )

    private fun mediaFiles() =
        MediaFileResolver(
            directories =
                object : MediaDirectories {
                    override fun directory(collection: MediaCollection): String =
                        when (collection) {
                            MediaCollection.Library -> "/files/media"
                            MediaCollection.Recordings -> "/files/audio_notes"
                        }
                },
            fileExists = { true },
        )

    private fun audio(path: String): JournalNote.Audio {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        return JournalNote.Audio(mediaRef = path, uid = noteId, creationTimestamp = now, lastUpdated = now, durationMs = 4_200)
    }
}
