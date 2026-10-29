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
import app.logdate.feature.editor.ui.editor.delegate.AudioBlockFinalizer
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests for the text editing functionality in the editor.
 * These tests verify block creation and updates in the editor state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorExplicitLoadOwnershipTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var viewModel: EntryEditorViewModel
    private lateinit var journalNotesRepository: FakeJournalNotesRepository
    private lateinit var journalContentRepository: FakeJournalContentRepository

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

    @Test
    fun `entry requests reject capture owners without consuming their retry request`() =
        testScope.runTest {
            advanceUntilIdle()
            val timestamp = Instant.parse("2020-01-01T00:00:00Z")
            val target = JournalNote.Text(content = "Loaded input", creationTimestamp = timestamp, lastUpdated = timestamp)
            journalNotesRepository.create(target)
            val protectedCaptures =
                listOf(
                    AudioCaptureState.Empty,
                    AudioCaptureState.Recording("file:///owned.aac"),
                    AudioCaptureState.Stopping("file:///owned.aac"),
                )
            for (capture in protectedCaptures) {
                viewModel = createViewModel()
                advanceUntilIdle()
                val owner = AudioBlockUiState(captureState = capture)
                val token = Uuid.random()
                viewModel.mutableEditorState.value =
                    EditorState(
                        blocks = listOf(owner),
                        creationInputTokens = mapOf(owner.id to token),
                        audioCaptureStartReservations =
                            if (capture == AudioCaptureState.Empty) mapOf(owner.id to Uuid.random()) else emptyMap(),
                    )
                viewModel.loadExistingEntry(target.uid)
                advanceUntilIdle()
                assertEquals(listOf(owner), viewModel.mutableEditorState.value.blocks)
                assertEquals(token, viewModel.mutableEditorState.value.creationInputTokens[owner.id])
                assertNull(viewModel.activeLoadRequest)
                viewModel.mutableEditorState.value =
                    viewModel.mutableEditorState.value.copy(
                        blocks = emptyList(),
                        audioCaptureStartReservations = emptyMap(),
                    )
                viewModel.loadExistingEntry(target.uid)
                advanceUntilIdle()
                assertEquals(
                    target.uid,
                    viewModel.mutableEditorState.value.blocks
                        .single()
                        .id,
                )
            }
        }

    @Test
    fun `entry loading synchronously blocks capture and late ownership rejects the load`() =
        testScope.runTest {
            advanceUntilIdle()
            val owner = AudioBlockUiState()
            val inputToken = Uuid.random()
            val timestamp = Instant.parse("2020-01-01T00:00:00Z")
            journalNotesRepository.create(
                JournalNote.Text(uid = owner.id, content = "Replacement", creationTimestamp = timestamp, lastUpdated = timestamp),
            )
            val pending = CompletableDeferred<Unit>()
            journalNotesRepository.getNoteGates[owner.id] = pending
            viewModel.mutableEditorState.value = EditorState(blocks = listOf(owner), creationInputTokens = mapOf(owner.id to inputToken))
            viewModel.loadExistingEntry(owner.id)
            assertTrue(viewModel.mutableEditorState.value.isLoading)
            runCurrent()
            assertNull(EditorAudioCaptureStartGate(viewModel).claim(owner.id))
            val attempt = Uuid.random()
            viewModel.mutableEditorState.value =
                viewModel.mutableEditorState.value.copy(
                    audioCaptureStartReservations = mapOf(owner.id to attempt),
                )
            pending.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(owner), viewModel.mutableEditorState.value.blocks)
            assertEquals(mapOf(owner.id to attempt), viewModel.mutableEditorState.value.audioCaptureStartReservations)
            assertEquals(inputToken, viewModel.mutableEditorState.value.creationInputTokens[owner.id])
            assertFalse(viewModel.mutableEditorState.value.isLoading)
            assertNull(viewModel.activeLoadRequest)
            viewModel.releaseAudioCaptureStart(owner.id, attempt)
            viewModel.loadExistingEntry(owner.id)
            advanceUntilIdle()
            assertEquals(
                "Replacement",
                (
                    viewModel.mutableEditorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
        }

    @Test
    fun `accepted entry replacement refreshes tokens and invalidates old finalizers only once`() =
        testScope.runTest {
            advanceUntilIdle()
            val timestamp = Instant.parse("2020-01-01T00:00:00Z")
            val note = JournalNote.Text(content = "Loaded", creationTimestamp = timestamp, lastUpdated = timestamp)
            journalNotesRepository.create(note)
            val oldToken = Uuid.random()
            val oldBlock = AudioBlockUiState(id = note.uid)
            viewModel.mutableEditorState.value = EditorState(blocks = listOf(oldBlock), creationInputTokens = mapOf(note.uid to oldToken))
            val staleFinalizer = AudioBlockFinalizer { _, _ -> AudioCaptureState.Ready("file:///stale.aac", 12) }
            viewModel.bindAudioBlockFinalizer(note.uid, staleFinalizer)
            viewModel.loadExistingEntry(note.uid)
            advanceUntilIdle()
            val currentToken = assertNotNull(viewModel.mutableEditorState.value.creationInputTokens[note.uid])
            assertNotEquals(oldToken, currentToken)
            assertNull(viewModel.audioBlockFinalizers[note.uid])
            viewModel.loadExistingEntry(note.uid)
            advanceUntilIdle()
            journalNotesRepository.seedExternally(note.copy(uid = Uuid.random(), content = "Ordinary repository emission"))
            advanceUntilIdle()
            assertEquals(currentToken, viewModel.mutableEditorState.value.creationInputTokens[note.uid])
            assertEquals(
                "Loaded",
                (
                    viewModel.mutableEditorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
        }

    @Test
    fun `failed entry load preserves tokens and an older failure cannot clear newer loading`() =
        testScope.runTest {
            advanceUntilIdle()
            val oldBlock = AudioBlockUiState()
            val oldToken = Uuid.random()
            viewModel.mutableEditorState.value =
                EditorState(
                    blocks = listOf(oldBlock),
                    creationInputTokens = mapOf(oldBlock.id to oldToken),
                )
            val missing = Uuid.random()
            val first = CompletableDeferred<Unit>()
            journalNotesRepository.getNoteGates[missing] = first
            val timestamp = Instant.parse("2020-01-01T00:00:00Z")
            val next = JournalNote.Text(content = "Next", creationTimestamp = timestamp, lastUpdated = timestamp)
            journalNotesRepository.create(next)
            val second = CompletableDeferred<Unit>()
            journalNotesRepository.getNoteGates[next.uid] = second
            viewModel.loadExistingEntry(missing)
            runCurrent()
            viewModel.loadExistingEntry(next.uid)
            runCurrent()
            first.complete(Unit)
            runCurrent()
            assertTrue(viewModel.mutableEditorState.value.isLoading)
            assertEquals(mapOf(oldBlock.id to oldToken), viewModel.mutableEditorState.value.creationInputTokens)
            second.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.mutableEditorState.value.isLoading)
            assertEquals(oldToken, viewModel.mutableEditorState.value.creationInputTokens[oldBlock.id])
        }

    @Test
    fun `delayed entry loading preserves edits made during the fetch`() =
        testScope.runTest {
            advanceUntilIdle()
            val noteId = Uuid.random()
            val timestamp = Instant.parse("2020-01-01T00:00:00Z")
            journalNotesRepository.create(
                JournalNote.Text(uid = noteId, content = "Stored", creationTimestamp = timestamp, lastUpdated = timestamp),
            )
            val gate = CompletableDeferred<Unit>()
            journalNotesRepository.getNoteGates[noteId] = gate
            viewModel.loadExistingEntry(noteId)
            runCurrent()
            val newBlock = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(newBlock.copy(content = "Keep my new work"))
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                "Keep my new work",
                (
                    viewModel.editorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
            assertTrue(viewModel.editorState.value.isDirty)
            viewModel.loadExistingEntry(noteId)
            advanceUntilIdle()
            assertEquals(
                "Keep my new work",
                (
                    viewModel.editorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
        }
}
