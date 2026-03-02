package app.logdate.feature.editor.ui.audio

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    fun startRecordingUpdatesUiState() =
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
                )

            viewModel.startRecording()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isRecording, "Should be recording")
            assertEquals(5.seconds, state.duration, "Should reflect emitted duration")
            assertTrue(state.audioLevels.isNotEmpty(), "Should collect audio levels")
        }

    @Test
    fun stopRecordingStoresRecordedUri() =
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
    fun transcriptionFlowUpdatesUiState() =
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
                )

            viewModel.startRecording()
            advanceUntilIdle()

            recordingManager.emitTranscription("Hello world")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val transcriptionState = state.transcriptionState
            assertTrue(
                transcriptionState is AudioUiState.TranscriptionState.Success,
                "Should update transcription state on new text",
            )
            assertEquals("Hello world", (transcriptionState as AudioUiState.TranscriptionState.Success).text)
        }
}

private class FakeAudioRecordingManager(
    private val outputUri: String,
    initialDuration: Duration,
    initialLevel: Float,
) : AudioRecordingManager {
    private val audioLevelFlow = MutableStateFlow(initialLevel)
    private val durationFlow = MutableStateFlow(initialDuration)
    private val transcriptionFlow = MutableStateFlow<String?>(null)

    override var isRecording: Boolean = false
        private set

    override suspend fun startRecording(): Boolean {
        isRecording = true
        return true
    }

    override suspend fun stopRecording(): String? {
        isRecording = false
        return outputUri
    }

    override fun getAudioLevelFlow(): Flow<Float> = audioLevelFlow

    override fun getRecordingDurationFlow(): Flow<Duration> = durationFlow

    override fun getTranscriptionFlow(): Flow<String?> = transcriptionFlow

    fun emitTranscription(text: String?) {
        transcriptionFlow.value = text
    }

    override fun setTranscriptionService(service: TranscriptionService) {
        // No-op for tests.
    }

    override fun release() {
        isRecording = false
    }
}

private class FakeAudioPlaybackManager : AudioPlaybackManager {
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

    override fun seekTo(position: Float) = Unit

    override fun release() = Unit
}

private class FakeAudioDurationResolver : AudioDurationResolver {
    override suspend fun resolveDurationMs(uri: String): Long? = null
}

private class FakeTranscriptionService : TranscriptionService {
    private val transcriptionFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = transcriptionFlow

    override suspend fun startLiveTranscription(): Boolean = true

    override suspend fun stopLiveTranscription() = Unit

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult = TranscriptionResult.Success("Test transcription")

    override fun cancelTranscription() = Unit

    override fun getSupportedLanguages(): List<String> = emptyList()

    override fun setLanguage(languageCode: String) = Unit

    override val supportsLiveTranscription: Boolean = true

    override val supportsFileTranscription: Boolean = true

    override fun release() = Unit
}
