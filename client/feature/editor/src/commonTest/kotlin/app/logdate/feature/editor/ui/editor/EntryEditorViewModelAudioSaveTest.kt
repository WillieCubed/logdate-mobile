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
import app.logdate.client.media.MediaCleaner
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Integration tests for [EntryEditorViewModel.saveEntry]'s pending-audio handling.
 *
 * The race these tests defend against: when a user taps Save while an audio
 * recording's URI lives in the recording side (AudioViewModel) and has not yet
 * been transferred to the editor's [AudioBlockUiState] via the Compose
 * [androidx.compose.runtime.LaunchedEffect] in `AudioBlockEditor`, the save
 * path used to drop the audio entirely. After the fix, save consults the
 * [AudioBlockFinalizer] for every pending audio block and absorbs any
 * in-flight URI before mapping to journal notes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryEditorViewModelAudioSaveTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var journalNotesRepository: FakeJournalNotesRepository
    private lateinit var entryDraftRepository: FakeEntryDraftRepository
    private lateinit var fakeFinalizer: RecordingAudioBlockFinalizer
    private lateinit var mediaCleaner: RecordingMediaCleaner

    private fun buildViewModel(finalizer: AudioBlockFinalizer = fakeFinalizer): EntryEditorViewModel {
        val journalContentRepository = FakeJournalContentRepository()
        val journalRepository = FakeJournalRepository()

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
        val deleteEntryDraft = DeleteEntryDraftUseCase(entryDraftRepository, journalNotesRepository, mediaCleaner)

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
            defaultAudioBlockFinalizer = finalizer,
        )
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        journalNotesRepository = FakeJournalNotesRepository()
        // Mirror Room: its own reactive "today's notes" query can complete and be observed
        // before the coroutine that triggered the write resumes. Draining the test dispatcher
        // right after the write reproduces that interleaving instead of letting the whole save
        // run as one uninterrupted, unrealistically atomic block.
        journalNotesRepository.afterCreate = { testDispatcher.scheduler.runCurrent() }
        entryDraftRepository = FakeEntryDraftRepository()
        fakeFinalizer = RecordingAudioBlockFinalizer()
        mediaCleaner = RecordingMediaCleaner()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun EntryEditorViewModel.seedAudioBlock(captureState: AudioCaptureState): AudioBlockUiState {
        val placeholder = createNewBlock(BlockType.AUDIO) as AudioBlockUiState
        val seeded = placeholder.copy(captureState = captureState)
        updateBlock(seeded)
        return seeded
    }

    private fun EntryEditorViewModel.seedTextBlock(content: String): TextBlockUiState {
        val placeholder = createNewBlock(BlockType.TEXT) as TextBlockUiState
        val seeded = placeholder.copy(content = content)
        updateBlock(seeded)
        return seeded
    }

    /** Headline-bug regression. */
    @Test
    fun `save entry with pending audio persists journal note audio`() =
        testScope.runTest {
            val viewModel = buildViewModel()
            viewModel.editorState.first()
            val seeded = viewModel.seedAudioBlock(AudioCaptureState.Empty)
            advanceUntilIdle()
            fakeFinalizer.respondWith(
                seeded.id,
                AudioCaptureState.Ready(uri = "file:///audio_notes/recording_${seeded.id}.m4a", durationMs = 4_200L),
            )

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            assertEquals(1, fakeFinalizer.invocationCount, "saveEntry must finalize each pending audio block once")
            val saved = journalNotesRepository.allNotesObserved.first()
            assertEquals(1, saved.size, "exactly one note must be persisted for the pending audio")
            val audio = assertIs<JournalNote.Audio>(saved.single())
            assertEquals(seeded.id, audio.uid, "uid must match the block so transcription rows linked by noteId remain valid")
            assertEquals("file:///audio_notes/recording_${seeded.id}.m4a", audio.mediaRef)
            assertEquals(4_200L, audio.durationMs)
        }

    /**
     * Regression for a real, live-reproduced bug: this note's own block flips read-only the
     * moment [journalNotesRepository] observes it (mirrored here by [FakeJournalNotesRepository.afterCreate]
     * draining the dispatcher right after the write, matching how Room's reactive query can
     * complete before the saving coroutine resumes). The post-save consistency check used to
     * re-derive `readOnlyBlocks` live and see that flip as a conflicting external edit, failing
     * the save with "The editor changed while saving" on every save whose finalize step is slow
     * enough to let the flip land inside the critical section -- which a real audio recording's
     * `stopRecordingInternal()` reliably is.
     */
    @Test
    fun `save entry while actively recording persists resolved audio`() =
        testScope.runTest {
            val viewModel = buildViewModel()
            viewModel.editorState.first()
            val seeded = viewModel.seedAudioBlock(AudioCaptureState.Recording())
            advanceUntilIdle()
            fakeFinalizer.respondWith(
                seeded.id,
                AudioCaptureState.Ready(uri = "file:///audio_notes/active.m4a", durationMs = 1_500L),
            )

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            val saved = journalNotesRepository.allNotesObserved.first()
            val audio = assertIs<JournalNote.Audio>(saved.single())
            assertEquals("file:///audio_notes/active.m4a", audio.mediaRef)
            val finalState = viewModel.editorState.value
            assertTrue(finalState.shouldExit, "save must complete and request editor exit when finalization succeeds")
            assertNull(finalState.errorMessage)
        }

    /**
     * The fix for the false positive above must not make the check blind to real conflicts: a
     * block this save is NOT publishing (an empty text block with nothing to save) becoming
     * read-only for an unrelated reason during the save -- simulating a genuinely concurrent
     * write landing mid-save -- must still fail the save with "The editor changed while saving".
     */
    @Test
    fun `save entry still detects a genuine conflict on a block it is not publishing`() =
        testScope.runTest {
            val viewModel = buildViewModel()
            viewModel.editorState.first()
            viewModel.seedTextBlock("today was a good day")
            val untouchedBlock = viewModel.seedTextBlock("")
            advanceUntilIdle()

            val unrelatedNote =
                JournalNote.Text(
                    uid = untouchedBlock.id,
                    creationTimestamp = untouchedBlock.timestamp,
                    lastUpdated = untouchedBlock.timestamp,
                    content = "written by something else entirely",
                )
            journalNotesRepository.afterCreate = {
                journalNotesRepository.seedExternally(unrelatedNote)
                testDispatcher.scheduler.runCurrent()
            }

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            val finalState = viewModel.editorState.value
            assertFalse(finalState.shouldExit, "a conflict on a block this save didn't publish must still block exit")
            assertEquals("The editor changed while saving. Review your entry before closing.", finalState.errorMessage)
        }

    /**
     * Regression for a real, live-reproduced bug: [AudioBlockFinalizer] resolved by Koin at this
     * ViewModel's own construction time is backed by an [AudioViewModel][app.logdate.feature.editor.ui.audio.AudioViewModel]
     * instance disconnected from whatever is actually recording on screen -- `get<AudioViewModel>()`
     * inside a plain `factory { }` block doesn't go through Compose's screen-scoped `koinViewModel()`
     * resolution, so it constructs a brand-new, never-started instance. [bindAudioBlockFinalizer]
     * lets the recording screen -- which owns the one true, live instance via its own
     * `koinViewModel()` call -- replace the disconnected one once it composes.
     */
    @Test
    fun `bindAudioBlockFinalizer replaces the constructor-injected finalizer for later saves`() =
        testScope.runTest {
            val viewModel = buildViewModel(finalizer = AudioBlockFinalizer.NoOp)
            viewModel.editorState.first()
            val seeded = viewModel.seedAudioBlock(AudioCaptureState.Recording())
            advanceUntilIdle()
            fakeFinalizer.respondWith(
                seeded.id,
                AudioCaptureState.Ready(uri = "file:///audio_notes/rebound.m4a", durationMs = 2_000L),
            )

            viewModel.bindAudioBlockFinalizer(seeded.id, fakeFinalizer)
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            val saved = journalNotesRepository.allNotesObserved.first()
            val audio = assertIs<JournalNote.Audio>(saved.single())
            assertEquals("file:///audio_notes/rebound.m4a", audio.mediaRef)
        }

    /**
     * Regression for a real, live-reproduced bug: `AudioBlockEditor` used to resolve its
     * [AudioViewModel][app.logdate.feature.editor.ui.audio.AudioViewModel] via an unkeyed
     * `koinViewModel()` call, so every audio block on the same editor screen shared ONE
     * instance and therefore ONE `lastTargetNoteId`. Recording block B after block A
     * overwrote the shared instance's notion of which block was recording, so block A's
     * own finalizer -- resolved via [bindAudioBlockFinalizer] -- would return null for
     * block A even though A's own recording had genuinely completed.
     *
     * The fix keys each `AudioBlockEditor`'s `koinViewModel()` call by its own block id, so
     * each block gets its own `AudioViewModel`, and [bindAudioBlockFinalizer] now stores
     * finalizers in a map keyed by block id instead of a single mutable var that the second
     * block composing would clobber. This test simulates two blocks' own `AudioBlockEditor`
     * instances each binding their own live finalizer, and asserts both resolve correctly --
     * neither clobbering the other, and neither falling back to the disconnected default.
     */
    @Test
    fun `bindAudioBlockFinalizer keeps each block's finalizer independent when two blocks are recording`() =
        testScope.runTest {
            val viewModel = buildViewModel(finalizer = AudioBlockFinalizer.NoOp)
            viewModel.editorState.first()
            val blockA = viewModel.seedAudioBlock(AudioCaptureState.Recording())
            val blockB = viewModel.seedAudioBlock(AudioCaptureState.Recording())
            advanceUntilIdle()

            val finalizerA = RecordingAudioBlockFinalizer()
            val finalizerB = RecordingAudioBlockFinalizer()
            finalizerA.respondWith(
                blockA.id,
                AudioCaptureState.Ready(uri = "file:///audio_notes/block_a.m4a", durationMs = 1_000L),
            )
            finalizerB.respondWith(
                blockB.id,
                AudioCaptureState.Ready(uri = "file:///audio_notes/block_b.m4a", durationMs = 2_000L),
            )

            viewModel.bindAudioBlockFinalizer(blockA.id, finalizerA)
            viewModel.bindAudioBlockFinalizer(blockB.id, finalizerB)
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            assertEquals(1, finalizerA.invocationCount, "block A's own finalizer must be consulted exactly once")
            assertEquals(1, finalizerB.invocationCount, "block B's own finalizer must be consulted exactly once")

            val saved = journalNotesRepository.allNotesObserved.first()
            assertEquals(2, saved.size, "both blocks' audio must be persisted")
            val audioA = assertIs<JournalNote.Audio>(saved.single { it.uid == blockA.id })
            val audioB = assertIs<JournalNote.Audio>(saved.single { it.uid == blockB.id })
            assertEquals("file:///audio_notes/block_a.m4a", audioA.mediaRef)
            assertEquals(1_000L, audioA.durationMs)
            assertEquals("file:///audio_notes/block_b.m4a", audioB.mediaRef)
            assertEquals(2_000L, audioB.durationMs)

            val finalState = viewModel.editorState.value
            assertTrue(finalState.shouldExit, "save must complete once both blocks' own finalizers resolve")
            assertNull(finalState.errorMessage)
        }

    @Test
    fun `save entry when finalization fails sets error message and does not exit`() =
        testScope.runTest {
            val viewModel = buildViewModel()
            viewModel.editorState.first()
            val seeded = viewModel.seedAudioBlock(AudioCaptureState.Recording())
            advanceUntilIdle()
            fakeFinalizer.respondWith(seeded.id, AudioCaptureState.Failed("Recording could not be finalized"))

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            val savedNotes = journalNotesRepository.allNotesObserved.first()
            assertTrue(savedNotes.isEmpty(), "no note must be written when audio finalization fails")
            val state = viewModel.editorState.value
            assertEquals("Recording could not be finalized", state.errorMessage)
            assertFalse(state.shouldExit, "editor must stay open so the user can retry")
            assertFalse(state.isSaving)
        }

    @Test
    fun `save entry when finalizer throws clears saving and surfaces recoverable error`() =
        testScope.runTest {
            val viewModel =
                buildViewModel(
                    finalizer = ThrowingAudioBlockFinalizer(IllegalStateException("finalizer exploded")),
                )
            viewModel.editorState.first()
            viewModel.seedAudioBlock(AudioCaptureState.Recording())
            advanceUntilIdle()

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            val state = viewModel.editorState.value
            assertFalse(state.isSaving)
            assertFalse(state.isEditingLocked)
            assertFalse(state.shouldExit)
            assertTrue(state.errorMessage?.contains("finalizer exploded") == true)
        }

    @Test
    fun `save entry when finalizer cancels clears saving without ui error`() =
        testScope.runTest {
            val viewModel =
                buildViewModel(
                    finalizer = ThrowingAudioBlockFinalizer(CancellationException("finalizer cancelled")),
                )
            viewModel.editorState.first()
            viewModel.seedAudioBlock(AudioCaptureState.Recording())
            advanceUntilIdle()

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            val state = viewModel.editorState.value
            assertFalse(state.isSaving)
            assertFalse(state.isEditingLocked)
            assertFalse(state.shouldExit)
            assertNull(state.errorMessage)
        }

    @Test
    fun `save entry when finalization stalls surfaces timeout and does not exit`() =
        testScope.runTest {
            val stallingFinalizer = StallingAudioBlockFinalizer()
            val viewModel = buildViewModel(finalizer = stallingFinalizer)
            viewModel.editorState.first()
            viewModel.seedAudioBlock(AudioCaptureState.Stopping())
            advanceUntilIdle()

            viewModel.saveEntry(viewModel.editorState.value)
            advanceTimeBy(10_000L)
            advanceUntilIdle()

            val savedNotes = journalNotesRepository.allNotesObserved.first()
            assertTrue(savedNotes.isEmpty())
            val state = viewModel.editorState.value
            assertNotNull(state.errorMessage, "timeout must surface a recoverable error")
            assertFalse(state.shouldExit, "editor must stay open after a finalize timeout")
            assertFalse(state.isSaving)
        }

    @Test
    fun `save entry text only does not invoke finalizer`() =
        testScope.runTest {
            val viewModel = buildViewModel()
            viewModel.editorState.first()
            viewModel.seedTextBlock("today was a good day")
            advanceUntilIdle()

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            assertEquals(0, fakeFinalizer.invocationCount, "text-only saves must not pay any audio finalize cost")
            val saved = journalNotesRepository.allNotesObserved.first()
            assertEquals(1, saved.size)
            assertIs<JournalNote.Text>(saved.single())
        }

    @Test
    fun `save entry with ready audio does not refinalize`() =
        testScope.runTest {
            val viewModel = buildViewModel()
            viewModel.editorState.first()
            viewModel.seedAudioBlock(
                AudioCaptureState.Ready(uri = "file:///audio_notes/ready.m4a", durationMs = 2_000L),
            )
            advanceUntilIdle()

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            assertEquals(0, fakeFinalizer.invocationCount, "blocks already in Ready must not be re-finalized")
            val saved = journalNotesRepository.allNotesObserved.first()
            val audio = assertIs<JournalNote.Audio>(saved.single())
            assertEquals("file:///audio_notes/ready.m4a", audio.mediaRef)
        }

    @Test
    fun `publishing an autosaved audio draft retains the permanent recording`() =
        testScope.runTest {
            val viewModel = buildViewModel()
            viewModel.editorState.first()
            val recordingPath = "file:///audio_notes/permanent.m4a"
            viewModel.seedAudioBlock(
                AudioCaptureState.Ready(uri = recordingPath, durationMs = 3_000L),
            )
            advanceUntilIdle()

            viewModel.persistDraft(viewModel.editorState.value)
            advanceUntilIdle()
            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            val saved = journalNotesRepository.allNotesObserved.first()
            assertEquals(recordingPath, assertIs<JournalNote.Audio>(saved.single()).mediaRef)
            assertTrue(
                mediaCleaner.deletedPaths.isEmpty(),
                "publishing must remove the draft row without deleting media now owned by the permanent note",
            )
        }

    @Test
    fun `save entry with mixed text and pending audio persists both`() =
        testScope.runTest {
            val viewModel = buildViewModel()
            viewModel.editorState.first()
            viewModel.seedTextBlock("voice memo:")
            val audio = viewModel.seedAudioBlock(AudioCaptureState.Empty)
            advanceUntilIdle()
            fakeFinalizer.respondWith(
                audio.id,
                AudioCaptureState.Ready(uri = "file:///audio_notes/mixed.m4a", durationMs = 7_777L),
            )

            viewModel.saveEntry(viewModel.editorState.value)
            advanceUntilIdle()

            val saved = journalNotesRepository.allNotesObserved.first()
            assertEquals(2, saved.size, "both blocks must be persisted")
            assertTrue(saved.any { it is JournalNote.Text })
            assertTrue(saved.any { it is JournalNote.Audio })
        }

    private class RecordingAudioBlockFinalizer : AudioBlockFinalizer {
        private val responses = mutableMapOf<Uuid, AudioCaptureState>()
        var invocationCount = 0
            private set

        fun respondWith(
            blockId: Uuid,
            state: AudioCaptureState,
        ) {
            responses[blockId] = state
        }

        override suspend fun finalize(
            blockId: Uuid,
            currentState: AudioCaptureState,
        ): AudioCaptureState {
            invocationCount += 1
            return responses[blockId] ?: currentState
        }
    }

    private class RecordingMediaCleaner : MediaCleaner {
        val deletedPaths = mutableListOf<String>()

        override suspend fun delete(path: String) {
            deletedPaths += path
        }
    }

    private class StallingAudioBlockFinalizer : AudioBlockFinalizer {
        private val gate = CompletableDeferred<Unit>()

        override suspend fun finalize(
            blockId: Uuid,
            currentState: AudioCaptureState,
        ): AudioCaptureState {
            gate.await()
            return currentState
        }
    }

    private class ThrowingAudioBlockFinalizer(
        private val failure: Throwable,
    ) : AudioBlockFinalizer {
        override suspend fun finalize(
            blockId: Uuid,
            currentState: AudioCaptureState,
        ): AudioCaptureState = throw failure
    }
}
