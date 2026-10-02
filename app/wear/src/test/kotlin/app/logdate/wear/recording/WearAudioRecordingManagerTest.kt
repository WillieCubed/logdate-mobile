package app.logdate.wear.recording

import app.logdate.client.media.audio.AudioRecordingTarget
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.media.audio.RecordingServiceController
import app.logdate.client.media.audio.RecordingServiceState
import app.logdate.client.media.device.AudioRouteRepository
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.wear.data.storage.StorageSpaceChecker
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests [WearAudioRecordingManager]'s session handling against a fake foreground service.
 *
 * The invariants: a start is reported only once the service confirms the recorder is running, a
 * stop returns only after the file is finalized, a session the service ends on its own (the
 * length limit) still hands back the recording it made, and a failed start says why.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearAudioRecordingManagerTest {
    private val controller = FakeRecordingServiceController()
    private val storageChecker = mockk<StorageSpaceChecker>()
    private val recordingFile = File.createTempFile("wear-recording", ".m4a").also { it.deleteOnExit() }
    private val audioStorage =
        object : AudioStorage {
            override suspend fun createRecordingTarget(extension: String) = AudioRecordingTarget(recordingFile.absolutePath)
        }
    private val audioRouteRepository = mockk<AudioRouteRepository>(relaxed = true)
    private var microphoneGranted = true

    init {
        coEvery { storageChecker.getAvailableStorageSpace() } returns 100L * 1024 * 1024
        coEvery { audioRouteRepository.inputDevices } returns
            MutableStateFlow(MediaDeviceSelectionUiState(MediaDeviceKind.AUDIO_INPUT, emptyList(), selectedDeviceId = null))
    }

    private fun TestScope.manager(startTimeout: Duration = 5.seconds) =
        WearAudioRecordingManager(
            storageChecker = storageChecker,
            audioStorage = audioStorage,
            audioRouteRepository = audioRouteRepository,
            serviceController = controller,
            microphonePermission = { microphoneGranted },
            workDispatcher = StandardTestDispatcher(testScheduler),
            startTimeout = startTimeout,
        )

    private fun confirmRecorderStarts() {
        controller.onStart = { controller.emit(RecordingServiceState(isRecording = true)) }
    }

    @Test
    fun `start reports success once the service confirms the recorder is running`() =
        runTest {
            confirmRecorderStarts()

            val started = manager().start()

            assertTrue(started)
            assertEquals(listOf(recordingFile.absolutePath), controller.startedPaths)
        }

    @Test
    fun `start fails and shuts the service down when the service reports an error`() =
        runTest {
            controller.onStart = { controller.emit(RecordingServiceState(isRecording = false, error = "microphone busy")) }
            val manager = manager()

            val started = manager.start()

            assertFalse(started)
            assertFalse(manager.isRecording.value)
            assertEquals(1, controller.shutdowns)
        }

    @Test
    fun `start fails when the service never confirms within the timeout`() =
        runTest {
            val manager = manager(startTimeout = 2.seconds)

            val started = manager.start()

            assertFalse(started)
            assertFalse(manager.isRecording.value)
            assertEquals(1, controller.shutdowns)
        }

    @Test
    fun `start fails when the service cannot be started`() =
        runTest {
            controller.startResult = false
            val manager = manager()

            assertFalse(manager.start())
            assertEquals(1, controller.shutdowns)
        }

    @Test
    fun `start fails without touching the service when storage is low`() =
        runTest {
            coEvery { storageChecker.getAvailableStorageSpace() } returns 1024L

            val started = manager().start()

            assertFalse(started)
            assertTrue(controller.startedPaths.isEmpty())
        }

    @Test
    fun `start reports low storage as the reason`() =
        runTest {
            coEvery { storageChecker.getAvailableStorageSpace() } returns 1024L
            val manager = manager()

            manager.start()

            assertEquals(RecordingStartFailure.NOT_ENOUGH_STORAGE, manager.lastStartFailure)
        }

    @Test
    fun `start needs room for the raw recording and the wrapped copy`() =
        runTest {
            val oneCopy = WearAudioRecordingManager.MAX_RECORDING_DURATION.inWholeSeconds * 128_000L / 8
            coEvery { storageChecker.getAvailableStorageSpace() } returns oneCopy + 8L * 1024 * 1024
            val manager = manager()

            assertFalse(manager.start())

            assertEquals(RecordingStartFailure.NOT_ENOUGH_STORAGE, manager.lastStartFailure)
        }

    @Test
    fun `start requires room for a full length recording`() =
        runTest {
            coEvery { storageChecker.getAvailableStorageSpace() } returns WearAudioRecordingManager.REQUIRED_STORAGE_BYTES - 1
            val manager = manager()

            assertFalse(manager.start())
            assertEquals(RecordingStartFailure.NOT_ENOUGH_STORAGE, manager.lastStartFailure)
        }

    @Test
    fun `start is refused without the microphone permission`() =
        runTest {
            microphoneGranted = false
            val manager = manager()

            val started = manager.start()

            assertFalse(started)
            assertEquals(RecordingStartFailure.MICROPHONE_PERMISSION_DENIED, manager.lastStartFailure)
            assertTrue(controller.startedPaths.isEmpty())
        }

    @Test
    fun `start reports the recorder as the reason when the service does not confirm`() =
        runTest {
            val manager = manager(startTimeout = 2.seconds)

            manager.start()

            assertEquals(RecordingStartFailure.RECORDER_UNAVAILABLE, manager.lastStartFailure)
        }

    @Test
    fun `a successful start clears the previous failure`() =
        runTest {
            microphoneGranted = false
            val manager = manager()
            manager.start()
            microphoneGranted = true
            confirmRecorderStarts()

            assertTrue(manager.start())
            assertNull(manager.lastStartFailure)
        }

    @Test
    fun `a failed start removes the file it created`() =
        runTest {
            recordingFile.writeText("partial")
            controller.onStart = { controller.emit(RecordingServiceState(isRecording = false, error = "microphone busy")) }

            manager().start()

            assertFalse(recordingFile.exists())
        }

    @Test
    fun `start while already recording is refused`() =
        runTest {
            confirmRecorderStarts()
            val manager = manager()
            manager.start()

            assertFalse(manager.start())
            assertEquals(1, controller.startedPaths.size)
        }

    @Test
    fun `stop returns the path the service finalized and shuts the service down`() =
        runTest {
            confirmRecorderStarts()
            controller.stopPath = "/audio_notes/final.m4a"
            val manager = manager()
            manager.start()

            val path = manager.stop()

            assertEquals("/audio_notes/final.m4a", path)
            assertFalse(manager.isRecording.value)
            assertEquals(1, controller.shutdowns)
            assertEquals(1, controller.finalizations)
        }

    @Test
    fun `stop finalizes the file before shutting the service down`() =
        runTest {
            confirmRecorderStarts()
            controller.stopPath = "/audio_notes/final.m4a"
            val manager = manager()
            manager.start()

            manager.stop()

            assertEquals(listOf("finalize", "shutdown"), controller.stopOrder)
        }

    @Test
    fun `stop hands back the recording of a session the service ended on its own`() =
        runTest {
            confirmRecorderStarts()
            controller.stopPath = null
            val manager = manager()
            manager.start()

            controller.emit(RecordingServiceState(isRecording = false, recordedFilePath = "/audio_notes/limit.m4a"))
            advanceUntilIdle()
            val path = manager.stop()

            assertEquals("/audio_notes/limit.m4a", path)
        }

    @Test
    fun `a session the service ends on its own is no longer recording`() =
        runTest {
            confirmRecorderStarts()
            val manager = manager()
            manager.start()

            controller.emit(RecordingServiceState(isRecording = false, recordedFilePath = "/audio_notes/limit.m4a"))
            advanceUntilIdle()

            assertFalse(manager.isRecording.value)
            assertFalse(manager.isRecording.first())
        }

    @Test
    fun `stop when idle returns nothing`() =
        runTest {
            assertNull(manager().stop())
        }

    @Test
    fun `stop returns nothing when the recording could not be finalized`() =
        runTest {
            confirmRecorderStarts()
            controller.stopPath = null
            val manager = manager()
            manager.start()

            assertNull(manager.stop())
            assertFalse(manager.isRecording.value)
        }

    @Test
    fun `stop keeps the recording on disk`() =
        runTest {
            confirmRecorderStarts()
            controller.stopPath = recordingFile.absolutePath
            recordingFile.writeText("audio")
            val manager = manager()
            manager.start()

            manager.stop()

            assertTrue(recordingFile.exists())
        }

    @Test
    fun `a new session can start after a stop`() =
        runTest {
            confirmRecorderStarts()
            controller.stopPath = "/audio_notes/final.m4a"
            val manager = manager()
            manager.start()
            manager.stop()

            assertTrue(manager.start())
        }

    @Test
    fun `discard stops the session and deletes the recording`() =
        runTest {
            confirmRecorderStarts()
            controller.stopPath = recordingFile.absolutePath
            recordingFile.writeText("audio")
            val manager = manager()
            manager.start()

            manager.discard()

            assertFalse(manager.isRecording.value)
            assertFalse(recordingFile.exists())
            assertEquals(1, controller.shutdowns)
        }

    @Test
    fun `discard deletes the recording even when it could not be finalized`() =
        runTest {
            confirmRecorderStarts()
            controller.stopPath = null
            recordingFile.writeText("audio")
            val manager = manager()
            manager.start()

            manager.discard()

            assertFalse(recordingFile.exists())
        }

    @Test
    fun `discard also deletes the raw recording a crash-safe session leaves when it could not be wrapped`() =
        runTest {
            confirmRecorderStarts()
            controller.stopPath = null
            val inFlight = CrashSafeRecording.inFlightFile(recordingFile).apply { writeText("raw audio") }
            val manager = manager()
            manager.start()

            manager.discard()

            assertFalse(inFlight.exists(), "a discarded recording must not come back at the next launch")
        }

    @Test
    fun `a stop requested from a cleared screen ends the session`() =
        runTest {
            confirmRecorderStarts()
            controller.stopPath = recordingFile.absolutePath
            val manager = manager()
            manager.start()

            manager.requestStop()
            advanceUntilIdle()

            assertFalse(manager.isRecording.value)
            assertEquals(1, controller.shutdowns)
        }

    @Test
    fun `releasing an idle manager does nothing`() =
        runTest {
            val manager = manager()

            manager.requestStop()
            advanceUntilIdle()

            assertEquals(0, controller.shutdowns)
        }

    @Test
    fun `pause and resume follow the service`() =
        runTest {
            confirmRecorderStarts()
            val manager = manager()
            manager.start()

            assertTrue(manager.pause())
            assertTrue(manager.resume())
            assertEquals(listOf("pause", "resume"), controller.pauseResumeCalls)
        }

    @Test
    fun `pause and resume are refused when nothing is recording`() =
        runTest {
            val manager = manager()

            assertFalse(manager.pause())
            assertFalse(manager.resume())
            assertTrue(controller.pauseResumeCalls.isEmpty())
        }

    @Test
    fun `pause that the service refuses is reported as a failure`() =
        runTest {
            confirmRecorderStarts()
            controller.pauseResult = false
            val manager = manager()
            manager.start()

            assertFalse(manager.pause())
        }

    @Test
    fun `paused state mirrors the service`() =
        runTest {
            confirmRecorderStarts()
            val manager = manager()
            manager.start()
            assertFalse(manager.isPaused.value)

            controller.emit(RecordingServiceState(isRecording = true, isPaused = true))
            advanceUntilIdle()

            assertTrue(manager.isPaused.value)
            assertFalse(manager.pausedByInterruption.value)
        }

    @Test
    fun `a pause from another app taking the audio is reported as an interruption`() =
        runTest {
            confirmRecorderStarts()
            val manager = manager()
            manager.start()

            controller.emit(RecordingServiceState(isRecording = true, isPaused = true, pausedByInterruption = true))
            advanceUntilIdle()

            assertTrue(manager.isPaused.value)
            assertTrue(manager.pausedByInterruption.value)
        }

    @Test
    fun `pause state clears when the session ends`() =
        runTest {
            confirmRecorderStarts()
            controller.stopPath = recordingFile.absolutePath
            val manager = manager()
            manager.start()
            controller.emit(RecordingServiceState(isRecording = true, isPaused = true, pausedByInterruption = true))
            advanceUntilIdle()

            manager.stop()

            assertFalse(manager.isPaused.value)
            assertFalse(manager.pausedByInterruption.value)
        }

    @Test
    fun `audio level and duration follow the service`() =
        runTest {
            confirmRecorderStarts()
            val manager = manager()
            manager.start()

            controller.emit(RecordingServiceState(isRecording = true, audioLevel = 0.5f, durationSeconds = 2))
            advanceUntilIdle()

            assertEquals(0.5f, manager.audioLevel.first())
            assertEquals(2_000.milliseconds, manager.elapsed.first())
        }

    private class FakeRecordingServiceController : RecordingServiceController {
        private val state = MutableStateFlow<RecordingServiceState?>(null)
        override val serviceState: StateFlow<RecordingServiceState?> = state

        val startedPaths = mutableListOf<String>()
        val stopOrder = mutableListOf<String>()
        val pauseResumeCalls = mutableListOf<String>()
        var onStart: () -> Unit = {}
        var startResult = true
        var stopPath: String? = null
        var pauseResult = true
        var shutdowns = 0
        var finalizations = 0

        fun emit(value: RecordingServiceState?) {
            state.value = value
        }

        override fun start(
            outputPath: String,
            inputDeviceId: String?,
        ): Boolean {
            startedPaths += outputPath
            if (startResult) onStart()
            return startResult
        }

        override fun stopRecordingNow(): String? {
            finalizations++
            stopOrder += "finalize"
            return stopPath
        }

        override fun shutdown() {
            shutdowns++
            stopOrder += "shutdown"
            state.value = null
        }

        override fun pause(): Boolean {
            pauseResumeCalls += "pause"
            return pauseResult
        }

        override fun resume(): Boolean {
            pauseResumeCalls += "resume"
            return true
        }

        override fun updatePreferredInputDevice(inputDeviceId: String?) = Unit
    }
}
