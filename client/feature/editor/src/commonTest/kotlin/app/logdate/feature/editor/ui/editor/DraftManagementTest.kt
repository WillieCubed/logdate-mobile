package app.logdate.feature.editor.ui.editor

import androidx.lifecycle.viewModelScope
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
import app.logdate.shared.model.Journal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    private fun textNote(content: String): JournalNote.Text {
        val now = Clock.System.now()
        return JournalNote.Text(
            creationTimestamp = now,
            lastUpdated = now,
            content = content,
        )
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
    fun testAutoSaveWaitsForDurableDraftPersistence() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Durable content"))
            advanceUntilIdle()
            val persistenceGate = CompletableDeferred<Unit>()
            entryDraftRepository.createDraftGate = persistenceGate

            val save = async { viewModel.persistDraft(viewModel.editorState.value) }
            runCurrent()

            assertEquals(1, entryDraftRepository.createDraftCallCount)
            assertFalse(save.isCompleted, "Autosave must await the local repository write")
            assertEquals(DraftState.None, viewModel.editorState.value.draftState)

            persistenceGate.complete(Unit)
            val draftId = save.await()
            advanceUntilIdle()

            assertNotNull(draftId)
            assertEquals(DraftState.Active(draftId), viewModel.editorState.value.draftState)
        }

    @Test
    fun testConcurrentAutoSavesPreserveSingleDraftIdentity() =
        testScope.runTest {
            val firstBlock = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(firstBlock.copy(content = "First version"))
            advanceUntilIdle()
            val firstState = viewModel.editorState.value
            val persistenceGate = CompletableDeferred<Unit>()
            entryDraftRepository.createDraftGate = persistenceGate

            val firstSave = async { viewModel.persistDraft(firstState) }
            runCurrent()

            viewModel.updateBlock(firstBlock.copy(content = "Second version"))
            advanceUntilIdle()
            val secondSave = async { viewModel.persistDraft(viewModel.editorState.value) }
            runCurrent()

            persistenceGate.complete(Unit)
            val firstId = firstSave.await()
            val secondId = secondSave.await()
            advanceUntilIdle()

            val drafts = entryDraftRepository.getDrafts().first()
            assertNotNull(firstId)
            assertEquals(firstId, secondId)
            assertEquals(1, drafts.size)
            assertEquals(1, entryDraftRepository.createDraftCallCount)
            assertEquals(1, entryDraftRepository.updateDraftCallCount)
        }

    @Test
    fun testClearingActiveDraftPersistsEmptyContent() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Content that will be cleared"))
            advanceUntilIdle()
            val draftId = viewModel.persistDraft(viewModel.editorState.value)
            advanceUntilIdle()
            assertNotNull(draftId)

            viewModel.removeBlock(block.id)
            advanceUntilIdle()
            val returnedId = viewModel.persistDraft(viewModel.editorState.value)
            advanceUntilIdle()

            val storedDraft = entryDraftRepository.getDraft(draftId).first().getOrThrow()
            assertEquals(draftId, returnedId)
            assertTrue(storedDraft.notes.isEmpty(), "Clearing the editor must clear the durable draft")
            assertTrue(storedDraft.pendingMedia.isEmpty())
        }

    @Test
    fun testClearingActiveDraftSoleBlockCannotResetToPickerBeforeEmptySnapshotPersists() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Content that must be cleared durably"))
            advanceUntilIdle()
            val draftId = assertNotNull(viewModel.persistDraft(viewModel.editorState.value))

            viewModel.updateBlock(block.copy(content = ""))
            advanceUntilIdle()

            assertFalse(
                viewModel.clearSingleEmptyBlock(),
                "An active draft must route Back through save/discard instead of clearing its dirty bit",
            )
            assertTrue(viewModel.editorState.value.isDirty)
            assertFalse(viewModel.editorState.value.canExitWithoutSaving)

            assertEquals(draftId, viewModel.persistDraft(viewModel.editorState.value))
            val storedDraft = entryDraftRepository.getDraft(draftId).first().getOrThrow()
            assertTrue(storedDraft.notes.isEmpty())
        }

    @Test
    fun testSaveAsDraftWaitsForDurableDraftPersistence() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Exit draft"))
            advanceUntilIdle()
            val persistenceGate = CompletableDeferred<Unit>()
            entryDraftRepository.createDraftGate = persistenceGate

            val save = async { viewModel.saveAsDraft(viewModel.editorState.value) }
            runCurrent()

            assertFalse(save.isCompleted, "Explicit draft save must await durable persistence")
            assertFalse(viewModel.editorState.value.shouldExit)

            persistenceGate.complete(Unit)
            assertTrue(save.await())
            advanceUntilIdle()
            assertNull(viewModel.editorState.value.errorMessage)
        }

    @Test
    fun testSaveAsDraftPersistsLatestChangesBeforeReturningSuccess() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Click-time content"))
            advanceUntilIdle()
            val persistenceGate = CompletableDeferred<Unit>()
            entryDraftRepository.createDraftGate = persistenceGate

            val save = async { viewModel.saveAsDraft(viewModel.editorState.value) }
            runCurrent()

            val selectedJournalId = Uuid.random()
            viewModel.updateBlock(block.copy(content = "Latest content while saving"))
            viewModel.setSelectedJournals(listOf(selectedJournalId))
            runCurrent()

            persistenceGate.complete(Unit)
            assertTrue(save.await())
            advanceUntilIdle()

            val stored = entryDraftRepository.getDrafts().first().single()
            val storedText = stored.notes.single() as JournalNote.Text
            assertEquals("Latest content while saving", storedText.content)
            assertEquals(listOf(selectedJournalId), stored.selectedJournalIds)
            assertEquals(1, entryDraftRepository.createDraftCallCount)
            assertEquals(1, entryDraftRepository.updateDraftCallCount)
        }

    @Test
    fun testSaveAsDraftFailureKeepsEditorOpenAndReportsError() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Unsaved exit draft"))
            advanceUntilIdle()
            entryDraftRepository.createDraftFailure = IllegalStateException("disk full")

            val saved = viewModel.saveAsDraft(viewModel.editorState.value)
            advanceUntilIdle()

            assertFalse(saved)
            assertFalse(viewModel.editorState.value.shouldExit)
            assertTrue(
                viewModel.editorState.value.errorMessage
                    ?.contains("disk full") == true,
            )
            assertEquals(DraftState.None, viewModel.editorState.value.draftState)
        }

    @Test
    fun testManualSaveWaitsForInFlightDurableAutoSave() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Publish after durable autosave"))
            advanceUntilIdle()
            val persistenceGate = CompletableDeferred<Unit>()
            entryDraftRepository.createDraftGate = persistenceGate

            val autoSave = async { viewModel.persistDraft(viewModel.editorState.value) }
            runCurrent()
            viewModel.updateBlock(block.copy(content = "Latest content at publish time"))
            runCurrent()
            viewModel.saveEntry(viewModel.editorState.value)
            runCurrent()

            assertFalse(
                viewModel.editorState.value.shouldExit,
                "Publish must wait for an in-flight local draft write",
            )

            persistenceGate.complete(Unit)
            autoSave.await()
            advanceUntilIdle()

            val finalState = viewModel.editorState.value
            assertTrue(finalState.shouldExit)
            assertEquals(DraftState.None, finalState.draftState)
            assertTrue(entryDraftRepository.getDrafts().first().isEmpty())
            val published = journalNotesRepository.allNotesObserved.first().single() as JournalNote.Text
            assertEquals("Latest content at publish time", published.content)
        }

    @Test
    fun testPublishAdmissionRejectsLateTextJournalAndBlockMutations() =
        testScope.runTest {
            val initialJournalId = Uuid.random()
            val rejectedJournalId = Uuid.random()
            val originalBlock = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(originalBlock.copy(content = "Snapshot admitted for publish"))
            viewModel.initializeSelectedJournals(listOf(initialJournalId))
            advanceUntilIdle()
            val publishGate = CompletableDeferred<Unit>()
            journalNotesRepository.createGate = publishGate

            viewModel.saveEntry(viewModel.editorState.value)
            runCurrent()

            viewModel.updateBlock(originalBlock.copy(content = "Late text that must be rejected"))
            viewModel.setSelectedJournals(listOf(rejectedJournalId))
            viewModel.createNewBlock(BlockType.IMAGE, Uuid.random())
            runCurrent()

            publishGate.complete(Unit)
            advanceUntilIdle()

            val finalState = viewModel.editorState.value
            assertTrue(finalState.shouldExit)
            assertEquals(listOf(initialJournalId), finalState.selectedJournalIds)
            assertEquals(1, finalState.blocks.size)
            assertEquals(
                "Snapshot admitted for publish",
                (finalState.blocks.single() as TextBlockUiState).content,
            )
            val published = journalNotesRepository.allNotesObserved.first().single() as JournalNote.Text
            assertEquals("Snapshot admitted for publish", published.content)
        }

    @Test
    fun testPublishDoesNotClearEditorWhenDurableFingerprintChangesDuringSave() =
        testScope.runTest {
            val originalBlock = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(originalBlock.copy(content = "Publish with invariant"))
            advanceUntilIdle()
            val publishGate = CompletableDeferred<Unit>()
            journalNotesRepository.createGate = publishGate

            viewModel.saveEntry(viewModel.editorState.value)
            runCurrent()

            journalRepository.create(
                Journal(
                    id = Uuid.random(),
                    title = "Default appearing during save",
                ),
            )
            runCurrent()
            publishGate.complete(Unit)
            advanceUntilIdle()

            val finalState = viewModel.editorState.value
            assertFalse(finalState.shouldExit, "A changed durable fingerprint must fail closed")
            assertTrue(finalState.isDirty, "The editor must retain the changed content for review")
            assertNotNull(finalState.errorMessage)
        }

    @Test
    fun testRapidDoubleSubmitPublishesEntryOnlyOnce() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Publish once"))
            advanceUntilIdle()
            val submitState = viewModel.editorState.value

            viewModel.saveEntry(submitState)
            viewModel.saveEntry(submitState)
            advanceUntilIdle()

            val published = journalNotesRepository.allNotesObserved.first()
            assertEquals(1, published.size)
            assertEquals("Publish once", (published.single() as JournalNote.Text).content)
        }

    @Test
    fun testPublishCancellationDoesNotBecomeUiError() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Cancelled publish"))
            advanceUntilIdle()
            journalNotesRepository.createFailure = CancellationException("publish cancelled")

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            assertNull(viewModel.editorState.value.errorMessage)
            assertFalse(viewModel.editorState.value.shouldExit)
            assertFalse(viewModel.editorState.value.isSaving)
        }

    @Test
    fun testPublishCancellationWhileWaitingForDraftMutexClearsExclusiveSaveState() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Waiting for draft mutex"))
            advanceUntilIdle()
            val persistenceGate = CompletableDeferred<Unit>()
            entryDraftRepository.createDraftGate = persistenceGate
            val inFlightDraftSave = async { viewModel.persistDraft(viewModel.editorState.value) }
            runCurrent()
            val viewModelJob = assertNotNull(viewModel.viewModelScope.coroutineContext[Job])
            val childrenBeforePublish = viewModelJob.children.toSet()

            viewModel.saveEntry(viewModel.editorState.value)
            runCurrent()
            val publishJob =
                viewModelJob.children
                    .filterNot { it in childrenBeforePublish }
                    .single()
            publishJob.cancel(CancellationException("cancel while waiting for draft mutex"))
            runCurrent()

            val cancelledState = viewModel.editorState.value
            assertFalse(cancelledState.isSaving)
            assertFalse(cancelledState.isEditingLocked)
            assertNull(cancelledState.errorMessage)
            assertFalse(cancelledState.shouldExit)

            persistenceGate.complete(Unit)
            inFlightDraftSave.await()
        }

    @Test
    fun testEmptyDraftDeleteCancellationClearsExclusiveSaveState() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Durable content before clear"))
            advanceUntilIdle()
            val draftId = assertNotNull(viewModel.persistDraft(viewModel.editorState.value))
            viewModel.updateBlock(block.copy(content = ""))
            entryDraftRepository.setDeletionFailure(
                draftId = draftId,
                failure = CancellationException("empty draft deletion cancelled"),
            )
            advanceUntilIdle()

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            val cancelledState = viewModel.editorState.value
            assertFalse(cancelledState.isSaving)
            assertFalse(cancelledState.isEditingLocked)
            assertNull(cancelledState.errorMessage)
            assertFalse(cancelledState.shouldExit)
        }

    @Test
    fun testJournalSelectionOnlyDraftPersistsAndLoadsSelection() =
        testScope.runTest {
            val selectedJournalId = Uuid.random()

            viewModel.setSelectedJournals(listOf(selectedJournalId))
            advanceUntilIdle()

            assertTrue(viewModel.editorState.value.isModified)
            val draftId = viewModel.persistDraft(viewModel.editorState.value)
            assertNotNull(draftId)
            val storedDraft = entryDraftRepository.getDraft(draftId).first().getOrThrow()
            assertEquals(listOf(selectedJournalId), storedDraft.selectedJournalIds)

            viewModel.setSelectedJournals(emptyList())
            viewModel.loadDraft(draftId)
            advanceUntilIdle()

            assertEquals(listOf(selectedJournalId), viewModel.editorState.value.selectedJournalIds)
        }

    @Test
    fun testNavigationJournalContextOverridesInitializedDefaultWithoutDirtyingEditor() =
        testScope.runTest {
            val defaultJournalId = Uuid.random()
            val navigationJournalId = Uuid.random()

            viewModel.initializeSelectedJournals(listOf(defaultJournalId))
            viewModel.initializeSelectedJournals(listOf(navigationJournalId))
            advanceUntilIdle()

            assertEquals(listOf(navigationJournalId), viewModel.editorState.value.selectedJournalIds)
            assertFalse(viewModel.editorState.value.isModified)
        }

    @Test
    fun testClearingTransientEmptyBlockPreservesJournalSelectionDirtyState() =
        testScope.runTest {
            val selectedJournalId = Uuid.random()
            viewModel.setSelectedJournals(listOf(selectedJournalId))
            viewModel.createNewBlock(BlockType.TEXT)

            assertTrue(viewModel.clearSingleEmptyBlock())
            advanceUntilIdle()
            val stateAfterPickerReset = viewModel.editorState.value
            assertTrue(stateAfterPickerReset.blocks.isEmpty())
            assertTrue(stateAfterPickerReset.isDirty)
            assertFalse(stateAfterPickerReset.canExitWithoutSaving)

            val draftId = assertNotNull(viewModel.persistDraft(stateAfterPickerReset))
            val stored = entryDraftRepository.getDraft(draftId).first().getOrThrow()
            assertEquals(listOf(selectedJournalId), stored.selectedJournalIds)
        }

    @Test
    fun testDeselectingEveryJournalRemainsDirtyAndCreatesEmptySelectionDraft() =
        testScope.runTest {
            viewModel.initializeSelectedJournals(listOf(Uuid.random()))
            viewModel.setSelectedJournals(emptyList())
            advanceUntilIdle()

            assertTrue(viewModel.editorState.value.isDirty)
            val draftId = assertNotNull(viewModel.persistDraft(viewModel.editorState.value))
            val stored = entryDraftRepository.getDraft(draftId).first().getOrThrow()
            assertTrue(stored.selectedJournalIds.isEmpty())
        }

    @Test
    fun testExplicitEmptyJournalSelectionOverridesDerivedDefaultAfterPersistence() =
        testScope.runTest {
            val defaultJournalId = Uuid.random()
            journalRepository.create(Journal(id = defaultJournalId, title = "Default"))
            advanceUntilIdle()
            assertEquals(listOf(defaultJournalId), viewModel.editorState.value.selectedJournalIds)

            viewModel.setSelectedJournals(emptyList())
            advanceUntilIdle()

            assertTrue(viewModel.editorState.value.isDirty)
            val draftId = assertNotNull(viewModel.persistDraft(viewModel.editorState.value))
            advanceUntilIdle()

            assertTrue(
                entryDraftRepository
                    .getDraft(draftId)
                    .first()
                    .getOrThrow()
                    .selectedJournalIds
                    .isEmpty(),
            )
            assertTrue(
                viewModel.editorState.value
                    .selectedJournalIds
                    .isEmpty(),
            )
        }

    @Test
    fun testImmediateSaveAsDraftKeepsExplicitEmptyJournalSelectionAuthoritative() =
        testScope.runTest {
            val defaultJournalId = Uuid.random()
            journalRepository.create(Journal(id = defaultJournalId, title = "Default"))
            advanceUntilIdle()
            assertEquals(listOf(defaultJournalId), viewModel.editorState.value.selectedJournalIds)

            viewModel.setSelectedJournals(emptyList())
            val saved = viewModel.saveAsDraft(viewModel.editorState.value)

            assertTrue(saved)
            val stored = entryDraftRepository.getDrafts().first().single()
            assertTrue(stored.selectedJournalIds.isEmpty())
        }

    @Test
    fun testPublishAfterExplicitEmptySaveDraftDoesNotRestoreCombinedDefault() =
        testScope.runTest {
            val defaultJournalId = Uuid.random()
            journalRepository.create(Journal(id = defaultJournalId, title = "Default"))
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "No journal publish"))
            advanceUntilIdle()

            viewModel.setSelectedJournals(emptyList())
            assertTrue(viewModel.saveAsDraft(viewModel.editorState.value))
            assertTrue(
                entryDraftRepository
                    .getDrafts()
                    .first()
                    .single()
                    .selectedJournalIds
                    .isEmpty(),
            )
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            assertTrue(journalContentRepository.addedJournalIds.isEmpty())
        }

    @Test
    fun testDraftManagerRethrowsLoadCancellation() =
        testScope.runTest {
            entryDraftRepository.getDraftFailure = CancellationException("load cancelled")

            assertFailsWith<CancellationException> {
                draftManager.loadDraft(Uuid.random())
            }
        }

    @Test
    fun testDraftManagerRethrowsDeleteCancellation() =
        testScope.runTest {
            val draftId = Uuid.random()
            entryDraftRepository.setDeletionFailure(
                draftId = draftId,
                failure = CancellationException("delete cancelled"),
            )

            assertFailsWith<CancellationException> {
                draftManager.deleteDraft(draftId)
            }
        }

    @Test
    fun testDraftManagerRethrowsDeleteAllCancellation() =
        testScope.runTest {
            entryDraftRepository.deleteAllDraftsFailure = CancellationException("delete all cancelled")

            assertFailsWith<CancellationException> {
                draftManager.deleteAllDrafts()
            }
        }

    @Test
    fun testRetryAfterPartialDraftCreatePreservesSingleIdentity() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Retry stable identity"))
            advanceUntilIdle()
            entryDraftRepository.createDraftFailureAfterCommit =
                IllegalStateException("store acknowledged after commit")

            val firstFailure = runCatching { viewModel.persistDraft(viewModel.editorState.value) }
            assertTrue(firstFailure.isFailure)

            val retryId = viewModel.persistDraft(viewModel.editorState.value)
            assertNotNull(retryId)

            val drafts = entryDraftRepository.getDrafts().first()
            assertEquals(1, drafts.size)
            assertEquals(retryId, drafts.single().id)
            assertEquals(2, entryDraftRepository.createDraftCallCount)
        }

    @Test
    fun testPublishAfterPartialDraftCreateDeletesRetainedCandidate() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Publish after partial draft create"))
            advanceUntilIdle()
            entryDraftRepository.createDraftFailureAfterCommit =
                IllegalStateException("draft committed before acknowledgement")

            val failedDraftSave = runCatching { viewModel.persistDraft(viewModel.editorState.value) }
            assertTrue(failedDraftSave.isFailure)
            assertEquals(1, entryDraftRepository.getDrafts().first().size)
            assertEquals(DraftState.None, viewModel.editorState.value.draftState)

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            assertTrue(viewModel.editorState.value.shouldExit)
            assertTrue(
                entryDraftRepository.getDrafts().first().isEmpty(),
                "Publish must delete the post-commit candidate retained after a failed acknowledgement",
            )
        }

    @Test
    fun testDiscardDeletesActiveDraftBeforeExit() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Discard durable draft"))
            advanceUntilIdle()
            val draftId = assertNotNull(viewModel.persistDraft(viewModel.editorState.value))

            val discarded = viewModel.discardAndExit()
            advanceUntilIdle()

            assertTrue(discarded)
            assertTrue(entryDraftRepository.getDrafts().first().none { it.id == draftId })
            assertTrue(viewModel.editorState.value.shouldExit)
            assertEquals(EditorExitReason.DISCARDED, viewModel.editorState.value.exitReason)
        }

    @Test
    fun testDiscardDeletionFailureFailsClosed() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Keep when deletion fails"))
            advanceUntilIdle()
            val draftId = assertNotNull(viewModel.persistDraft(viewModel.editorState.value))
            entryDraftRepository.setDeletionFailure(draftId)

            val discarded = viewModel.discardAndExit()
            advanceUntilIdle()

            assertFalse(discarded)
            assertTrue(entryDraftRepository.getDrafts().first().any { it.id == draftId })
            assertFalse(viewModel.editorState.value.shouldExit)
            assertFalse(viewModel.editorState.value.isSaving)
            assertFalse(viewModel.editorState.value.isEditingLocked)
            assertNotNull(viewModel.editorState.value.errorMessage)
        }

    @Test
    fun testDiscardDeletesRetainedPartialCreateBeforeExit() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Discard retained candidate"))
            advanceUntilIdle()
            entryDraftRepository.createDraftFailureAfterCommit =
                IllegalStateException("draft committed before acknowledgement")
            assertTrue(runCatching { viewModel.persistDraft(viewModel.editorState.value) }.isFailure)
            assertEquals(1, entryDraftRepository.getDrafts().first().size)

            val discarded = viewModel.discardAndExit()
            advanceUntilIdle()

            assertTrue(discarded)
            assertTrue(entryDraftRepository.getDrafts().first().isEmpty())
            assertTrue(viewModel.editorState.value.shouldExit)
            assertEquals(EditorExitReason.DISCARDED, viewModel.editorState.value.exitReason)
        }

    @Test
    fun testDiscardCancellationFailsClosedAndReleasesEditorLock() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Retain cancelled discard"))
            advanceUntilIdle()
            val draftId = assertNotNull(viewModel.persistDraft(viewModel.editorState.value))
            val deleteGate = CompletableDeferred<Unit>()
            entryDraftRepository.deleteDraftGate = deleteGate

            val discard = async { viewModel.discardAndExit() }
            runCurrent()
            assertTrue(viewModel.editorState.value.isSaving)
            assertTrue(viewModel.editorState.value.isEditingLocked)

            discard.cancel(CancellationException("discard cancelled"))
            assertFailsWith<CancellationException> { discard.await() }
            advanceUntilIdle()

            assertTrue(entryDraftRepository.getDrafts().first().any { it.id == draftId })
            assertFalse(viewModel.editorState.value.shouldExit)
            assertFalse(viewModel.editorState.value.isSaving)
            assertFalse(viewModel.editorState.value.isEditingLocked)
        }

    @Test
    fun testDeleteAllWaitsForInFlightDraftPersistence() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Delete after write"))
            advanceUntilIdle()
            val persistenceGate = CompletableDeferred<Unit>()
            entryDraftRepository.createDraftGate = persistenceGate

            val save = async { viewModel.persistDraft(viewModel.editorState.value) }
            runCurrent()
            viewModel.deleteAllDrafts()
            runCurrent()

            persistenceGate.complete(Unit)
            save.await()
            advanceUntilIdle()

            assertTrue(entryDraftRepository.getDrafts().first().isEmpty())
            assertEquals(DraftState.None, viewModel.editorState.value.draftState)
        }

    @Test
    fun testDeletingActiveDraftInvalidatesQueuedAutosaveSnapshot() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Delete without resurrection"))
            advanceUntilIdle()
            val draftId = assertNotNull(viewModel.persistDraft(viewModel.editorState.value))
            advanceUntilIdle()
            val staleSnapshot = viewModel.editorState.value

            viewModel.deleteDraft(draftId)
            advanceUntilIdle()

            val staleResult = viewModel.persistDraft(staleSnapshot)
            advanceUntilIdle()

            assertNull(staleResult, "A queued snapshot must not recreate an explicitly deleted draft")
            assertTrue(entryDraftRepository.getDrafts().first().isEmpty())
            assertEquals(DraftState.None, viewModel.editorState.value.draftState)
        }

    @Test
    fun testDeletingAllDraftsInvalidatesQueuedAutosaveSnapshot() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Delete all without resurrection"))
            advanceUntilIdle()
            assertNotNull(viewModel.persistDraft(viewModel.editorState.value))
            advanceUntilIdle()
            val staleSnapshot = viewModel.editorState.value

            viewModel.deleteAllDrafts()
            advanceUntilIdle()

            val staleResult = viewModel.persistDraft(staleSnapshot)
            advanceUntilIdle()

            assertNull(staleResult, "A queued snapshot must not recreate drafts after delete all")
            assertTrue(entryDraftRepository.getDrafts().first().isEmpty())
            assertEquals(DraftState.None, viewModel.editorState.value.draftState)
        }

    @Test
    fun testDeleteDraftCompletionPreservesEditMadeWhileDeletionIsSuspended() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Persisted before delete"))
            advanceUntilIdle()
            val draftId = assertNotNull(viewModel.persistDraft(viewModel.editorState.value))
            val deleteGate = CompletableDeferred<Unit>()
            entryDraftRepository.deleteDraftGate = deleteGate

            viewModel.deleteDraft(draftId)
            runCurrent()
            viewModel.updateBlock(block.copy(content = "Edit made during delete"))
            runCurrent()
            deleteGate.complete(Unit)
            advanceUntilIdle()

            val finalState = viewModel.editorState.value
            assertEquals(DraftState.None, finalState.draftState)
            assertEquals(
                "Edit made during delete",
                (finalState.blocks.single() as TextBlockUiState).content,
            )
            assertTrue(finalState.isDirty, "Delete completion must not clear a newer edit")
        }

    @Test
    fun testDeleteAllCompletionPreservesEditMadeWhileDeletionIsSuspended() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Persisted before delete all"))
            advanceUntilIdle()
            assertNotNull(viewModel.persistDraft(viewModel.editorState.value))
            val deleteGate = CompletableDeferred<Unit>()
            entryDraftRepository.deleteAllDraftsGate = deleteGate

            viewModel.deleteAllDrafts()
            runCurrent()
            viewModel.updateBlock(block.copy(content = "Edit made during delete all"))
            runCurrent()
            deleteGate.complete(Unit)
            advanceUntilIdle()

            val finalState = viewModel.editorState.value
            assertEquals(DraftState.None, finalState.draftState)
            assertEquals(
                "Edit made during delete all",
                (finalState.blocks.single() as TextBlockUiState).content,
            )
            assertTrue(finalState.isDirty, "Delete-all completion must not clear a newer edit")
        }

    @Test
    fun testLoadDraftWinsAfterOlderAutosaveCompletes() =
        testScope.runTest {
            val targetDraftId = entryDraftRepository.createDraft(listOf(textNote("Loaded target")))
            val oldBlock = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(oldBlock.copy(content = "Older editor content"))
            advanceUntilIdle()
            val saveGate = CompletableDeferred<Unit>()
            entryDraftRepository.createDraftGate = saveGate

            val oldAutoSave = async { viewModel.persistDraft(viewModel.editorState.value) }
            runCurrent()
            viewModel.loadDraft(targetDraftId)
            runCurrent()
            saveGate.complete(Unit)
            oldAutoSave.await()
            advanceUntilIdle()

            val finalState = viewModel.editorState.value
            assertEquals(DraftState.Active(targetDraftId), finalState.draftState)
            assertEquals(
                "Loaded target",
                (finalState.blocks.single() as TextBlockUiState).content,
            )
        }

    @Test
    fun testQueuedOldAutosaveCannotRetargetContentToNewlyLoadedDraft() =
        testScope.runTest {
            val targetDraftId = entryDraftRepository.createDraft(listOf(textNote("Loaded target")))
            val oldBlock = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(oldBlock.copy(content = "Queued old content"))
            advanceUntilIdle()
            val staleState = viewModel.editorState.value
            val loadGate = CompletableDeferred<Unit>()
            entryDraftRepository.getDraftGate = loadGate

            viewModel.loadDraft(targetDraftId)
            runCurrent()
            val queuedAutoSave = async { viewModel.persistDraft(staleState) }
            runCurrent()
            loadGate.complete(Unit)
            val queuedResult = queuedAutoSave.await()
            advanceUntilIdle()

            assertNull(queuedResult, "A snapshot from before load must be rejected")
            val finalState = viewModel.editorState.value
            assertEquals(DraftState.Active(targetDraftId), finalState.draftState)
            assertEquals(
                "Loaded target",
                (finalState.blocks.single() as TextBlockUiState).content,
            )
        }

    @Test
    fun testSlowLoadCompletionDoesNotOverwriteNewerEditorMutation() =
        testScope.runTest {
            val targetDraftId = entryDraftRepository.createDraft(listOf(textNote("Loaded target")))
            val currentBlock = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(currentBlock.copy(content = "Before load"))
            advanceUntilIdle()
            val loadGate = CompletableDeferred<Unit>()
            entryDraftRepository.getDraftGate = loadGate

            viewModel.loadDraft(targetDraftId)
            runCurrent()
            viewModel.updateBlock(currentBlock.copy(content = "Newer edit while loading"))
            runCurrent()
            loadGate.complete(Unit)
            advanceUntilIdle()

            val finalState = viewModel.editorState.value
            assertEquals(
                "Newer edit while loading",
                (finalState.blocks.single() as TextBlockUiState).content,
            )
            assertFalse(finalState.draftState == DraftState.Active(targetDraftId))
            assertTrue(finalState.isDirty)
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
    fun testSaveEntryAfterClearingActiveDraftDeletesOldDurableContentBeforeExit() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Old durable content"))
            advanceUntilIdle()
            assertNotNull(viewModel.persistDraft(viewModel.editorState.value))

            viewModel.updateBlock(block.copy(content = ""))
            advanceUntilIdle()
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            assertTrue(viewModel.editorState.value.shouldExit)
            assertEquals(DraftState.None, viewModel.editorState.value.draftState)
            assertTrue(
                entryDraftRepository.getDrafts().first().isEmpty(),
                "Publishing an empty cleared draft must not leave its old content behind",
            )
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
