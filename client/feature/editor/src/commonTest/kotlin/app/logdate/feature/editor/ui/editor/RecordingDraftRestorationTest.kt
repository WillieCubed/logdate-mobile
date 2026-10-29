package app.logdate.feature.editor.ui.editor

import androidx.lifecycle.SavedStateHandle
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
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import app.logdate.feature.editor.ui.editor.delegate.ContentLoader
import app.logdate.feature.editor.ui.editor.delegate.DefaultPendingAudioRecoverer
import app.logdate.feature.editor.ui.editor.delegate.DraftManager
import app.logdate.feature.editor.ui.editor.delegate.PendingAudioRecoverer
import app.logdate.feature.editor.ui.editor.fakes.FakeActivityTimelineRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeClientLocationProvider
import app.logdate.feature.editor.ui.editor.fakes.FakeEntryDraftRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalContentRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalNotesRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeLocationHistoryRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeLocationTrackingSettingsRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeMediaManager
import app.logdate.feature.editor.ui.layout.EditorFocus
import app.logdate.feature.editor.ui.layout.editorFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingDraftRestorationTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private val journalNotesRepository = FakeJournalNotesRepository()
    private val entryDraftRepository = FakeEntryDraftRepository()
    private val mediaCleaner =
        object : MediaCleaner {
            override suspend fun delete(path: String) {}
        }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        pendingAudioRecoverer: PendingAudioRecoverer? = null,
    ): EntryEditorViewModel {
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
            savedStateHandle = handle,
            pendingAudioRecoverer = pendingAudioRecoverer,
        )
    }

    @Test
    fun `process recreation restores the recording draft without requesting a new take`() =
        testScope.runTest {
            val handle = SavedStateHandle()
            val original = buildViewModel(handle = handle)
            val audio = original.createNewBlock(BlockType.AUDIO) as AudioBlockUiState
            advanceUntilIdle()
            assertTrue(original.persistAudioRecordingTarget(audio.id, "/retained-recording.m4a"))
            val draftId = assertNotNull(original.recoverableDraftId)
            val restored = buildViewModel(handle = SavedStateHandle(mapOf("editor.draftId" to handle.get<String>("editor.draftId"))))
            assertEquals(draftId, restored.recoverableDraftId)
            restored.loadDraft(draftId)
            advanceUntilIdle()
            val block =
                assertIs<AudioBlockUiState>(
                    restored.editorState.value.blocks
                        .single(),
                )
            assertEquals(audio.id, block.id)
            assertIs<AudioCaptureState.Stopping>(block.captureState)
            assertFalse(block.startOnOpen)
        }

    @Test
    fun `explicit mixed draft with failed audio opens entry overview without starting capture`() =
        testScope.runTest {
            val draft = seedMixedDraft()
            val recorder = ObservedRecordingManager()
            val viewModel = buildViewModel(pendingAudioRecoverer = missingAudioRecoverer(recorder))
            viewModel.loadDraft(draft.id)
            advanceUntilIdle()

            val state = viewModel.mutableEditorState.value
            val audio = assertMixedDraftRetained(draft, state)
            val failed = assertIs<AudioCaptureState.Failed>(audio.captureState)
            assertEquals(draft.pendingMedia.single().filePath, failed.filePath)
            assertEquals(draft.pendingMedia.single().fallbackFilePath, failed.fallbackFilePath)
            assertNull(state.expandedBlockId)
            assertEquals(EditorFocus.Entry, state.editorFocus())
            assertFalse(state.isRecordingAudio)
            assertEquals(0, recorder.startCalls)
            assertEquals(0, recorder.stopCalls)

            // Recovery remains available when the person selects the failed audio block.
            viewModel.setExpandedBlockId(audio.id)
            assertEquals(EditorFocus.Editing(audio.id), viewModel.mutableEditorState.value.editorFocus())
            assertEquals(audio, viewModel.mutableEditorState.value.blocks[1])
            assertEquals(0, recorder.startCalls)
            assertEquals(0, recorder.stopCalls)
            assertEquals(draft, entryDraftRepository.getDraft(draft.id).first().getOrThrow())
        }

    @Test
    fun `explicit mixed draft still focuses its live recording without starting another take`() =
        testScope.runTest {
            val draft = seedMixedDraft()
            val pending = draft.pendingMedia.single()
            val recorder = ObservedRecordingManager(activePath = pending.filePath)
            val viewModel = buildViewModel(pendingAudioRecoverer = missingAudioRecoverer(recorder))
            viewModel.loadDraft(draft.id)
            advanceUntilIdle()

            val state = viewModel.mutableEditorState.value
            val audio = assertMixedDraftRetained(draft, state)
            val recording = assertIs<AudioCaptureState.Recording>(audio.captureState)
            assertEquals(pending.filePath, recording.filePath)
            assertEquals(pending.fallbackFilePath, recording.fallbackFilePath)
            assertEquals(audio.id, state.expandedBlockId)
            assertEquals(EditorFocus.Recording(audio.id), state.editorFocus())
            assertTrue(state.isRecordingAudio)
            assertEquals(0, recorder.startCalls)
            assertEquals(0, recorder.stopCalls)
            assertEquals(draft, entryDraftRepository.getDraft(draft.id).first().getOrThrow())
        }

    private fun missingAudioRecoverer(recorder: AudioRecordingManager): PendingAudioRecoverer =
        DefaultPendingAudioRecoverer(
            durationResolver = AudioDurationResolver { null },
            recordingManager = recorder,
        )

    private suspend fun seedMixedDraft(): EntryDraft {
        val now = Clock.System.now()
        val draftId = Uuid.parse("00000000-0000-0000-0000-000000000021")
        entryDraftRepository.createDraft(
            uid = draftId,
            notes =
                listOf(
                    JournalNote.Text(
                        uid = Uuid.parse("00000000-0000-0000-0000-000000000022"),
                        creationTimestamp = now,
                        lastUpdated = now,
                        content = "Current editor draft retained through native transfer",
                    ),
                    JournalNote.Image(
                        uid = Uuid.parse("00000000-0000-0000-0000-000000000023"),
                        creationTimestamp = now,
                        lastUpdated = now,
                        mediaRef = "/retained-image.png",
                        caption = "Unfinished image caption",
                    ),
                ),
            pendingMedia =
                listOf(
                    PendingMediaRecord(
                        blockId = Uuid.parse("00000000-0000-0000-0000-000000000032"),
                        mediaType = PendingMediaType.AUDIO,
                        createdAt = now,
                        filePath = "/recordings/excluded-retained.m4a",
                        fallbackFilePath = "/recordings/missing-fallback.m4a",
                        timeZoneId = "America/Los_Angeles",
                        position = 1,
                        caption = "Retained recording caption",
                        transcription = "Retained transcription",
                    ),
                ),
            selectedJournalIds = listOf(Uuid.parse("00000000-0000-0000-0000-000000000001")),
        )
        return entryDraftRepository.getDraft(draftId).first().getOrThrow()
    }

    private fun assertMixedDraftRetained(
        draft: EntryDraft,
        state: EditorState,
    ): AudioBlockUiState {
        val text = assertIs<JournalNote.Text>(draft.notes[0])
        val image = assertIs<JournalNote.Image>(draft.notes[1])
        val pending = draft.pendingMedia.single()
        assertEquals(listOf(text.uid, pending.blockId, image.uid), state.blocks.map { it.id })
        assertEquals(text.content, assertIs<TextBlockUiState>(state.blocks[0]).content)
        val retainedImage = assertIs<ImageBlockUiState>(state.blocks[2])
        assertEquals(image.mediaRef, retainedImage.uri)
        assertEquals(image.caption, retainedImage.caption)
        assertEquals(DraftState.Active(draft.id), state.draftState)
        assertEquals(draft.selectedJournalIds, state.selectedJournalIds)
        val audio = assertIs<AudioBlockUiState>(state.blocks[1])
        assertEquals(pending.createdAt, audio.timestamp)
        assertEquals(pending.caption, audio.caption)
        assertEquals(pending.transcription, audio.transcription)
        assertEquals(pending.timeZoneId, audio.timeZoneId)
        assertFalse(audio.startOnOpen)
        return audio
    }

    private class ObservedRecordingManager(
        private val activePath: String? = null,
    ) : AudioRecordingManager {
        var startCalls = 0
            private set
        var stopCalls = 0
            private set

        override val isRecording: Boolean get() = activePath != null
        override val currentRecordingPath: String? get() = activePath

        override suspend fun startRecording(targetNoteId: Uuid?): Boolean {
            startCalls++
            return false
        }

        override suspend fun startRecording(
            targetNoteId: Uuid?,
            onTargetReady: suspend (String) -> Boolean,
        ): Boolean {
            startCalls++
            return false
        }

        override suspend fun stopRecording(): String? {
            stopCalls++
            return activePath
        }

        override fun getAudioLevelFlow() = emptyFlow<Float>()

        override fun getRecordingDurationFlow() = emptyFlow<kotlin.time.Duration>()

        override fun getTranscriptionFlow() = emptyFlow<String?>()

        override fun setTranscriptionService(service: TranscriptionService) {}

        override fun release() {}
    }
}
