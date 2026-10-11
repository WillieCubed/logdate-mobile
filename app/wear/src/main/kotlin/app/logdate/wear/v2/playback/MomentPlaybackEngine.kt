package app.logdate.wear.v2.playback

import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackOptions
import app.logdate.client.media.audio.AudioPlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.Uuid

/** Where the audio engine is in the recording [noteId], or in nothing while it is null. */
data class EngineStatus(
    val noteId: Uuid?,
    val positionMs: Long,
    val durationMs: Long,
    val suppressedForUnsuitableOutput: Boolean,
) {
    companion object {
        val Idle = EngineStatus(noteId = null, positionMs = 0L, durationMs = 0L, suppressedForUnsuitableOutput = false)
    }
}

/** The audio engine [MomentPlayer] drives. */
interface MomentPlaybackEngine {
    val status: StateFlow<EngineStatus>

    /** Starts [uri]; [onFinished] receives true when playback failed and false when the recording ended. */
    fun start(
        uri: String,
        noteId: Uuid,
        options: AudioPlaybackOptions,
        onFinished: (failed: Boolean) -> Unit,
    )

    fun pause()

    fun resume()

    fun stop()

    fun seekTo(positionMs: Long)

    fun setSpeed(speed: Float)

    fun setSkipSilence(enabled: Boolean)
}

/** [MomentPlaybackEngine] over the shared Media3 playback manager and its session. */
class AndroidMomentPlaybackEngine(
    private val manager: AndroidAudioPlaybackManager,
    scope: CoroutineScope,
) : MomentPlaybackEngine {
    override val status: StateFlow<EngineStatus> =
        manager.playbackStatus
            .map(AudioPlaybackStatus::toEngineStatus)
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, EngineStatus.Idle)

    override fun start(
        uri: String,
        noteId: Uuid,
        options: AudioPlaybackOptions,
        onFinished: (failed: Boolean) -> Unit,
    ) = manager.startPlayback(
        uri = uri,
        metadata = AudioPlaybackMetadata(noteId = noteId),
        options = options,
        onProgressUpdated = {},
        onPlaybackCompleted = { onFinished(manager.playbackStatus.value.errorMessage != null) },
    )

    override fun pause() = manager.pausePlayback()

    override fun resume() = manager.resumePlayback()

    override fun stop() = manager.stopPlayback()

    override fun seekTo(positionMs: Long) = manager.seekToPosition(positionMs)

    override fun setSpeed(speed: Float) = manager.setPlaybackSpeed(speed)

    override fun setSkipSilence(enabled: Boolean) = manager.setSkipSilenceEnabled(enabled)
}

internal fun AudioPlaybackStatus.toEngineStatus(): EngineStatus =
    EngineStatus(
        noteId = metadata?.noteId,
        positionMs = positionMs,
        durationMs = duration.inWholeMilliseconds,
        suppressedForUnsuitableOutput = isSuppressedForUnsuitableOutput,
    )
