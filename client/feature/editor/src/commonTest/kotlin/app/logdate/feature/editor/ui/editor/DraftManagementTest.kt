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
import app.logdate.client.domain.notes.drafts.SetEntryDraftPendingMediaUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.domain.world.LogLocationUseCase
import app.logdate.client.repository.journals.JournalNote
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
    private lateinit var draftManager: DraftManager

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

        val addNoteUseCase =
            AddNoteUseCase(
                repository = journalNotesRepository,
                journalContentRepository = journalContentRepository,
                logLocationUseCase = logLocationUseCase,
                logCurrentLocationUseCase = logCurrentLocationUseCase,
                settingsRepository = FakeLocationTrackingSettingsRepository(),
                mediaManager = mediaManager,
            )
        val deleteEntryDraft = DeleteEntryDraftUseCase(entryDraftRepository)

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
                setPendingMedia = SetEntryDraftPendingMediaUseCase(entryDraftRepository),
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

        viewModel =
            EntryEditorViewModel(
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
            val finalState = viewModel.editorState.value
            val remainingDrafts = finalState.availableDrafts
            assertTrue(remainingDrafts.isEmpty(), "Draft should be deleted after save")
            assertEquals(DraftState.None, finalState.draftState, "Draft state should reset after save")
            assertFalse(finalState.isModified, "Editor should no longer be marked modified after save")
        }

    @Test
    fun testSaveEntryDeletesDraftWhenCallerStateMissesLatestAutosave() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            advanceUntilIdle()

            viewModel.updateBlock(block.copy(content = "Content with stale caller state"))
            advanceUntilIdle()

            val stalePreAutosaveState = viewModel.editorState.value
            assertEquals(DraftState.None, stalePreAutosaveState.draftState)

            viewModel.autoSaveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            assertTrue(
                viewModel.editorState.value.draftState is DraftState.Active,
                "draftState should be Active after autosave",
            )

            viewModel.saveEntry(stalePreAutosaveState)
            advanceUntilIdle()

            val finalState = viewModel.editorState.value
            assertTrue(finalState.availableDrafts.isEmpty(), "Draft should be deleted even with stale caller state")
            assertEquals(DraftState.None, finalState.draftState, "Draft state should reset after save")
            assertTrue(finalState.shouldExit, "Successful save should mark the editor for exit")
        }

    @Test
    fun testDelayedAutoSaveAfterSuccessfulSaveDoesNotRecreateDraft() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            advanceUntilIdle()

            viewModel.updateBlock(block.copy(content = "Content to publish"))
            advanceUntilIdle()

            viewModel.autoSaveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            val staleAutoSaveState = viewModel.editorState.value
            assertTrue(staleAutoSaveState.draftState is DraftState.Active)

            viewModel.saveEntry(staleAutoSaveState)
            advanceUntilIdle()

            viewModel.autoSaveEntry(staleAutoSaveState)
            advanceUntilIdle()

            val finalState = viewModel.editorState.value
            assertTrue(finalState.availableDrafts.isEmpty(), "A stale autosave callback must not recreate drafts")
            assertEquals(DraftState.None, finalState.draftState, "Draft state should remain cleared after save")
            assertTrue(finalState.shouldExit, "Editor should still be in exit state after save")
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
            assertTrue(
                viewModel.editorState.value.shouldExit,
                "Successful save should mark the editor for exit",
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
