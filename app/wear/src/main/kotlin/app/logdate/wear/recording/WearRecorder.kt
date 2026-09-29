package app.logdate.wear.recording

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** Why a recording could not start, so the screen can say what to do about it. */
enum class RecordingStartFailure {
    MICROPHONE_PERMISSION_DENIED,
    NOT_ENOUGH_STORAGE,
    RECORDER_UNAVAILABLE,
}

/**
 * The watch's voice recorder as the screens see it.
 *
 * [start] reports success only once audio is being captured, and [stop] returns only after the
 * file is complete. A session the recorder ends on its own (the length limit) flips [isRecording]
 * to false; the next [stop] still returns that recording's file.
 */
interface WearRecorder {
    val isRecording: StateFlow<Boolean>

    /** True while paused, whether the user or an interruption paused it. */
    val isPaused: StateFlow<Boolean>

    /** True when the current pause came from a call or another app taking the audio. */
    val pausedByInterruption: StateFlow<Boolean>

    /** Live input level, 0 to 1. */
    val audioLevel: StateFlow<Float>

    /** Time recorded so far, excluding pauses. */
    val elapsed: StateFlow<Duration>

    /** Why the most recent [start] returned false, or null after a successful start. */
    val lastStartFailure: RecordingStartFailure?

    suspend fun start(): Boolean

    /** Ends the session and returns the finalized file path, or null when there is no recording. */
    suspend fun stop(): String?

    suspend fun pause(): Boolean

    suspend fun resume(): Boolean

    /** Ends the session and deletes its file. */
    suspend fun discard()

    /** Ends any session from a context with no usable coroutine scope, such as a cleared ViewModel. */
    fun requestStop()
}
