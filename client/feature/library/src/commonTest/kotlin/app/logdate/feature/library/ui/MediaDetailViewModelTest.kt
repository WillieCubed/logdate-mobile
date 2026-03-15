package app.logdate.feature.library.ui

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.feature.library.fakes.FakeJournalNotesRepository
import app.logdate.feature.library.ui.detail.MediaDetailUiState
import app.logdate.feature.library.ui.detail.MediaDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MediaDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun missingNoteProducesError() =
        runTest(testDispatcher) {
            val repository = FakeJournalNotesRepository(emptyList())
            val noteId = Uuid.random()
            val viewModel = MediaDetailViewModel(noteId, repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertIs<MediaDetailUiState.Error>(viewModel.uiState.value)
            collectJob.cancel()
        }

    @Test
    fun imageNoteProducesImageContent() =
        runTest(testDispatcher) {
            val noteId = Uuid.random()
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = noteId,
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = "content://media/external/images/1",
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel = MediaDetailViewModel(noteId, repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<MediaDetailUiState.ImageContent>(state)
            assertEquals("content://media/external/images/1", state.mediaRef)
            assertEquals(noteId, state.noteId)
            collectJob.cancel()
        }

    @Test
    fun videoNoteProducesVideoContent() =
        runTest(testDispatcher) {
            val noteId = Uuid.random()
            val notes =
                listOf(
                    JournalNote.Video(
                        uid = noteId,
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = "content://media/external/video/1",
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel = MediaDetailViewModel(noteId, repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<MediaDetailUiState.VideoContent>(state)
            assertEquals("content://media/external/video/1", state.mediaRef)
            collectJob.cancel()
        }

    @Test
    fun locationDataIncludedWhenPresent() =
        runTest(testDispatcher) {
            val noteId = Uuid.random()
            val location =
                NoteLocation(
                    coordinates = NoteCoordinates(latitude = 37.7749, longitude = -122.4194),
                )
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = noteId,
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = "content://media/external/images/1",
                        location = location,
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel = MediaDetailViewModel(noteId, repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<MediaDetailUiState.ImageContent>(state)
            assertEquals(37.7749, state.location?.coordinates?.latitude)
            collectJob.cancel()
        }

    @Test
    fun textNoteProducesError() =
        runTest(testDispatcher) {
            val noteId = Uuid.random()
            val notes =
                listOf(
                    JournalNote.Text(
                        uid = noteId,
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        content = "Not a photo",
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel = MediaDetailViewModel(noteId, repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertIs<MediaDetailUiState.Error>(viewModel.uiState.value)
            collectJob.cancel()
        }
}
