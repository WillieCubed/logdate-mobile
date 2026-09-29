package app.logdate.wear.playback

import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.Uuid

/** The parts of audio playback the watch's voice note player drives. */
interface WearPlaybackEngine {
    /** True while the system refuses to play on the current output, such as no speaker and no headphones. */
    val suppressedForUnsuitableOutput: StateFlow<Boolean>

    /** [onProgress] reports 0 to 1; [onCompleted] fires when the track ends or fails. */
    fun start(
        uri: String,
        noteId: Uuid,
        onProgress: (Float) -> Unit,
        onCompleted: () -> Unit,
    )

    fun pause()

    fun resume()

    fun stop()

    fun seekTo(progress: Float)
}

/** [WearPlaybackEngine] over the shared Media3 playback manager. */
class AndroidWearPlaybackEngine(
    private val manager: AndroidAudioPlaybackManager,
    scope: CoroutineScope,
) : WearPlaybackEngine {
    override val suppressedForUnsuitableOutput: StateFlow<Boolean> =
        manager.playbackStatus
            .map { it.isSuppressedForUnsuitableOutput }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, false)

    override fun start(
        uri: String,
        noteId: Uuid,
        onProgress: (Float) -> Unit,
        onCompleted: () -> Unit,
    ) = manager.startPlayback(
        uri = uri,
        metadata = AudioPlaybackMetadata(noteId = noteId),
        onProgressUpdated = onProgress,
        onPlaybackCompleted = onCompleted,
    )

    override fun pause() = manager.pausePlayback()

    override fun resume() = manager.resumePlayback()

    override fun stop() = manager.stopPlayback()

    override fun seekTo(progress: Float) = manager.seekTo(progress)
}
