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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Session state machine tests for [AndroidAudioRecordingManager] against a fake service.
 *
 * The bugs these guard against: a start that reported success before the recorder ran,
 * a file path handed back before the recorder had finalized it, a singleton that disabled
 * itself after one stop failure, and a stop from the notification that lost the recording.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAudioRecordingManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val controller = FakeRecordingServiceController()

    private fun buildManager(transcription: TranscriptionService? = null): AndroidAudioRecordingManager =
        AndroidAudioRecordingManager(
            audioStorage = FakeAudioStorage(),
            transcriptionRepository = FakeTranscriptionRepository(),
            audioTaggingService = NoopAudioTaggingService,
            audioTagRepository = FakeAudioTagRepository(),
            audioRouteRepository = FakeAudioRouteRepository(),
            serviceController = controller,
            workDispatcher = dispatcher,
            startTimeout = 5.seconds,
        ).also { manager -> transcription?.let(manager::setTranscriptionService) }

    @Test
    fun `start reports success only once the service confirms the recorder is running`() =
        testScope.runTest {
            val manager = buildManager()
            var result: Boolean? = null
            val starter = launch { result = manager.startRecording(Uuid.random()) }
            runCurrent()

            assertNull(result, "Must wait for the service instead of assuming the recorder started")
            assertFalse(manager.isRecording)

            controller.connect(RecordingServiceState(isRecording = true))
            advanceUntilIdle()

            assertEquals(true, result)
            assertTrue(manager.isRecording)
            assertTrue(manager.getRecordingStateFlow().value)
            starter.join()
        }

    @Test
    fun `a service error during start fails the start and leaves the manager usable`() =
        testScope.runTest {
            val manager = buildManager()
            val starter = launch { manager.startRecording() }
            runCurrent()
            controller.connect(RecordingServiceState(isRecording = false, error = "Failed to start recording"))
            advanceUntilIdle()
            starter.join()

            assertFalse(manager.isRecording)
            assertEquals(1, controller.shutdownCalls, "A failed start must release the service")

            // The next attempt is a fresh session, not a permanently wedged singleton.
            val retry = launch { manager.startRecording() }
            runCurrent()
            controller.connect(RecordingServiceState(isRecording = true))
            advanceUntilIdle()
            retry.join()
            assertTrue(manager.isRecording)
        }

    @Test
    fun `a service that never connects times out the start`() =
        testScope.runTest {
            val manager = buildManager()
            var result: Boolean? = null
            val starter = launch { result = manager.startRecording() }
            advanceTimeBy(6.seconds)
            advanceUntilIdle()
            starter.join()

            assertEquals(false, result)
            assertFalse(manager.isRecording)
            assertEquals(1, controller.shutdownCalls)
        }

    @Test
    fun `stop finalizes the file through the binder before the service is torn down`() =
        testScope.runTest {
            val manager = buildManager()
            startAndConfirm(manager)
            controller.stopResult = "/recordings/one.m4a"

            val path = manager.stopRecording()
            advanceUntilIdle()

            assertEquals("/recordings/one.m4a", path)
            assertEquals(listOf("stopRecordingNow", "shutdown"), controller.stopOrder)
            assertFalse(manager.isRecording)
            assertFalse(manager.getRecordingStateFlow().value)
        }

    @Test
    fun `a second start while recording is refused without touching the service`() =
        testScope.runTest {
            val manager = buildManager()
            startAndConfirm(manager)
            val startsBefore = controller.startCalls

            assertFalse(manager.startRecording())
            advanceUntilIdle()

            assertEquals(startsBefore, controller.startCalls)
            assertTrue(manager.isRecording)
        }

    @Test
    fun `a recording the service ended on its own is still handed back by stop`() =
        testScope.runTest {
            val manager = buildManager()
            startAndConfirm(manager)

            // The notification's Stop action finalizes inside the service.
            controller.emit(RecordingServiceState(isRecording = false, recordedFilePath = "/recordings/notif.m4a"))
            advanceUntilIdle()

            assertFalse(manager.getRecordingStateFlow().value, "The editor must learn the session ended")
            controller.stopResult = null

            val path = manager.stopRecording()
            advanceUntilIdle()

            assertEquals("/recordings/notif.m4a", path)
            assertEquals(1, controller.shutdownCalls)
        }

    @Test
    fun `a stop that fails inside the service returns null and the next start works`() =
        testScope.runTest {
            val manager = buildManager()
            startAndConfirm(manager)
            controller.stopThrows = IllegalStateException("stop failed")

            val path = manager.stopRecording()
            advanceUntilIdle()

            assertNull(path)
            assertFalse(manager.isRecording)
            assertEquals(1, controller.shutdownCalls)

            controller.stopThrows = null
            startAndConfirm(manager)
            assertTrue(manager.isRecording)
        }

    @Test
    fun `stopping while idle does not touch the service`() =
        testScope.runTest {
            val manager = buildManager()

            assertNull(manager.stopRecording())
            advanceUntilIdle()

            assertEquals(0, controller.shutdownCalls)
            assertTrue(controller.stopOrder.isEmpty())
        }

    @Test
    fun `stop still returns the file when transcription teardown throws`() =
        testScope.runTest {
            val transcription = FakeTranscriptionService(stopThrows = true)
            val manager = buildManager(transcription)
            startAndConfirm(manager)
            controller.stopResult = "/recordings/two.m4a"

            val path = manager.stopRecording()
            advanceUntilIdle()

            assertEquals("/recordings/two.m4a", path)
            assertFalse(manager.isRecording)
        }

    @Test
    fun `rebinding the same transcription service does not restart its warm-up`() =
        testScope.runTest {
            val transcription = FakeTranscriptionService()
            val manager = buildManager(transcription)
            advanceUntilIdle()
            assertEquals(1, transcription.warmUpCalls)

            manager.setTranscriptionService(transcription)
            manager.setTranscriptionService(transcription)
            advanceUntilIdle()

            assertEquals(1, transcription.warmUpCalls)
        }

    @Test
    fun `start does not wait on transcription setup to report the recorder is running`() =
        testScope.runTest {
            val startGate = CompletableDeferred<Unit>()
            val manager = buildManager(FakeTranscriptionService(startGate = startGate))

            var result: Boolean? = null
            val starter = launch { result = manager.startRecording(Uuid.random()) }
            runCurrent()
            controller.connect(RecordingServiceState(isRecording = true))
            advanceUntilIdle()

            assertEquals(true, result, "Recording must be confirmed without waiting on a second microphone acquisition")
            starter.join()

            startGate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `stop right after start waits for transcription setup before tearing it down`() =
        testScope.runTest {
            val startGate = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            val manager = buildManager(FakeTranscriptionService(startGate = startGate, callOrder = order))
            controller.stopResult = "/recordings/quick.m4a"

            startAndConfirm(manager)
            val stopper = launch { manager.stopRecording() }
            runCurrent()

            assertTrue(order.isEmpty(), "Stop must not race a transcription start still in flight")

            startGate.complete(Unit)
            advanceUntilIdle()
            stopper.join()

            assertEquals(listOf("startLiveTranscription", "stopLiveTranscription"), order)
        }

    private suspend fun TestScope.startAndConfirm(manager: AndroidAudioRecordingManager) {
        val starter = launch { assertTrue(manager.startRecording(Uuid.random())) }
        runCurrent()
        controller.connect(RecordingServiceState(isRecording = true))
        advanceUntilIdle()
        starter.join()
    }
}

private class FakeRecordingServiceController : RecordingServiceController {
    private val state = MutableStateFlow<RecordingServiceState?>(null)
    override val serviceState: StateFlow<RecordingServiceState?> = state.asStateFlow()

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
        state.value = null
    }

    override fun pause(): Boolean = true

    override fun resume(): Boolean = true

    override fun updatePreferredInputDevice(inputDeviceId: String?) = Unit
}

private class FakeAudioStorage : AudioStorage {
    override suspend fun createRecordingTarget(extension: String): AudioRecordingTarget =
        AudioRecordingTarget(path = "/recordings/${Uuid.random()}.$extension")
}

private class FakeAudioRouteRepository : AudioRouteRepository {
    private val selection =
        MediaDeviceSelectionUiState(
            kind = MediaDeviceKind.AUDIO_INPUT,
            devices = emptyList(),
            selectedDeviceId = null,
        )
    override val inputDevices: StateFlow<MediaDeviceSelectionUiState> = MutableStateFlow(selection)
    override val outputDevices: StateFlow<MediaDeviceSelectionUiState> = MutableStateFlow(selection)

    override fun selectInputDevice(deviceId: String) = Unit

    override fun selectOutputDevice(deviceId: String) = Unit
}

private class FakeTranscriptionRepository : TranscriptionRepository {
    override suspend fun requestTranscription(noteId: Uuid): Boolean = true

    override suspend fun getTranscription(noteId: Uuid): TranscriptionData? = null

    override fun observeTranscription(noteId: Uuid): Flow<TranscriptionData?> = flowOf(null)

    override suspend fun getPendingTranscriptions(): List<TranscriptionData> = emptyList()

    override suspend fun updateTranscription(
        noteId: Uuid,
        text: String?,
        status: TranscriptionStatus,
        errorMessage: String?,
    ): Boolean = true

    override suspend fun deleteTranscription(noteId: Uuid): Boolean = true
}

private class FakeAudioTagRepository : AudioTagRepository {
    override suspend fun replaceTagsForNote(
        noteId: Uuid,
        tags: List<AudioTag>,
    ) = Unit

    override suspend fun getTagsForNote(noteId: Uuid): List<AudioTag> = emptyList()

    override fun observeTagsForNote(noteId: Uuid): Flow<List<AudioTag>> = flowOf(emptyList())

    override suspend fun findNotesBySoundName(soundName: String): List<Uuid> = emptyList()
}

private class FakeTranscriptionService(
    private val stopThrows: Boolean = false,
    private val startGate: CompletableDeferred<Unit>? = null,
    private val callOrder: MutableList<String>? = null,
) : TranscriptionService {
    private val results = MutableSharedFlow<TranscriptionResult>(replay = 1)
    var warmUpCalls = 0

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = results

    override suspend fun startLiveTranscription(): TranscriptionStartResult {
        startGate?.await()
        callOrder?.add("startLiveTranscription")
        return TranscriptionStartResult.Started
    }

    override suspend fun stopLiveTranscription(): TranscriptionResult {
        callOrder?.add("stopLiveTranscription")
        if (stopThrows) throw IllegalStateException("transcription stop failed")
        return TranscriptionResult.Cancelled
    }

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult = TranscriptionResult.Cancelled

    override suspend fun cancelTranscription() = Unit

    override fun getSupportedLanguages(): List<String> = listOf("en-US")

    override fun setLanguage(languageCode: String) = Unit

    override val supportsLiveTranscription: Boolean = true

    override val supportsFileTranscription: Boolean = false

    override suspend fun resetTranscription() = Unit

    override suspend fun warmUp() {
        warmUpCalls++
    }

    override fun release() = Unit
}
