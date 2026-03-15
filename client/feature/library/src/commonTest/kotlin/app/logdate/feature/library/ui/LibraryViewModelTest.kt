package app.logdate.feature.library.ui

import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.library.fakes.FakeJournalNotesRepository
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
class LibraryViewModelTest {
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
    fun initialStateIsLoading() =
        runTest(testDispatcher) {
            val repository = FakeJournalNotesRepository()
            val viewModel = LibraryViewModel(repository)
            assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun emptyRepositoryProducesEmptyState() =
        runTest(testDispatcher) {
            val repository = FakeJournalNotesRepository(emptyList())
            val viewModel = LibraryViewModel(repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertIs<LibraryUiState.Empty>(viewModel.uiState.value)
            collectJob.cancel()
        }

    @Test
    fun textOnlyNotesProduceEmptyState() =
        runTest(testDispatcher) {
            val notes =
                listOf(
                    JournalNote.Text(
                        uid = Uuid.random(),
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        content = "Hello world",
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel = LibraryViewModel(repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertIs<LibraryUiState.Empty>(viewModel.uiState.value)
            collectJob.cancel()
        }

    @Test
    fun imageNotesProduceContentState() =
        runTest(testDispatcher) {
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = Uuid.random(),
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = "content://media/external/images/1",
                    ),
                    JournalNote.Video(
                        uid = Uuid.random(),
                        creationTimestamp = Instant.fromEpochMilliseconds(1710100000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710100000000),
                        mediaRef = "content://media/external/video/1",
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel = LibraryViewModel(repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<LibraryUiState.Content>(state)
            assertEquals(2, state.totalCount)
            assertEquals(1, state.groups.size)
            assertEquals(2, state.groups[0].items.size)
            collectJob.cancel()
        }

    @Test
    fun mediaGroupedByMonth() =
        runTest(testDispatcher) {
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = Uuid.random(),
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000), // March 2024
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = "content://media/external/images/1",
                    ),
                    JournalNote.Image(
                        uid = Uuid.random(),
                        creationTimestamp = Instant.fromEpochMilliseconds(1704067200000), // January 2024
                        lastUpdated = Instant.fromEpochMilliseconds(1704067200000),
                        mediaRef = "content://media/external/images/2",
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel = LibraryViewModel(repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<LibraryUiState.Content>(state)
            assertEquals(2, state.groups.size)
            collectJob.cancel()
        }

    @Test
    fun videoItemsMarkedAsVideo() =
        runTest(testDispatcher) {
            val notes =
                listOf(
                    JournalNote.Video(
                        uid = Uuid.random(),
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = "content://media/external/video/1",
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel = LibraryViewModel(repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<LibraryUiState.Content>(state)
            assertEquals(true, state.groups[0].items[0].isVideo)
            collectJob.cancel()
        }
}
