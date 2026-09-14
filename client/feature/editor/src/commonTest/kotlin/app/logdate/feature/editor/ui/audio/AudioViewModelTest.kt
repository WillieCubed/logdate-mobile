package app.logdate.feature.editor.ui.audio

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.tagging.NoopAudioTaggingService
import app.logdate.client.media.audio.transcription.TimedTranscript
import app.logdate.client.media.audio.transcription.TimedUtterance
import app.logdate.client.media.audio.transcription.TimedWord
import app.logdate.client.media.audio.transcription.TranscriptionFailure
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionStartResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Unit tests for [AudioViewModel].
 *
 * This test suite validates the audio recording and playback state management,
 * ensuring that UI states correctly reflect the recording progress, transcription
 * updates, and playback control actions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `start recording updates ui state`() =
        runTest(dispatcher) {
            val recordingManager =
                FakeAudioRecordingManager(
                    outputUri = "file:///test/audio.m4a",
                    initialDuration = 5.seconds,
                    initialLevel = 0.5f,
                )
            val viewModel =
                AudioViewModel(
                    audioRecordingManager = recordingManager,
                    audioPlaybackManager = FakeAudioPlaybackManager(),
                    audioDurationResolver = FakeAudioDurationResolver(),
                    transcriptionService = FakeTranscriptionService(),
                    audioTaggingService = NoopAudioTaggingService,
                )

            viewModel.startRecording()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isRecording, "Should be recording")
            assertEquals(5.seconds, state.duration, "Should reflect emitted duration")
            assertTrue(state.audioLevels.isNotEmpty(), "Should collect audio levels")
        }

    @Test
    fun `stop recording stores recorded uri`() =
        runTest(dispatcher) {
            val recordingManager =
                FakeAudioRecordingManager(
                    outputUri = "file:///test/audio.m4a",
                    initialDuration = 3.seconds,
                    initialLevel = 0.2f,
                )
            val viewModel =
                AudioViewModel(
                    audioRecordingManager = recordingManager,
                    audioPlaybackManager = FakeAudioPlaybackManager(),
                    audioDurationResolver = FakeAudioDurationResolver(),
                    transcriptionService = FakeTranscriptionService(),
                    audioTaggingService = NoopAudioTaggingService,
                )

            viewModel.startRecording()
            advanceUntilIdle()
            viewModel.stopRecording()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRecording, "Should stop recording")
            assertEquals("file:///test/audio.m4a", state.recordedAudioUri, "Should store recorded URI")
        }

    @Test
    fun `transcription flow updates ui state`() =
        runTest(dispatcher) {
            val recordingManager =
                FakeAudioRecordingManager(
                    outputUri = "file:///test/audio.m4a",
                    initialDuration = 1.seconds,
                    initialLevel = 0.1f,
                )
            val viewModel =
                AudioViewModel(
                    audioRecordingManager = recordingManager,
                    audioPlaybackManager = FakeAudioPlaybackManager(),
                    audioDurationResolver = FakeAudioDurationResolver(),
                    transcriptionService = FakeTranscriptionService(),
                    audioTaggingService = NoopAudioTaggingService,
                )

            viewModel.startRecording()
            advanceUntilIdle()

            recordingManager.emitStructuredTranscription(
                TranscriptionResult.Success(
                    text = "Hello world",
                    timedTranscript = null,
                    isFinal = false,
                ),
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val transcriptionState = state.transcriptionState
            assertTrue(
                transcriptionState is AudioUiState.TranscriptionState.Success,
                "Should update transcription state on new text",
            )
            val successState = transcriptionState
            assertEquals("Hello world", successState.text)
        }

    @Test
    fun `a transcription start failure is not overwritten by optimistic progress`() =
        runTest(dispatcher) {
            val recordingManager =
                FakeAudioRecordingManager(
                    outputUri = "file:///test/audio.m4a",
                    initialDuration = 1.seconds,
                    initialLevel = 0.1f,
                    startResult = TranscriptionResult.Error(TranscriptionFailure.NotAvailable),
                )
            val viewModel =
                AudioViewModel(
                    audioRecordingManager = recordingManager,
                    audioPlaybackManager = FakeAudioPlaybackManager(),
                    audioDurationResolver = FakeAudioDurationResolver(),
                    transcriptionService = FakeTranscriptionService(),
                    audioTaggingService = NoopAudioTaggingService,
                )

            viewModel.startRecording()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isRecording)
            assertEquals(
                AudioUiState.TranscriptionState.Error(TranscriptionFailure.NotAvailable),
                viewModel.uiState.value.transcriptionState,
            )
        }

    @Test
    fun `structured transcription preserves timed transcript`() =
        runTest(dispatcher) {
            val recordingManager =
                FakeAudioRecordingManager(
                    outputUri = "file:///test/audio.m4a",
                    initialDuration = 1.seconds,
                    initialLevel = 0.1f,
                )
            val viewModel =
                AudioViewModel(
                    audioRecordingManager = recordingManager,
                    audioPlaybackManager = FakeAudioPlaybackManager(),
                    audioDurationResolver = FakeAudioDurationResolver(),
                    transcriptionService = FakeTranscriptionService(),
                    audioTaggingService = NoopAudioTaggingService,
                )

            viewModel.startRecording()
            advanceUntilIdle()

            val timedTranscript =
                TimedTranscript(
                    utterances =
                        listOf(
                            TimedUtterance(
                                text = "Hello world.",
                                startMs = 0,
                                endMs = 1000,
                                words =
                                    listOf(
                                        TimedWord("Hello", "hello", 0, 500),
                                        TimedWord("world", "world", 500, 1000),
                                    ),
                            ),
                        ),
                )

            recordingManager.emitStructuredTranscription(
                TranscriptionResult.Success(
                    text = timedTranscript.plainText,
                    timedTranscript = timedTranscript,
                    isFinal = true,
                ),
            )
            advanceUntilIdle()

            val transcriptionState = viewModel.uiState.value.transcriptionState
            val successState = assertNotNull(transcriptionState as? AudioUiState.TranscriptionState.Success)
            assertEquals(timedTranscript, successState.timedTranscript)
            assertTrue(successState.isFinal)
        }

    private fun buildViewModel(recordingManager: FakeAudioRecordingManager): AudioViewModel =
        AudioViewModel(
            audioRecordingManager = recordingManager,
            audioPlaybackManager = FakeAudioPlaybackManager(),
            audioDurationResolver = FakeAudioDurationResolver(),
            transcriptionService = FakeTranscriptionService(),
            audioTaggingService = NoopAudioTaggingService,
        )

    @Test
    fun `tapping record twice starts a single recording`() =
        runTest(dispatcher) {
            val recordingManager =
                FakeAudioRecordingManager(
                    outputUri = "file:///test/audio.m4a",
                    initialDuration = Duration.ZERO,
                    initialLevel = 0f,
                )
            val gate = CompletableDeferred<Unit>()
            recordingManager.startGate = gate
            val viewModel = buildViewModel(recordingManager)
            val blockId = Uuid.random()

            viewModel.startRecording(blockId)
            viewModel.startRecording(blockId)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isStartingRecording, "Should report the start in flight")
            assertFalse(viewModel.uiState.value.isRecording)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, recordingManager.startCalls, "Second tap must not start a second session")
            assertTrue(viewModel.uiState.value.isRecording)
            assertFalse(viewModel.uiState.value.isStartingRecording)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `a start the platform rejects returns to idle with an error`() =
        runTest(dispatcher) {
            val recordingManager =
                FakeAudioRecordingManager(
                    outputUri = "file:///test/audio.m4a",
                    initialDuration = Duration.ZERO,
                    initialLevel = 0f,
                    startSucceeds = false,
                )
            val viewModel = buildViewModel(recordingManager)

            viewModel.startRecording(Uuid.random())
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRecording)
            assertFalse(state.isStartingRecording)
            assertNotNull(state.error, "The user must be told the recording did not start")
            assertNull(state.recordingTargetNoteId)
        }

    @Test
    fun `stop while idle does not reach the platform`() =
        runTest(dispatcher) {
            val recordingManager =
                FakeAudioRecordingManager(
                    outputUri = "file:///test/audio.m4a",
                    initialDuration = Duration.ZERO,
                    initialLevel = 0f,
                )
            val viewModel = buildViewModel(recordingManager)

            viewModel.stopRecording()
            advanceUntilIdle()

            assertEquals(0, recordingManager.stopCalls)
            assertFalse(viewModel.uiState.value.isRecording)
        }

    @Test
    fun `a stop that yields no file clears the session and flags the block`() =
        runTest(dispatcher) {
            val recordingManager =
                FakeAudioRecordingManager(
                    outputUri = null,
                    initialDuration = Duration.ZERO,
                    initialLevel = 0f,
                )
            val viewModel = buildViewModel(recordingManager)
            val blockId = Uuid.random()

            viewModel.startRecording(blockId)
            advanceUntilIdle()
            viewModel.stopRecording()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRecording)
            assertNull(state.recordedAudioUri)
            assertEquals(blockId, state.failedRecordingTargetNoteId)
            assertNotNull(state.error)

            // The next start clears the failure so the block can record again.
            viewModel.startRecording(blockId)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.failedRecordingTargetNoteId)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `a recording ended outside the editor is finalized`() =
        runTest(dispatcher) {
            val recordingManager =
                FakeAudioRecordingManager(
                    outputUri = "file:///test/audio.m4a",
                    initialDuration = 2.seconds,
                    initialLevel = 0f,
                )
            val viewModel = buildViewModel(recordingManager)
            val blockId = Uuid.random()

            viewModel.startRecording(blockId)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isRecording)

            recordingManager.endRecordingExternally()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRecording, "The editor must follow a stop it did not initiate")
            assertEquals("file:///test/audio.m4a", state.recordedAudioUri)
            assertEquals(1, recordingManager.stopCalls, "Finalizing collects the file through the manager")
        }

    @Test
    fun `a new view model does not adopt a previous session's transcript`() =
        runTest(dispatcher) {
            val recordingManager =
                FakeAudioRecordingManager(
                    outputUri = "file:///test/audio.m4a",
                    initialDuration = Duration.ZERO,
                    initialLevel = 0f,
                )
            recordingManager.emitStructuredTranscription(
                TranscriptionResult.Success(text = "Words from another block", isFinal = true),
            )

            val viewModel = buildViewModel(recordingManager)
            advanceUntilIdle()

            assertEquals(
                AudioUiState.TranscriptionState.NotRequested,
                viewModel.uiState.value.transcriptionState,
            )
        }

    @Test
    fun `seek to position ms uses absolute seek and updates playback progress`() =
        runTest(dispatcher) {
            val playbackManager = FakeAudioPlaybackManager()
            val viewModel =
                AudioViewModel(
                    audioRecordingManager =
                        FakeAudioRecordingManager(
                            outputUri = "file:///test/audio.m4a",
                            initialDuration = 4.seconds,
                            initialLevel = 0.1f,
                        ),
                    audioPlaybackManager = playbackManager,
                    audioDurationResolver = FakeAudioDurationResolver(),
                    transcriptionService = FakeTranscriptionService(),
                    audioTaggingService = NoopAudioTaggingService,
                )

            viewModel.seekToPositionMs(positionMs = 2_500L, durationMs = 10_000L)
            advanceUntilIdle()

            assertEquals(2_500L, playbackManager.lastSeekPositionMs)
            assertEquals(10_000L, playbackManager.lastSeekDurationMs)
            assertEquals(0.25f, viewModel.uiState.value.playbackProgress)
        }
}

private class FakeAudioRecordingManager(
    private val outputUri: String?,
    initialDuration: Duration,
    initialLevel: Float,
    private val startResult: TranscriptionResult? = null,
    private val startSucceeds: Boolean = true,
) : AudioRecordingManager {
    private val audioLevelFlow = MutableStateFlow(initialLevel)
    private val durationFlow = MutableStateFlow(initialDuration)
    private val transcriptionFlow = MutableStateFlow<String?>(null)
    private val structuredFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)
    private val recordingStateFlow = MutableStateFlow(false)

    /** When set, [startRecording] suspends until it completes so tests can observe the in-flight state. */
    var startGate: CompletableDeferred<Unit>? = null
    var startCalls: Int = 0
        private set
    var stopCalls: Int = 0
        private set

    override var isRecording: Boolean = false
        private set

    override suspend fun startRecording(targetNoteId: kotlin.uuid.Uuid?): Boolean {
        startCalls++
        startGate?.await()
        if (!startSucceeds) return false
        isRecording = true
        recordingStateFlow.value = true
        startResult?.let { structuredFlow.emit(it) }
        return true
    }

    override suspend fun stopRecording(): String? {
        stopCalls++
        isRecording = false
        recordingStateFlow.value = false
        return outputUri
    }

    override fun getRecordingStateFlow(): Flow<Boolean> = recordingStateFlow

    /** Simulates the platform ending the recording outside the editor (for example a notification action). */
    fun endRecordingExternally() {
        isRecording = false
        recordingStateFlow.value = false
    }

    override fun getAudioLevelFlow(): Flow<Float> = audioLevelFlow

    override fun getRecordingDurationFlow(): Flow<Duration> = durationFlow

    override fun getTranscriptionFlow(): Flow<String?> = transcriptionFlow

    override fun getStructuredTranscriptionFlow(): Flow<TranscriptionResult> = structuredFlow.asSharedFlow()

    fun emitTranscription(text: String?) {
        transcriptionFlow.value = text
    }

    suspend fun emitStructuredTranscription(result: TranscriptionResult) {
        structuredFlow.emit(result)
    }

    override fun setTranscriptionService(service: TranscriptionService) {
        // No-op for tests.
    }

    override fun release() {
        isRecording = false
    }
}

private class FakeAudioPlaybackManager : AudioPlaybackManager {
    var lastSeekRatio: Float? = null
    var lastSeekPositionMs: Long? = null
    var lastSeekDurationMs: Long? = null

    override fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata?,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit,
    ) {
        onProgressUpdated(0f)
    }

    override fun pausePlayback() = Unit

    override fun stopPlayback() = Unit

    override fun seekTo(position: Float) {
        lastSeekRatio = position
    }

    override fun seekTo(
        positionMs: Long,
        durationMs: Long,
    ) {
        lastSeekPositionMs = positionMs
        lastSeekDurationMs = durationMs
    }

    override fun release() = Unit
}

private class FakeAudioDurationResolver : AudioDurationResolver {
    override suspend fun resolveDurationMs(uri: String): Long? = null
}

private class FakeTranscriptionService : TranscriptionService {
    private val transcriptionFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = transcriptionFlow

    override suspend fun startLiveTranscription(): TranscriptionStartResult = TranscriptionStartResult.Started

    override suspend fun stopLiveTranscription(): TranscriptionResult = TranscriptionResult.Cancelled

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult = TranscriptionResult.Success("Test transcription")

    override suspend fun cancelTranscription() = Unit

    override fun getSupportedLanguages(): List<String> = emptyList()

    override fun setLanguage(languageCode: String) = Unit

    override val supportsLiveTranscription: Boolean = true

    override val supportsFileTranscription: Boolean = true

    override suspend fun resetTranscription() = Unit

    override fun release() = Unit
}
