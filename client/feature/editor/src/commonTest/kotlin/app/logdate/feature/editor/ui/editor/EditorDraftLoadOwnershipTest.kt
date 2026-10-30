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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
class EditorDraftLoadOwnershipTest {
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
    fun `draft requests preserve capture owners and remain deliberately retryable`() =
        testScope.runTest {
            advanceUntilIdle()
            val note = textNote("Loaded draft")
            val target = entryDraftRepository.createDraft(listOf(note))
            val protectedCaptures =
                listOf(
                    AudioCaptureState.Empty,
                    AudioCaptureState.Recording("file:///owned.aac"),
                    AudioCaptureState.Stopping("file:///owned.aac"),
                )
            for (capture in protectedCaptures) {
                val owner = AudioBlockUiState(captureState = capture)
                val inputToken = Uuid.random()
                viewModel.activeLoadRequest = null
                viewModel.mutableEditorState.value =
                    EditorState(
                        blocks = listOf(owner),
                        creationInputTokens = mapOf(owner.id to inputToken),
                        audioCaptureStartReservations =
                            if (capture == AudioCaptureState.Empty) mapOf(owner.id to Uuid.random()) else emptyMap(),
                    )
                viewModel.loadDraft(target)
                advanceUntilIdle()
                assertEquals(listOf(owner), viewModel.mutableEditorState.value.blocks)
                assertEquals(inputToken, viewModel.mutableEditorState.value.creationInputTokens[owner.id])
                assertNull(viewModel.activeLoadRequest)
            }
            viewModel.mutableEditorState.value = EditorState()
            viewModel.loadDraft(target)
            advanceUntilIdle()
            assertEquals(DraftState.Active(target), viewModel.mutableEditorState.value.draftState)
        }

    @Test
    fun `late capture ownership rejects a draft load without changing durable destination`() =
        testScope.runTest {
            advanceUntilIdle()
            val owner = AudioBlockUiState()
            val inputToken = Uuid.random()
            val originalDraft = Uuid.random()
            val target = entryDraftRepository.createDraft(listOf(textNote("Different draft").copy(uid = owner.id)))
            val pending = CompletableDeferred<Unit>()
            entryDraftRepository.getDraftGate = pending
            viewModel.mutableEditorState.value =
                EditorState(
                    blocks = listOf(owner),
                    draftState = DraftState.Active(originalDraft),
                    creationInputTokens = mapOf(owner.id to inputToken),
                )
            viewModel.savedStateHandle["editor.draftId"] = originalDraft.toString()
            viewModel.loadDraft(target)
            assertTrue(viewModel.mutableEditorState.value.isLoading)
            runCurrent()
            val attempt = Uuid.random()
            viewModel.mutableEditorState.value =
                viewModel.mutableEditorState.value.copy(
                    audioCaptureStartReservations = mapOf(owner.id to attempt),
                )
            pending.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(owner), viewModel.mutableEditorState.value.blocks)
            assertEquals(DraftState.Active(originalDraft), viewModel.mutableEditorState.value.draftState)
            assertEquals(originalDraft, viewModel.recoverableDraftId)
            assertEquals(inputToken, viewModel.mutableEditorState.value.creationInputTokens[owner.id])
            assertEquals(attempt, viewModel.mutableEditorState.value.audioCaptureStartReservations[owner.id])
            assertFalse(viewModel.mutableEditorState.value.isLoading)
            assertNull(viewModel.activeLoadRequest)
            viewModel.releaseAudioCaptureStart(owner.id, attempt)
            viewModel.loadDraft(target)
            advanceUntilIdle()
            assertEquals(target, viewModel.recoverableDraftId)
        }

    @Test
    fun `accepted explicit draft load refreshes same ID input once while replay and emissions retain it`() =
        testScope.runTest {
            advanceUntilIdle()
            val note = textNote("Loaded draft input")
            val target = entryDraftRepository.createDraft(listOf(note))
            val oldToken = Uuid.random()
            viewModel.mutableEditorState.value =
                EditorState(
                    blocks = listOf(TextBlockUiState(id = note.uid, content = "Before")),
                    creationInputTokens = mapOf(note.uid to oldToken),
                )
            viewModel.loadDraft(target)
            advanceUntilIdle()
            val token = assertNotNull(viewModel.mutableEditorState.value.creationInputTokens[note.uid])
            assertNotEquals(oldToken, token)
            assertEquals(target, viewModel.recoverableDraftId)
            viewModel.loadDraft(target)
            advanceUntilIdle()
            entryDraftRepository.createDraft(listOf(textNote("Ordinary draft emission")))
            advanceUntilIdle()
            assertEquals(token, viewModel.mutableEditorState.value.creationInputTokens[note.uid])
            assertEquals(
                note.uid,
                viewModel.mutableEditorState.value.blocks
                    .single()
                    .id,
            )
        }

    @Test
    fun `failed draft loading clears only loading and preserves tokens for a real retry`() =
        testScope.runTest {
            advanceUntilIdle()
            val note = textNote("Eventually available")
            val target = entryDraftRepository.createDraft(listOf(note))
            val block = AudioBlockUiState()
            val token = Uuid.random()
            viewModel.mutableEditorState.value = EditorState(blocks = listOf(block), creationInputTokens = mapOf(block.id to token))
            entryDraftRepository.getDraftFailure = IllegalStateException("Temporarily unavailable")
            viewModel.loadDraft(target)
            advanceUntilIdle()
            assertFalse(viewModel.mutableEditorState.value.isLoading)
            assertEquals(mapOf(block.id to token), viewModel.mutableEditorState.value.creationInputTokens)
            assertEquals(listOf(block), viewModel.mutableEditorState.value.blocks)
            assertNull(viewModel.activeLoadRequest)
            assertNull(viewModel.mutableEditorState.value.pendingDraftReveal)
            entryDraftRepository.getDraftFailure = null
            viewModel.loadDraft(target)
            advanceUntilIdle()
            assertEquals(target, viewModel.recoverableDraftId)
            assertNotNull(viewModel.mutableEditorState.value.creationInputTokens[note.uid])
        }

    @Test
    fun `slow load completion does not overwrite newer editor mutation`() =
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
            viewModel.loadDraft(targetDraftId)
            advanceUntilIdle()
            assertEquals(
                "Newer edit while loading",
                (
                    viewModel.editorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
        }

    @Test
    fun `accepted draft requests one reveal of its current input without changing focus`() =
        testScope.runTest {
            advanceUntilIdle()
            val note = textNote("Retained current draft")
            val id = entryDraftRepository.createDraft(listOf(note))
            viewModel.loadDraft(id)
            advanceUntilIdle()
            val loaded = viewModel.mutableEditorState.value
            val request = assertNotNull(loaded.pendingDraftReveal)
            assertEquals(note.uid, request.blockId)
            assertEquals(loaded.creationInputTokens[note.uid], request.inputToken)
            assertNull(loaded.expandedBlockId)
            assertFalse(loaded.isRecordingAudio)
            assertTrue(viewModel.consumeDraftReveal(request))
            val consumed = viewModel.mutableEditorState.value
            assertNull(consumed.pendingDraftReveal)
            assertEquals(loaded.contentRevision, consumed.contentRevision)
            assertEquals(loaded.blocks, consumed.blocks)
            assertEquals(loaded.isModified, consumed.isModified)
            assertFalse(viewModel.consumeDraftReveal(request))
            viewModel.loadDraft(id)
            advanceUntilIdle()
            assertNull(viewModel.mutableEditorState.value.pendingDraftReveal)
        }

    @Test
    fun `old reveal cannot consume a newer load and stale input cannot move viewport`() =
        testScope.runTest {
            advanceUntilIdle()
            val first = entryDraftRepository.createDraft(listOf(textNote("First")))
            val second = entryDraftRepository.createDraft(listOf(textNote("Second")))
            viewModel.loadDraft(first)
            advanceUntilIdle()
            val old = assertNotNull(viewModel.mutableEditorState.value.pendingDraftReveal)
            viewModel.loadDraft(second)
            advanceUntilIdle()
            val current = assertNotNull(viewModel.mutableEditorState.value.pendingDraftReveal)
            assertFalse(viewModel.consumeDraftReveal(old))
            assertEquals(current, viewModel.mutableEditorState.value.pendingDraftReveal)
            viewModel.mutableEditorState.value =
                viewModel.mutableEditorState.value.copy(
                    creationInputTokens = mapOf(current.blockId to Uuid.random()),
                )
            assertFalse(viewModel.consumeDraftReveal(current))
            assertNull(viewModel.mutableEditorState.value.pendingDraftReveal)
        }

    @Test
    fun `focus entering then leaving before placement permanently cancels the reveal`() =
        testScope.runTest {
            advanceUntilIdle()
            val note = textNote("Selected by the user")
            val draft = entryDraftRepository.createDraft(listOf(note))
            viewModel.loadDraft(draft)
            advanceUntilIdle()
            val request = assertNotNull(viewModel.mutableEditorState.value.pendingDraftReveal)
            viewModel.setExpandedBlockId(note.uid)
            viewModel.setExpandedBlockId(null)
            assertNull(viewModel.mutableEditorState.value.pendingDraftReveal)
            assertFalse(viewModel.consumeDraftReveal(request))
        }

    @Test
    fun `capture reservation cannot revive a pending reveal after release`() =
        testScope.runTest {
            advanceUntilIdle()
            val block = AudioBlockUiState()
            val request = EditorDraftReveal(block.id, Uuid.random())
            viewModel.mutableEditorState.value =
                EditorState(
                    blocks = listOf(block),
                    creationInputTokens = mapOf(block.id to request.inputToken),
                    pendingDraftReveal = request,
                )
            val attempt = assertNotNull(viewModel.claimAudioCaptureStart(block.id))
            viewModel.releaseAudioCaptureStart(block.id, attempt)
            assertNull(viewModel.mutableEditorState.value.pendingDraftReveal)
            assertFalse(viewModel.consumeDraftReveal(request))
        }

    @Test
    fun `live user cancellation clears a load before the rendered state exposes it`() =
        testScope.runTest {
            advanceUntilIdle()
            val rendered = viewModel.editorState.value
            val draft = entryDraftRepository.createDraft(listOf(textNote("New latest input")))
            viewModel.loadDraft(draft)
            advanceUntilIdle()
            assertNull(rendered.pendingDraftReveal)
            assertNotNull(viewModel.mutableEditorState.value.pendingDraftReveal)
            val revision = viewModel.mutableEditorState.value.contentRevision
            viewModel.cancelDraftReveal()
            assertNull(viewModel.mutableEditorState.value.pendingDraftReveal)
            assertEquals(revision, viewModel.mutableEditorState.value.contentRevision)
        }
}
