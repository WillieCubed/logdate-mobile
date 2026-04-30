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
            audioBlockFinalizer = finalizer,
        )
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        journalNotesRepository = FakeJournalNotesRepository()
        entryDraftRepository = FakeEntryDraftRepository()
        fakeFinalizer = RecordingAudioBlockFinalizer()
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
    fun saveEntry_withPendingAudio_persistsJournalNoteAudio() =
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

    @Test
    fun saveEntry_whileActivelyRecording_persistsResolvedAudio() =
        testScope.runTest {
            val viewModel = buildViewModel()
            viewModel.editorState.first()
            val seeded = viewModel.seedAudioBlock(AudioCaptureState.Recording)
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

    @Test
    fun saveEntry_whenFinalizationFails_setsErrorMessageAndDoesNotExit() =
        testScope.runTest {
            val viewModel = buildViewModel()
            viewModel.editorState.first()
            val seeded = viewModel.seedAudioBlock(AudioCaptureState.Recording)
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
    fun saveEntry_whenFinalizationStalls_surfacesTimeoutAndDoesNotExit() =
        testScope.runTest {
            val stallingFinalizer = StallingAudioBlockFinalizer()
            val viewModel = buildViewModel(finalizer = stallingFinalizer)
            viewModel.editorState.first()
            viewModel.seedAudioBlock(AudioCaptureState.Stopping)
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
    fun saveEntry_textOnly_doesNotInvokeFinalizer() =
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
    fun saveEntry_withReadyAudio_doesNotRefinalize() =
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
    fun saveEntry_withMixedTextAndPendingAudio_persistsBoth() =
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
}
