package app.logdate.feature.editor.ui.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android implementation of AudioPlaybackManager using MediaPlayer.
 * Handles audio playback, seeking, and progress tracking.
 */
class AndroidAudioPlaybackManager(
    private val context: Context
) : AudioPlaybackManager {
    private var mediaPlayer: MediaPlayer? = null
    private var progressUpdateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // Track if we're actively playing
    private var isPlaying = false
    
    override fun startPlayback(
        uri: String,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit
    ) {
        Napier.d("AndroidAudioPlaybackManager: Starting playback of $uri")
        
        try {
            // Stop any existing playback
            stopPlayback()
            
            // Create and prepare the media player
            val player = MediaPlayer()
            mediaPlayer = player
            
            // Set up the data source
            player.setDataSource(context, Uri.parse(uri))
            
            // Set up completion listener
            player.setOnCompletionListener {
                Napier.d("AndroidAudioPlaybackManager: Playback completed")
                isPlaying = false
                onProgressUpdated(1.0f)
                onPlaybackCompleted()
                stopProgressUpdates()
            }
            
            // Set up error listener
            player.setOnErrorListener { _, what, extra ->
                Napier.e("AndroidAudioPlaybackManager: Error during playback: code=$what, extra=$extra")
                isPlaying = false
                onPlaybackCompleted()
                stopProgressUpdates()
                true
            }
            
            // Prepare synchronously for simplicity
            player.prepare()
            
            // Start playback
            player.start()
            isPlaying = true
            
            // Start tracking progress
            startProgressUpdates(onProgressUpdated)
            
            Napier.d("AndroidAudioPlaybackManager: Playback started successfully")
        } catch (e: Exception) {
            Napier.e("AndroidAudioPlaybackManager: Error starting playback", e)
            releaseMediaPlayer()
            onPlaybackCompleted()
        }
    }
    
    override fun pausePlayback() {
        Napier.d("AndroidAudioPlaybackManager: Pausing playback")
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
                stopProgressUpdates()
            }
        }
    }
    
    override fun stopPlayback() {
        Napier.d("AndroidAudioPlaybackManager: Stopping playback")
        releaseMediaPlayer()
        stopProgressUpdates()
        isPlaying = false
    }
    
    override fun seekTo(position: Float) {
        Napier.d("AndroidAudioPlaybackManager: Seeking to $position")
        mediaPlayer?.let {
            val durationMs = it.duration
            val seekPositionMs = (position * durationMs).toInt()
            it.seekTo(seekPositionMs)
        }
    }
    
    override fun release() {
        Napier.d("AndroidAudioPlaybackManager: Releasing resources")
        releaseMediaPlayer()
        stopProgressUpdates()
    }
    
    private fun startProgressUpdates(onProgressUpdated: (Float) -> Unit) {
        stopProgressUpdates()
        
        progressUpdateJob = scope.launch {
            while (isActive && isPlaying) {
                mediaPlayer?.let {
                    if (it.duration > 0) {
                        val progress = it.currentPosition.toFloat() / it.duration.toFloat()
                        onProgressUpdated(progress)
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
    
    private fun releaseMediaPlayer() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            } catch (e: Exception) {
                Napier.e("AndroidAudioPlaybackManager: Error releasing media player", e)
            }
        }
        mediaPlayer = null
    }
}