package app.logdate.feature.editor.ui.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android implementation of AudioPlaybackManager using Media3 ExoPlayer.
 *
 * ExoPlayer provides better audio handling than MediaPlayer:
 * - Better format support (including Opus, FLAC, etc.)
 * - More reliable seeking
 * - Better error handling
 * - Audio focus management
 */
@OptIn(UnstableApi::class)
class AndroidAudioPlaybackManager(
    private val context: Context
) : AudioPlaybackManager {

    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun startPlayback(
        uri: String,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit
    ) {
        Napier.d { "ExoPlayerAudioPlaybackManager: Starting playback of $uri" }

        // Release any existing player
        release()

        try {
            exoPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true // Handle audio focus
                )
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(uri))

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_ENDED -> {
                                    Napier.d { "ExoPlayerAudioPlaybackManager: Playback completed" }
                                    onProgressUpdated(1.0f)
                                    onPlaybackCompleted()
                                    stopProgressTracking()
                                }
                                Player.STATE_READY -> {
                                    if (isPlaying) {
                                        startProgressTracking(onProgressUpdated)
                                    }
                                }
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying) {
                                startProgressTracking(onProgressUpdated)
                            } else {
                                stopProgressTracking()
                            }
                        }
                    })

                    prepare()
                    play()
                }

            Napier.d { "ExoPlayerAudioPlaybackManager: Playback started successfully" }
        } catch (e: Exception) {
            Napier.e(e) { "ExoPlayerAudioPlaybackManager: Error starting playback" }
            onPlaybackCompleted()
        }
    }

    override fun pausePlayback() {
        Napier.d { "ExoPlayerAudioPlaybackManager: Pausing playback" }
        exoPlayer?.pause()
        stopProgressTracking()
    }

    override fun stopPlayback() {
        Napier.d { "ExoPlayerAudioPlaybackManager: Stopping playback" }
        stopProgressTracking()
        exoPlayer?.stop()
    }

    override fun seekTo(position: Float) {
        Napier.d { "ExoPlayerAudioPlaybackManager: Seeking to $position" }
        exoPlayer?.let { player ->
            val duration = player.duration
            if (duration > 0) {
                val seekPosition = (position * duration).toLong()
                player.seekTo(seekPosition)
            }
        }
    }

    override fun release() {
        Napier.d { "ExoPlayerAudioPlaybackManager: Releasing resources" }
        stopProgressTracking()
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun startProgressTracking(onProgressUpdated: (Float) -> Unit) {
        stopProgressTracking()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    val duration = player.duration
                    if (duration > 0) {
                        val progress = player.currentPosition.toFloat() / duration.toFloat()
                        onProgressUpdated(progress.coerceIn(0f, 1f))
                    }
                }
                delay(100) // Update every 100ms
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
