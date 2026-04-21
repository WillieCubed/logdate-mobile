package app.logdate.feature.library.ui

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.feature.library.fakes.FakeIndexedMediaRepository
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Unit tests for [LibraryViewModel].
 *
 * Tests the logic for aggregating and grouping indexed media and journal notes
 * into the library view, ensuring that items are correctly prioritized,
 * grouped by date, and categorized by media type.
 */
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
            val notesRepository = FakeJournalNotesRepository()
            val repository = FakeIndexedMediaRepository()
            val viewModel = LibraryViewModel(notesRepository, repository)
            assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun emptyRepositoryProducesEmptyState() =
        runTest(testDispatcher) {
            val notesRepository = FakeJournalNotesRepository(emptyList())
            val repository = FakeIndexedMediaRepository(emptyList())
            val viewModel = LibraryViewModel(notesRepository, repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertIs<LibraryUiState.Empty>(viewModel.uiState.value)
            collectJob.cancel()
        }

    @Test
    fun indexedMediaProducesContentState() =
        runTest(testDispatcher) {
            val media =
                listOf(
                    IndexedMedia.Image(
                        uid = Uuid.random(),
                        uri = "content://media/external/images/1",
                        timestamp = Instant.fromEpochMilliseconds(1710000000000),
                    ),
                    IndexedMedia.Video(
                        uid = Uuid.random(),
                        uri = "content://media/external/video/1",
                        timestamp = Instant.fromEpochMilliseconds(1710100000000),
                        duration = 3.seconds,
                    ),
                )
            val repository = FakeIndexedMediaRepository(media)
            val viewModel = LibraryViewModel(FakeJournalNotesRepository(emptyList()), repository)

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
    fun noteOnlyMediaStillAppearsWhenIndexedStoreIsEmpty() =
        runTest(testDispatcher) {
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = Uuid.random(),
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = "content://media/external/images/note-only",
                    ),
                )
            val viewModel =
                LibraryViewModel(
                    FakeJournalNotesRepository(notes),
                    FakeIndexedMediaRepository(emptyList()),
                )

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<LibraryUiState.Content>(state)
            val firstGroup = state.groups.first()
            val firstItem = firstGroup.items.first()
            assertEquals(1, state.totalCount)
            assertEquals(
                "content://media/external/images/note-only",
                firstItem.uri,
            )
            collectJob.cancel()
        }

    @Test
    fun indexedMediaPreferredOverDuplicateNoteUri() =
        runTest(testDispatcher) {
            val sharedUri = "content://media/external/images/shared"
            val indexedId = Uuid.random()
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = Uuid.random(),
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = sharedUri,
                    ),
                )
            val indexedMedia =
                listOf(
                    IndexedMedia.Image(
                        uid = indexedId,
                        uri = sharedUri,
                        timestamp = Instant.fromEpochMilliseconds(1710100000000),
                    ),
                )
            val viewModel =
                LibraryViewModel(
                    FakeJournalNotesRepository(notes),
                    FakeIndexedMediaRepository(indexedMedia),
                )

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<LibraryUiState.Content>(state)
            val firstGroup = state.groups.first()
            val firstItem = firstGroup.items.first()
            assertEquals(1, state.totalCount)
            assertEquals(
                indexedId,
                firstItem.uid,
            )
            collectJob.cancel()
        }

    @Test
    fun mediaGroupedByMonth() =
        runTest(testDispatcher) {
            val media =
                listOf(
                    IndexedMedia.Image(
                        uid = Uuid.random(),
                        uri = "content://media/external/images/1",
                        timestamp = Instant.fromEpochMilliseconds(1710000000000), // March 2024
                    ),
                    IndexedMedia.Image(
                        uid = Uuid.random(),
                        uri = "content://media/external/images/2",
                        timestamp = Instant.fromEpochMilliseconds(1704067200000), // January 2024
                    ),
                )
            val repository = FakeIndexedMediaRepository(media)
            val viewModel = LibraryViewModel(FakeJournalNotesRepository(emptyList()), repository)

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
            val media =
                listOf(
                    IndexedMedia.Video(
                        uid = Uuid.random(),
                        uri = "content://media/external/video/1",
                        timestamp = Instant.fromEpochMilliseconds(1710000000000),
                        duration = 5.seconds,
                    ),
                )
            val repository = FakeIndexedMediaRepository(media)
            val viewModel = LibraryViewModel(FakeJournalNotesRepository(emptyList()), repository)

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<LibraryUiState.Content>(state)
            assertEquals(true, state.groups[0].items[0].isVideo)
            collectJob.cancel()
        }
}
