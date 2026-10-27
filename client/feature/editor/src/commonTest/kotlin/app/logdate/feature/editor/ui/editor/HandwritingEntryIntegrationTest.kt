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
import app.logdate.feature.editor.ui.handwriting.HandwritingPage
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for draft management in the editor.
 * Verifies draft creation, loading, deletion, and state persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HandwritingEntryIntegrationTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var newViewModel: () -> EntryEditorViewModel
    private lateinit var viewModel: EntryEditorViewModel
    private lateinit var entryDraftRepository: FakeEntryDraftRepository
    private lateinit var journalNotesRepository: FakeJournalNotesRepository
    private lateinit var journalContentRepository: FakeJournalContentRepository
    private lateinit var journalRepository: FakeJournalRepository
    private lateinit var draftManager: DraftManager

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        journalNotesRepository = FakeJournalNotesRepository()
        journalContentRepository = FakeJournalContentRepository()
        journalRepository = FakeJournalRepository()
        entryDraftRepository = FakeEntryDraftRepository()

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
        draftManager =
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

        newViewModel = {
            EntryEditorViewModel(
                observeEditorData = observeEditorData,
                saveEntryUseCase = saveEntryUseCase,
                draftManager = draftManager,
                contentLoader = contentLoader,
            )
        }
        viewModel = newViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun inkPage() =
        HandwritingPage(
            height = 1700f,
            strokes =
                listOf(
                    CanvasElement.Ink(
                        Uuid.random(),
                        InkTool.PEN,
                        "#263044",
                        3.5f,
                        listOf(InkPoint(24.5f, 1400.75f, 0.21f), InkPoint(200.25f, 1500.5f, 0.88f)),
                    ),
                ),
        )

    @Test
    fun `leaving as draft and opening in a new editor preserves original strokes`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.HANDWRITING) as HandwritingBlockUiState
            val page = inkPage()
            viewModel.updateBlock(block.copy(page = page))
            advanceUntilIdle()
            assertTrue(viewModel.saveAsDraft(viewModel.editorState.value))
            advanceUntilIdle()
            val draftId = (viewModel.editorState.value.draftState as DraftState.Active).id
            val reopened = newViewModel()
            reopened.loadDraft(draftId)
            advanceUntilIdle()
            val restored =
                reopened.editorState.value.blocks
                    .single() as HandwritingBlockUiState
            assertEquals(block.id, restored.id)
            assertEquals(page, restored.page)
            assertEquals(block.timeZoneId, restored.timeZoneId)
        }

    @Test
    fun `publishing then opening saved entry preserves original strokes`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.HANDWRITING) as HandwritingBlockUiState
            val page = inkPage()
            viewModel.updateBlock(block.copy(page = page))
            advanceUntilIdle()
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()
            assertTrue(viewModel.editorState.value.shouldExit)
            val reopened = newViewModel()
            reopened.loadExistingEntry(block.id)
            advanceUntilIdle()
            val restored =
                reopened.editorState.value.blocks
                    .single() as HandwritingBlockUiState
            assertEquals(block.id, restored.id)
            assertEquals(page, restored.page)
        }

    @Test
    fun `failed draft write keeps original ink and does not authorize leaving`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.HANDWRITING) as HandwritingBlockUiState
            val page = inkPage()
            viewModel.updateBlock(block.copy(page = page))
            advanceUntilIdle()
            entryDraftRepository.createDraftFailure = IllegalStateException("Storage is full")
            assertFalse(viewModel.saveAsDraft(viewModel.editorState.value))
            advanceUntilIdle()
            assertFalse(viewModel.editorState.value.shouldExit)
            assertEquals(
                page,
                (
                    viewModel.editorState.value.blocks
                        .single() as HandwritingBlockUiState
                ).page,
            )
            assertNotNull(viewModel.editorState.value.errorMessage)
        }
}
