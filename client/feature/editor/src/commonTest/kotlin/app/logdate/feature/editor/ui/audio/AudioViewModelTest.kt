package app.logdate.feature.editor.ui.audio

import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

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
    fun startRecordingUpdatesUiState() = runTest(dispatcher) {
        val recordingManager = FakeAudioRecordingManager(
            outputUri = "file:///test/audio.m4a",
            initialDuration = 5.seconds,
            initialLevel = 0.5f
        )
        val viewModel = AudioViewModel(
            audioRecordingManager = recordingManager,
            audioPlaybackManager = FakeAudioPlaybackManager(),
            transcriptionRepository = FakeTranscriptionRepository()
        )

        viewModel.startRecording()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isRecording, "Should be recording")
        assertEquals(5.seconds, state.duration, "Should reflect emitted duration")
        assertTrue(state.audioLevels.isNotEmpty(), "Should collect audio levels")
    }

    @Test
    fun stopRecordingStoresRecordedUri() = runTest(dispatcher) {
        val recordingManager = FakeAudioRecordingManager(
            outputUri = "file:///test/audio.m4a",
            initialDuration = 3.seconds,
            initialLevel = 0.2f
        )
        val viewModel = AudioViewModel(
            audioRecordingManager = recordingManager,
            audioPlaybackManager = FakeAudioPlaybackManager(),
            transcriptionRepository = FakeTranscriptionRepository()
        )

        viewModel.startRecording()
        advanceUntilIdle()
        viewModel.stopRecording()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording, "Should stop recording")
        assertEquals("file:///test/audio.m4a", state.recordedAudioUri, "Should store recorded URI")
    }
}

private class FakeAudioRecordingManager(
    private val outputUri: String,
    initialDuration: Duration,
    initialLevel: Float
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
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit
    ) {
        onProgressUpdated(0f)
    }

    override fun pausePlayback() = Unit

    override fun stopPlayback() = Unit

    override fun seekTo(position: Float) = Unit

    override fun release() = Unit
}

private class FakeTranscriptionRepository : TranscriptionRepository {
    private val transcriptions = mutableMapOf<Uuid, MutableStateFlow<TranscriptionData?>>()

    override suspend fun requestTranscription(noteId: Uuid): Boolean = true

    override suspend fun getTranscription(noteId: Uuid): TranscriptionData? =
        transcriptions[noteId]?.value

    override fun observeTranscription(noteId: Uuid): Flow<TranscriptionData?> =
        transcriptions.getOrPut(noteId) { MutableStateFlow(null) }.asStateFlow()

    override suspend fun getPendingTranscriptions(): List<TranscriptionData> = emptyList()

    override suspend fun updateTranscription(
        noteId: Uuid,
        text: String?,
        status: TranscriptionStatus,
        errorMessage: String?
    ): Boolean {
        val now = Clock.System.now()
        val current = transcriptions.getOrPut(noteId) { MutableStateFlow(null) }
        current.value = TranscriptionData(
            noteId = noteId,
            text = text,
            status = status,
            errorMessage = errorMessage,
            created = now,
            lastUpdated = now,
            id = Uuid.random()
        )
        return true
    }

    override suspend fun deleteTranscription(noteId: Uuid): Boolean {
        transcriptions.remove(noteId)
        return true
    }
}
