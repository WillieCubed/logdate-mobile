package app.logdate.client.media.audio

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.Foundation.NSURL

/**
 * iOS implementation of AudioPlaybackManager using AVAudioPlayer.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioPlaybackManager : AudioPlaybackManager {
    private var audioPlayer: AVAudioPlayer? = null
    private var progressUpdateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // Track if we're actively playing
    private var isPlaying = false

    init {
        setupAudioSession()
    }

    private fun setupAudioSession() {
        try {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)
        } catch (e: Exception) {
            Napier.e("IosAudioPlaybackManager: Failed to set up audio session", e)
        }
    }

    override fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata?,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit,
    ) {
        Napier.d("IosAudioPlaybackManager: Starting playback of $uri")

        try {
            // Stop any existing playback
            stopPlayback()

            // Create URL from string
            val fileUrl = NSURL.fileURLWithPath(uri)

            // Create the audio player
            val player = AVAudioPlayer(
                contentsOfURL = fileUrl,
                fileTypeHint = "m4a",
                error = null,
            )

            audioPlayer = player
            player.delegate = null

            // Prepare and play
            player.prepareToPlay()
            player.play()
            isPlaying = true

            // Start tracking progress and completion
            startProgressUpdates(onProgressUpdated, onPlaybackCompleted)

            Napier.d("IosAudioPlaybackManager: Playback started successfully")
        } catch (e: Exception) {
            Napier.e("IosAudioPlaybackManager: Error starting playback", e)
            releaseResources()
            onPlaybackCompleted()
        }
    }

    override fun pausePlayback() {
        Napier.d("IosAudioPlaybackManager: Pausing playback")
        audioPlayer?.let {
            if (it.isPlaying()) {
                it.pause()
                isPlaying = false
                stopProgressUpdates()
            }
        }
    }

    override fun stopPlayback() {
        Napier.d("IosAudioPlaybackManager: Stopping playback")
        releaseResources()
        stopProgressUpdates()
        isPlaying = false
    }

    override fun seekTo(position: Float) {
        Napier.d("IosAudioPlaybackManager: Seeking to $position")
        audioPlayer?.let {
            val durationSec = it.duration
            val seekPositionSec = position * durationSec
            it.currentTime = seekPositionSec
        }
    }

    override fun release() {
        Napier.d("IosAudioPlaybackManager: Releasing resources")
        releaseResources()
        stopProgressUpdates()
    }

    private fun startProgressUpdates(
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit,
    ) {
        stopProgressUpdates()

        progressUpdateJob = scope.launch {
            while (isActive && isPlaying) {
                audioPlayer?.let {
                    if (it.duration > 0) {
                        val progress = (it.currentTime / it.duration).toFloat().coerceIn(0f, 1f)
                        onProgressUpdated(progress)

                        if (it.currentTime >= it.duration) {
                            isPlaying = false
                            onProgressUpdated(1.0f)
                            onPlaybackCompleted()
                            stopProgressUpdates()
                            return@launch
                        }
                    }
                }
                delay(100) // Update every 100ms
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private fun releaseResources() {
        try {
            audioPlayer?.let {
                if (it.isPlaying()) {
                    it.stop()
                }
            }
            audioPlayer = null
        } catch (e: Exception) {
            Napier.e("IosAudioPlaybackManager: Error releasing resources", e)
        }
    }
}

// Removed AVAudioPlayerDelegateProtocol since we're not using it in this simplified implementation
