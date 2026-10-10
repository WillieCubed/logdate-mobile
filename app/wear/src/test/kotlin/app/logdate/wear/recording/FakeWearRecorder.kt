package app.logdate.wear.recording

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** A [WearRecorder] for tests: each call changes its flows at once and is counted. */
class FakeWearRecorder : WearRecorder {
    val recordingFlow = MutableStateFlow(false)
    val pausedFlow = MutableStateFlow(false)
    val interruptedFlow = MutableStateFlow(false)
    val levelFlow = MutableStateFlow(0f)
    val elapsedFlow = MutableStateFlow(Duration.ZERO)

    override val isRecording: StateFlow<Boolean> = recordingFlow
    override val isPaused: StateFlow<Boolean> = pausedFlow
    override val pausedByInterruption: StateFlow<Boolean> = interruptedFlow
    override val audioLevel: StateFlow<Float> = levelFlow
    override val elapsed: StateFlow<Duration> = elapsedFlow
    override var lastStartFailure: RecordingStartFailure? = null

    var startResult = true
    var startDelay: Duration = Duration.ZERO
    var stopPath: String? = DEFAULT_PATH
    var starts = 0
    var stops = 0
    var resumes = 0
    var discards = 0
    var stopRequested = false

    override suspend fun start(): Boolean {
        starts++
        if (startDelay > Duration.ZERO) delay(startDelay)
        if (startResult) recordingFlow.value = true
        return startResult
    }

    override suspend fun stop(): String? {
        stops++
        recordingFlow.value = false
        pausedFlow.value = false
        return stopPath
    }

    override suspend fun pause(): Boolean {
        pausedFlow.value = true
        return true
    }

    override suspend fun resume(): Boolean {
        resumes++
        pausedFlow.value = false
        interruptedFlow.value = false
        return true
    }

    override suspend fun discard() {
        discards++
        recordingFlow.value = false
        pausedFlow.value = false
    }

    override fun requestStop() {
        stopRequested = true
    }

    companion object {
        const val DEFAULT_PATH = "/files/audio_notes/recording_fake.m4a"
    }
}
