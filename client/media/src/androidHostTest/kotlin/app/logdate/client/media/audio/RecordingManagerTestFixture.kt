package app.logdate.client.media.audio

import app.logdate.client.media.audio.tagging.NoopAudioTaggingService
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionStartResult
import app.logdate.client.media.device.AudioRouteRepository
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.repository.audio.AudioTag
import app.logdate.client.repository.audio.AudioTagRepository
import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import app.logdate.client.text.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class RecordingManagerTestFixture {
    protected val dispatcher = StandardTestDispatcher()
    protected val testScope = TestScope(dispatcher)
    protected val controller = FakeRecordingServiceController()

    protected fun buildManager(
        transcription: TranscriptionService? = null,
        repository: TranscriptionRepository = FakeTranscriptionRepository(),
        storage: AudioStorage = FakeAudioStorage(),
        routes: FakeAudioRouteRepository = FakeAudioRouteRepository(),
        draftSession: RecordingDraftLifecycle? = null,
        quietSaveEnabled: suspend () -> Boolean = { false },
        onQuietSave: suspend (Uuid) -> Unit = {},
        finalizedFileAvailable: (String) -> Boolean = { true },
    ): AndroidAudioRecordingManager =
        AndroidAudioRecordingManager(
            audioStorage = storage,
            transcriptionRepository = repository,
            audioTaggingService = NoopAudioTaggingService,
            audioTagRepository = FakeAudioTagRepository(),
            audioRouteRepository = routes,
            serviceController = controller,
            workDispatcher = dispatcher,
            startTimeout = 5.seconds,
            draftSession = draftSession,
            quietSaveEnabled = quietSaveEnabled,
            onQuietSave = onQuietSave,
            finalizedFileAvailable = finalizedFileAvailable,
        ).also { manager -> transcription?.let(manager::setTranscriptionService) }

    protected suspend fun TestScope.startAndConfirm(
        manager: AndroidAudioRecordingManager,
        noteId: Uuid = Uuid.random(),
    ) {
        val starter = launch { assertTrue(manager.startRecording(noteId)) }
        runCurrent()
        controller.connect(RecordingServiceState(isRecording = true))
        advanceUntilIdle()
        starter.join()
    }
}

internal class FakeRecordingServiceController : RecordingServiceController {
    private val state = MutableStateFlow<RecordingServiceState?>(null)
    override val serviceState: StateFlow<RecordingServiceState?> = state.asStateFlow()

    val quietSaveConfigurations = mutableListOf<Boolean>()

    override fun configureQuietSave(enabled: Boolean) {
        quietSaveConfigurations += enabled
    }

    var microphoneActive = false
    var startCalls = 0
    var shutdownCalls = 0
    var stopResult: String? = "/recordings/default.m4a"
    var stopThrows: Exception? = null
    val stopOrder = mutableListOf<String>()

    override fun start(
        outputPath: String,
        inputDeviceId: String?,
    ): Boolean {
        startCalls++
        microphoneActive = true
        return true
    }

    /** Simulates the service connecting with [initial] as its current state. */
    fun connect(initial: RecordingServiceState) {
        state.value = initial
    }

    fun emit(next: RecordingServiceState) {
        state.value = next
    }

    override fun stopRecordingNow(): String? {
        stopOrder += "stopRecordingNow"
        stopThrows?.let { throw it }
        val current = state.value
        if (current != null && current.isRecording) {
            state.value = current.copy(isRecording = false, recordedFilePath = stopResult)
        }
        return stopResult
    }

    override fun shutdown() {
        stopOrder += "shutdown"
        shutdownCalls++
        microphoneActive = false
        state.value = null
    }

    override fun pause(): Boolean = true

    override fun resume(): Boolean = true

    val inputRequests = mutableListOf<String?>()
    var inputUpdateThrows: Exception? = null

    override fun updatePreferredInputDevice(inputDeviceId: String?) {
        inputUpdateThrows?.let { throw it }
        inputRequests += inputDeviceId
    }
}

internal class FakeAudioStorage : AudioStorage {
    override suspend fun createRecordingTarget(extension: String): AudioRecordingTarget =
        AudioRecordingTarget(path = "/recordings/${Uuid.random()}.$extension")
}

internal class FakeAudioRouteRepository : AudioRouteRepository {
    private val selection =
        MediaDeviceSelectionUiState(
            kind = MediaDeviceKind.AUDIO_INPUT,
            devices = emptyList(),
            selectedDeviceId = null,
        )
    override val inputDevices: StateFlow<MediaDeviceSelectionUiState> = MutableStateFlow(selection)
    override val outputDevices: StateFlow<MediaDeviceSelectionUiState> = MutableStateFlow(selection)

    private val requested = MutableSharedFlow<String?>(replay = 1).apply { tryEmit(null) }
    override val requestedInputDeviceIds: SharedFlow<String?> = requested
    var reported: Triple<String?, Boolean, UiText?>? = null

    override fun updateRecordingInputDevice(
        deviceId: String?,
        isRecording: Boolean,
        error: UiText?,
    ) {
        reported = Triple(deviceId, isRecording, error)
    }

    override fun selectInputDevice(deviceId: String) {
        requested.tryEmit(deviceId)
    }

    override fun selectOutputDevice(deviceId: String) = Unit
}

internal class FakeTranscriptionRepository : TranscriptionRepository {
    val nextUpdate = CompletableDeferred<Pair<Uuid, String?>>()

    override suspend fun requestTranscription(noteId: Uuid): Boolean = true

    override suspend fun getTranscription(noteId: Uuid): TranscriptionData? = null

    override fun observeTranscription(noteId: Uuid): Flow<TranscriptionData?> = flowOf(null)

    override suspend fun getPendingTranscriptions(): List<TranscriptionData> = emptyList()

    override suspend fun updateTranscription(
        noteId: Uuid,
        text: String?,
        status: TranscriptionStatus,
        errorMessage: String?,
    ): Boolean {
        nextUpdate.complete(noteId to text)
        return true
    }

    override suspend fun deleteTranscription(noteId: Uuid): Boolean = true
}

internal class FakeAudioTagRepository : AudioTagRepository {
    override suspend fun replaceTagsForNote(
        noteId: Uuid,
        tags: List<AudioTag>,
    ) = Unit

    override suspend fun getTagsForNote(noteId: Uuid): List<AudioTag> = emptyList()

    override fun observeTagsForNote(noteId: Uuid): Flow<List<AudioTag>> = flowOf(emptyList())

    override suspend fun findNotesBySoundName(soundName: String): List<Uuid> = emptyList()
}

internal class FakeTranscriptionService(
    private val stopThrows: Boolean = false,
    private val startGate: CompletableDeferred<Unit>? = null,
    private val stopGate: CompletableDeferred<Unit>? = null,
    private val callOrder: MutableList<String>? = null,
    private val fileRecovery: CompletableDeferred<TranscriptionResult>? = null,
    private val fileRecoveries: Map<String, CompletableDeferred<TranscriptionResult>> = emptyMap(),
    private val liveStartResult: TranscriptionStartResult = TranscriptionStartResult.Started,
    private val liveStopResult: TranscriptionResult = TranscriptionResult.Cancelled,
    private val allowFileRecovery: Boolean = false,
    private val fallbackResult: TranscriptionResult = TranscriptionResult.Cancelled,
) : TranscriptionService {
    private val results = MutableSharedFlow<TranscriptionResult>(replay = 1)
    var warmUpCalls = 0
    val inputRequests = mutableListOf<String?>()

    var inputRoutingAccepted = true
    var fileTranscriptionCalls = 0

    override fun updatePreferredInputDevice(deviceId: String?): Boolean {
        inputRequests += deviceId
        return inputRoutingAccepted
    }

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = results

    suspend fun emit(result: TranscriptionResult) {
        results.emit(result)
    }

    override suspend fun startLiveTranscription(): TranscriptionStartResult {
        startGate?.await()
        callOrder?.add("startLiveTranscription")
        return liveStartResult
    }

    override suspend fun stopLiveTranscription(): TranscriptionResult {
        stopGate?.await()
        callOrder?.add("stopLiveTranscription")
        if (stopThrows) throw IllegalStateException("transcription stop failed")
        return liveStopResult
    }

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult {
        fileTranscriptionCalls++
        return if (audioUri in fileRecoveries) {
            fileRecoveries.getValue(audioUri).await()
        } else if (audioUri == "/recordings/first.m4a") {
            fileRecovery?.await() ?: TranscriptionResult.Cancelled
        } else {
            fallbackResult
        }
    }

    override suspend fun cancelTranscription() = Unit

    override fun getSupportedLanguages(): List<String> = listOf("en-US")

    override fun setLanguage(languageCode: String) = Unit

    override val supportsLiveTranscription: Boolean = fileRecovery == null && fileRecoveries.isEmpty()

    override val supportsFileTranscription: Boolean = allowFileRecovery || fileRecovery != null || fileRecoveries.isNotEmpty()

    override suspend fun resetTranscription() = Unit

    override suspend fun warmUp() {
        warmUpCalls++
    }

    override fun release() = Unit
}
