package app.logdate.feature.editor.ui.editor

import app.logdate.client.domain.editor.ObserveEditorDataUseCase
import app.logdate.client.domain.editor.SaveEntryUseCase
import app.logdate.client.domain.journals.GetCurrentUserJournalsUseCase
import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.client.domain.location.LocationRetryWorker
import app.logdate.client.domain.location.LogCurrentLocationUseCase
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.FetchEntryUseCase
import app.logdate.client.domain.notes.FetchTodayNotesUseCase
import app.logdate.client.domain.notes.drafts.CleanupExpiredDraftsUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.notes.drafts.GetAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.domain.world.LogLocationUseCase
import app.logdate.feature.editor.ui.editor.delegate.ContentLoader
import app.logdate.feature.editor.ui.editor.delegate.DraftManager
import app.logdate.feature.editor.ui.editor.fakes.FakeActivityTimelineRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeClientLocationProvider
import app.logdate.feature.editor.ui.editor.fakes.FakeEntryDraftRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalContentRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalNotesRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeLocationHistoryRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeLocationTrackingSettingsRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeMediaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class EditorInputGenerationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var viewModel: EntryEditorViewModel
    private lateinit var journalNotesRepository: FakeJournalNotesRepository
    private lateinit var journalContentRepository: FakeJournalContentRepository

    @Test
    fun `text commit acknowledges exact revision before published state catches up`() =
        testScope.runTest {
            val block = TextBlockUiState()
            val token = Uuid.random()
            viewModel.mutableEditorState.value =
                EditorState(blocks = listOf(block), creationInputTokens = mapOf(block.id to token), contentRevision = 4)
            advanceUntilIdle()
            assertEquals(5L, viewModel.commitTextInput(block.id, token, "A"))
            assertEquals(6L, viewModel.commitTextInput(block.id, token, "A quiet"))
            assertEquals(7L, viewModel.commitTextInput(block.id, token, "A"))
            assertEquals(
                "A",
                (
                    viewModel.mutableEditorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
            assertEquals(7L, viewModel.commitTextInput(block.id, token, "A"))
            assertTrue(viewModel.mutableEditorState.value.isModified)
        }

    @Test
    fun `rejected text input cannot alter another generation or loading document`() =
        testScope.runTest {
            val block = TextBlockUiState(content = "Retain")
            val token = Uuid.random()
            val initial = EditorState(blocks = listOf(block), creationInputTokens = mapOf(block.id to token), contentRevision = 4)
            viewModel.mutableEditorState.value = initial
            advanceUntilIdle()
            assertEquals(null, viewModel.commitTextInput(block.id, Uuid.random(), "Stale"))
            assertSame(initial, viewModel.mutableEditorState.value)
            val loading = initial.copy(isLoading = true)
            viewModel.mutableEditorState.value = loading
            assertEquals(null, viewModel.commitTextInput(block.id, token, "Wrong load"))
            assertSame(loading, viewModel.mutableEditorState.value)
        }

    @Test
    fun `stale focus completion cannot remove a new input with the same memory identity`() =
        testScope.runTest {
            val block = TextBlockUiState()
            val old = Uuid.random()
            val current = Uuid.random()
            val state = EditorState(blocks = listOf(block), expandedBlockId = block.id, creationInputTokens = mapOf(block.id to current))
            viewModel.mutableEditorState.value = state
            assertFalse(viewModel.dismissOwnedEditorFocus(block.id, old))
            assertSame(state, viewModel.mutableEditorState.value)
        }

    @Test
    fun `matching focus completion retains meaningful content without dirtying its revision`() =
        testScope.runTest {
            val block = TextBlockUiState(content = "Retain this input")
            val token = Uuid.random()
            val state =
                EditorState(
                    blocks = listOf(block),
                    expandedBlockId = block.id,
                    creationInputTokens = mapOf(block.id to token),
                    contentRevision = 7,
                )
            viewModel.mutableEditorState.value = state
            assertTrue(viewModel.dismissOwnedEditorFocus(block.id, token))
            assertEquals(null, viewModel.mutableEditorState.value.expandedBlockId)
            assertEquals(state.blocks, viewModel.mutableEditorState.value.blocks)
            assertEquals(7, viewModel.mutableEditorState.value.contentRevision)
            assertEquals(state.isDirty, viewModel.mutableEditorState.value.isDirty)
        }

    @Test
    fun `pending capture ownership refuses focus completion without removing the empty target`() =
        testScope.runTest {
            val block = AudioBlockUiState()
            val token = Uuid.random()
            val state =
                EditorState(
                    blocks = listOf(block),
                    expandedBlockId = block.id,
                    creationInputTokens = mapOf(block.id to token),
                    audioCaptureStartReservations = mapOf(block.id to Uuid.random()),
                )
            viewModel.mutableEditorState.value = state
            assertFalse(viewModel.dismissOwnedEditorFocus(block.id, token))
            assertSame(state, viewModel.mutableEditorState.value)
        }

    @Test
    fun `callbacks from an earlier mode cannot edit the same memory after reversal`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            val previousInput = Uuid.random()
            val currentInput = Uuid.random()
            viewModel.mutableEditorState.value =
                viewModel.mutableEditorState.value.copy(
                    creationInputTokens = mapOf(block.id to currentInput),
                )
            viewModel.updateBlockFromInput(block.copy(content = "Stale input"), block, previousInput)
            advanceUntilIdle()
            assertEquals(
                "",
                (
                    viewModel.editorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
            viewModel.updateBlockFromInput(block.copy(content = "Current input"), block, currentInput)
            advanceUntilIdle()
            assertEquals(
                "Current input",
                (
                    viewModel.editorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
        }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        journalNotesRepository = FakeJournalNotesRepository()
        journalContentRepository = FakeJournalContentRepository()
        viewModel = createViewModel()
    }

    private fun createViewModel(): EntryEditorViewModel {
        val journalRepository = FakeJournalRepository()
        val entryDraftRepository = FakeEntryDraftRepository()

        val locationProvider = FakeClientLocationProvider()
        val activityTimelineRepository = FakeActivityTimelineRepository()
        val locationHistoryRepository = FakeLocationHistoryRepository()
        val locationRetryWorker =
            LocationRetryWorker(
                locationProvider = locationProvider,
                locationHistoryRepository = locationHistoryRepository,
                coroutineScope = testScope.backgroundScope,
            )
        val logLocationUseCase = LogLocationUseCase(locationProvider, activityTimelineRepository)
        val logCurrentLocationUseCase =
            LogCurrentLocationUseCase(
                locationProvider = locationProvider,
                locationHistoryRepository = locationHistoryRepository,
                locationRetryWorker = locationRetryWorker,
                canonicalOwnerProvider = TestCanonicalOwnerProvider(),
                deviceIdProvider = TestDeviceIdProvider(),
            )
        val mediaManager = FakeMediaManager()

        val addNoteUseCase =
            AddNoteUseCase(
                repository = journalNotesRepository,
                journalContentRepository = journalContentRepository,
                logLocationUseCase = logLocationUseCase,
                logCurrentLocationUseCase = logCurrentLocationUseCase,
                settingsRepository = FakeLocationTrackingSettingsRepository(),
                mediaManager = mediaManager,
            )
        val deleteEntryDraft = DeleteEntryDraftUseCase(entryDraftRepository, journalNotesRepository)

        val observeEditorData =
            ObserveEditorDataUseCase(
                fetchTodayNotes = FetchTodayNotesUseCase(journalNotesRepository),
                getCurrentUserJournals = GetCurrentUserJournalsUseCase(journalRepository),
                fetchMostRecentDraft = FetchMostRecentDraftUseCase(entryDraftRepository),
                getAllDrafts = GetAllDraftsUseCase(entryDraftRepository),
            )
        val saveEntryUseCase =
            SaveEntryUseCase(
                addNoteUseCase = addNoteUseCase,
                deleteEntryDraft = deleteEntryDraft,
            )
        val draftManager =
            DraftManager(
                updateEntryDraft = UpdateEntryDraftUseCase(entryDraftRepository),
                createEntryDraft = CreateEntryDraftUseCase(entryDraftRepository),
                fetchEntryDraft = FetchEntryDraftUseCase(entryDraftRepository),
                deleteEntryDraft = deleteEntryDraft,
                deleteAllDraftsUseCase = DeleteAllDraftsUseCase(entryDraftRepository),
                cleanupExpiredDraftsUseCase = CleanupExpiredDraftsUseCase(entryDraftRepository),
            )
        val contentLoader =
            ContentLoader(
                fetchEntryUseCase = FetchEntryUseCase(journalNotesRepository),
                getDefaultSelectedJournals =
                    GetDefaultSelectedJournalsUseCase(
                        journalNotesRepository,
                        journalContentRepository,
                    ),
            )

        return EntryEditorViewModel(
            observeEditorData = observeEditorData,
            saveEntryUseCase = saveEntryUseCase,
            draftManager = draftManager,
            contentLoader = contentLoader,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
