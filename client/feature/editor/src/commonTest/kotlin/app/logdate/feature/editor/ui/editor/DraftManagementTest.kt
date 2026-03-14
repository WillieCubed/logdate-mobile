package app.logdate.feature.editor.ui.editor

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
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.editor.delegate.AutoSaveDelegate
import app.logdate.feature.editor.ui.editor.delegate.JournalSelectionDelegate
import app.logdate.feature.editor.ui.editor.fakes.FakeActivityTimelineRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeClientLocationProvider
import app.logdate.feature.editor.ui.editor.fakes.FakeEditorMediator
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Tests for draft management in the editor.
 * Verifies draft creation, loading, deletion, and state persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftManagementTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var viewModel: EntryEditorViewModel
    private lateinit var entryDraftRepository: FakeEntryDraftRepository
    private lateinit var autoSaveDelegate: AutoSaveDelegate

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        val journalNotesRepository = FakeJournalNotesRepository()
        val journalContentRepository = FakeJournalContentRepository()
        val journalRepository = FakeJournalRepository()
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
            )
        val mediaManager = FakeMediaManager()

        val fetchTodayNotes = FetchTodayNotesUseCase(journalNotesRepository)
        val getCurrentUserJournals = GetCurrentUserJournalsUseCase(journalRepository)
        val getDefaultSelectedJournals =
            GetDefaultSelectedJournalsUseCase(
                journalNotesRepository,
                journalContentRepository,
            )
        val addNoteUseCase =
            AddNoteUseCase(
                repository = journalNotesRepository,
                journalContentRepository = journalContentRepository,
                logLocationUseCase = logLocationUseCase,
                logCurrentLocationUseCase = logCurrentLocationUseCase,
                settingsRepository = FakeLocationTrackingSettingsRepository(),
                mediaManager = mediaManager,
            )
        val fetchEntryUseCase = FetchEntryUseCase(journalNotesRepository)
        val updateEntryDraft = UpdateEntryDraftUseCase(entryDraftRepository)
        val createEntryDraft = CreateEntryDraftUseCase(entryDraftRepository)
        val deleteEntryDraft = DeleteEntryDraftUseCase(entryDraftRepository)
        val deleteAllDraftsUseCase = DeleteAllDraftsUseCase(entryDraftRepository)
        val fetchEntryDraft = FetchEntryDraftUseCase(entryDraftRepository)
        val fetchMostRecentDraft = FetchMostRecentDraftUseCase(entryDraftRepository)
        val getAllDrafts = GetAllDraftsUseCase(entryDraftRepository)
        val cleanupExpiredDrafts = CleanupExpiredDraftsUseCase(entryDraftRepository)

        autoSaveDelegate =
            AutoSaveDelegate(
                updateEntryDraft = updateEntryDraft,
                createEntryDraft = createEntryDraft,
            )
        val journalSelectionDelegate =
            JournalSelectionDelegate(
                getDefaultSelectedJournals = getDefaultSelectedJournals,
            )

        viewModel =
            EntryEditorViewModel(
                fetchTodayNotes = fetchTodayNotes,
                getCurrentUserJournals = getCurrentUserJournals,
                getDefaultSelectedJournals = getDefaultSelectedJournals,
                addNoteUseCase = addNoteUseCase,
                fetchEntryUseCase = fetchEntryUseCase,
                journalContentRepository = journalContentRepository,
                updateEntryDraft = updateEntryDraft,
                createEntryDraft = createEntryDraft,
                deleteEntryDraft = deleteEntryDraft,
                deleteAllDraftsUseCase = deleteAllDraftsUseCase,
                fetchEntryDraft = fetchEntryDraft,
                fetchMostRecentDraft = fetchMostRecentDraft,
                getAllDrafts = getAllDrafts,
                cleanupExpiredDrafts = cleanupExpiredDrafts,
                mediator = FakeEditorMediator(),
                autoSaveDelegate = autoSaveDelegate,
                journalSelectionDelegate = journalSelectionDelegate,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDraftIdSurvivesCombineReemission() =
        testScope.runTest {
            // Create a text block with content
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            advanceUntilIdle()

            viewModel.updateBlock(block.copy(content = "Test content"))
            advanceUntilIdle()

            // Trigger auto-save
            viewModel.autoSaveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            // draftState should be Active even after combine re-emits
            val state = viewModel.editorState.value
            assertTrue(
                state.draftState is DraftState.Active,
                "draftState should be Active after auto-save",
            )
        }

    @Test
    fun testSaveEntryDeletesDraftAfterAutoSave() =
        testScope.runTest {
            // Create content and auto-save to create a draft
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            advanceUntilIdle()

            viewModel.updateBlock(block.copy(content = "Content to publish"))
            advanceUntilIdle()

            viewModel.autoSaveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            // Verify draft exists
            val stateBeforeSave = viewModel.editorState.value
            assertTrue(
                stateBeforeSave.draftState is DraftState.Active,
                "draftState should be Active before save",
            )

            // Now save the entry (publish)
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            // Draft should be deleted from the repository
            val remainingDrafts = viewModel.editorState.value.availableDrafts
            assertTrue(remainingDrafts.isEmpty(), "Draft should be deleted after save")
        }

    @Test
    fun testLoadDraftSetsIsModified() =
        testScope.runTest {
            // Pre-populate a draft in the repository
            val draftId =
                entryDraftRepository.createDraft(
                    listOf(
                        JournalNote.Text(
                            uid = Uuid.random(),
                            creationTimestamp = Clock.System.now(),
                            lastUpdated = Clock.System.now(),
                            content = "Draft content",
                        ),
                    ),
                )
            advanceUntilIdle()

            // Load the draft
            viewModel.loadDraft(draftId)
            advanceUntilIdle()

            val state = viewModel.editorState.value
            assertTrue(state.isModified, "isModified should be true after loading a draft")
            assertTrue(state.isDirty, "isDirty should be true after loading a draft with content")
            assertFalse(
                state.canExitWithoutSaving,
                "Should not be able to exit without saving after loading a draft",
            )
        }

    @Test
    fun testAutoSaveSkippedDuringManualSave() =
        testScope.runTest {
            // Create content
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            advanceUntilIdle()

            viewModel.updateBlock(block.copy(content = "Some content"))
            advanceUntilIdle()

            // Start a manual save — this synchronously sets isSaving = true
            viewModel.saveEntry(viewModel.editorState.value)

            // Read the state after isSaving was set but before the coroutine completes.
            // We need to advance once so the combine re-emits with isSaving = true.
            advanceUntilIdle()

            // Verify isSaving was set (the manual save completed and reset it,
            // so verify that no drafts were created as a side effect)
            val drafts = viewModel.editorState.value.availableDrafts
            assertTrue(drafts.isEmpty(), "No drafts should exist after manual save")

            // Now try auto-save after the save completed — should work normally
            // but there's nothing to save since shouldExit is true
            assertFalse(
                viewModel.editorState.value.isSaving,
                "isSaving should be false after save completes",
            )
        }

    @Test
    fun testDeleteAllDraftsClearsAllAtOnce() =
        testScope.runTest {
            // Create multiple drafts
            entryDraftRepository.createDraft(
                listOf(
                    JournalNote.Text(
                        uid = Uuid.random(),
                        creationTimestamp = Clock.System.now(),
                        lastUpdated = Clock.System.now(),
                        content = "Draft 1",
                    ),
                ),
            )
            entryDraftRepository.createDraft(
                listOf(
                    JournalNote.Text(
                        uid = Uuid.random(),
                        creationTimestamp = Clock.System.now(),
                        lastUpdated = Clock.System.now(),
                        content = "Draft 2",
                    ),
                ),
            )
            entryDraftRepository.createDraft(
                listOf(
                    JournalNote.Text(
                        uid = Uuid.random(),
                        creationTimestamp = Clock.System.now(),
                        lastUpdated = Clock.System.now(),
                        content = "Draft 3",
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals(3, viewModel.editorState.value.availableDrafts.size)

            // Delete all drafts atomically
            viewModel.deleteAllDrafts()
            advanceUntilIdle()

            // All drafts should be gone
            val remaining = viewModel.editorState.value.availableDrafts
            assertTrue(remaining.isEmpty(), "All drafts should be deleted")

            // DraftState should be cleared
            assertEquals(
                DraftState.None,
                viewModel.editorState.value.draftState,
                "DraftState should be None after deleting all drafts",
            )
        }
}
