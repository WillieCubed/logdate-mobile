package app.logdate.feature.journals.ui.detail

import app.logdate.client.domain.notes.RemoveNoteUseCase
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val journalRepository = FakeDetailJournalRepository()
    private val journalContentRepository = FakeDetailJournalContentRepository()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun mapsTextNoteToContentState() =
        runTest(dispatcher) {
            val noteId = Uuid.random()
            val now = Instant.parse("2025-01-01T00:00:00Z")
            val note =
                JournalNote.Text(
                    uid = noteId,
                    creationTimestamp = now,
                    lastUpdated = now,
                    content = "Hello",
                )
            val repository = FakeJournalNotesRepository(listOf(note))
            val viewModel =
                NoteViewerViewModel(
                    noteId = noteId,
                    journalId = null,
                    notesRepository = repository,
                    journalRepository = journalRepository,
                    journalContentRepository = journalContentRepository,
                    removeNoteUseCase = RemoveNoteUseCase(repository),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            val content = assertIs<NoteViewerUiState.TextContent>(state)
            assertEquals("Hello", content.text)
            assertEquals(noteId, content.shared.noteId)
        }

    @Test
    fun mapsImageNoteToContentState() =
        runTest(dispatcher) {
            val noteId = Uuid.random()
            val now = Instant.parse("2025-01-01T00:00:00Z")
            val note =
                JournalNote.Image(
                    uid = noteId,
                    creationTimestamp = now,
                    lastUpdated = now,
                    mediaRef = "image://test",
                )
            val repository = FakeJournalNotesRepository(listOf(note))
            val viewModel =
                NoteViewerViewModel(
                    noteId = noteId,
                    journalId = null,
                    notesRepository = repository,
                    journalRepository = journalRepository,
                    journalContentRepository = journalContentRepository,
                    removeNoteUseCase = RemoveNoteUseCase(repository),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            val content = assertIs<NoteViewerUiState.ImageContent>(state)
            assertEquals("image://test", content.mediaRef)
        }

    @Test
    fun mapsVideoNoteToContentState() =
        runTest(dispatcher) {
            val noteId = Uuid.random()
            val now = Instant.parse("2025-01-01T00:00:00Z")
            val note =
                JournalNote.Video(
                    uid = noteId,
                    creationTimestamp = now,
                    lastUpdated = now,
                    mediaRef = "video://test",
                )
            val repository = FakeJournalNotesRepository(listOf(note))
            val viewModel =
                NoteViewerViewModel(
                    noteId = noteId,
                    journalId = null,
                    notesRepository = repository,
                    journalRepository = journalRepository,
                    journalContentRepository = journalContentRepository,
                    removeNoteUseCase = RemoveNoteUseCase(repository),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            val content = assertIs<NoteViewerUiState.VideoContent>(state)
            assertEquals("video://test", content.mediaRef)
        }

    @Test
    fun mapsAudioNoteToContentState() =
        runTest(dispatcher) {
            val noteId = Uuid.random()
            val now = Instant.parse("2025-01-01T00:00:00Z")
            val note =
                JournalNote.Audio(
                    mediaRef = "audio://test",
                    durationMs = 1234L,
                    uid = noteId,
                    creationTimestamp = now,
                    lastUpdated = now,
                )
            val repository = FakeJournalNotesRepository(listOf(note))
            val viewModel =
                NoteViewerViewModel(
                    noteId = noteId,
                    journalId = null,
                    notesRepository = repository,
                    journalRepository = journalRepository,
                    journalContentRepository = journalContentRepository,
                    removeNoteUseCase = RemoveNoteUseCase(repository),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            val content = assertIs<NoteViewerUiState.AudioContent>(state)
            assertEquals("audio://test", content.mediaRef)
            assertEquals(1234L, content.durationMs)
        }

    @Test
    fun deleteNoteInvokesUseCase() =
        runTest(dispatcher) {
            val noteId = Uuid.random()
            val now = Instant.parse("2025-01-01T00:00:00Z")
            val note =
                JournalNote.Text(
                    uid = noteId,
                    creationTimestamp = now,
                    lastUpdated = now,
                    content = "Delete me",
                )
            val repository = FakeJournalNotesRepository(listOf(note))
            val viewModel =
                NoteViewerViewModel(
                    noteId = noteId,
                    journalId = null,
                    notesRepository = repository,
                    journalRepository = journalRepository,
                    journalContentRepository = journalContentRepository,
                    removeNoteUseCase = RemoveNoteUseCase(repository),
                )

            var deleted = false
            viewModel.deleteNote { deleted = true }
            advanceUntilIdle()

            assertTrue(deleted)
            assertEquals(noteId, repository.lastRemovedId)
        }
}
