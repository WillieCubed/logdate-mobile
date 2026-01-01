package app.logdate.feature.editor.ui.audio

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineEvent
import java.net.URI
import java.nio.file.Paths

/**
 * Desktop implementation of AudioPlaybackManager using Java Sound API.
 */
class DesktopAudioPlaybackManager : AudioPlaybackManager {
    private var clip: Clip? = null
    private var audioInputStream: AudioInputStream? = null
    private var progressUpdateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // Track if we're actively playing
    private var isPlaying = false
    private var totalFrames: Long = 0
    
    override fun startPlayback(
        uri: String,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit
    ) {
        Napier.d("DesktopAudioPlaybackManager: Starting playback of $uri")
        
        try {
            // Stop any existing playback
            stopPlayback()
            
            // Parse the URI to get a file
            val path = when {
                uri.startsWith("file:///") -> uri.substring(7)
                uri.startsWith("file:/") -> uri.substring(5)
                else -> uri
            }
            
            val file = File(path)
            if (!file.exists()) {
                Napier.e("DesktopAudioPlaybackManager: File does not exist: $path")
                onPlaybackCompleted()
                return
            }
            
            // Open the audio input stream
            val inputStream = AudioSystem.getAudioInputStream(file)
            audioInputStream = inputStream
            
            // Get clip and open with the stream
            val audioFormat = inputStream.format
            val info = DataLine.Info(Clip::class.java, audioFormat)
            
            if (!AudioSystem.isLineSupported(info)) {
                Napier.e("DesktopAudioPlaybackManager: Audio format not supported")
                onPlaybackCompleted()
                return
            }
            
            // Get the clip and open it with the stream
            val newClip = AudioSystem.getLine(info) as Clip
            newClip.open(inputStream)
            clip = newClip
            
            // Get total frames for progress calculation
            totalFrames = newClip.frameLength.toLong()
            
            // Add listener for playback completion
            newClip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) {
                    isPlaying = false
                    onProgressUpdated(1.0f)
                    onPlaybackCompleted()
                    stopProgressUpdates()
                }
            }
            
            // Start playback
            newClip.start()
            isPlaying = true
            
            // Start tracking progress
            startProgressUpdates(onProgressUpdated)
            
            Napier.d("DesktopAudioPlaybackManager: Playback started successfully")
        } catch (e: Exception) {
            Napier.e("DesktopAudioPlaybackManager: Error starting playback", e)
            releaseResources()
            onPlaybackCompleted()
        }
    }
    
    override fun pausePlayback() {
        Napier.d("DesktopAudioPlaybackManager: Pausing playback")
        clip?.let {
            if (it.isRunning) {
                it.stop()
                isPlaying = false
                stopProgressUpdates()
            }
        }
    }
    
    override fun stopPlayback() {
        Napier.d("DesktopAudioPlaybackManager: Stopping playback")
        releaseResources()
        stopProgressUpdates()
        isPlaying = false
    }
    
    override fun seekTo(position: Float) {
        Napier.d("DesktopAudioPlaybackManager: Seeking to $position")
        clip?.let {
            val framePosition = (position * totalFrames).toLong()
            it.framePosition = framePosition.toInt()
        }
    }
    
    override fun release() {
        Napier.d("DesktopAudioPlaybackManager: Releasing resources")
        releaseResources()
        stopProgressUpdates()
    }
    
    private fun startProgressUpdates(onProgressUpdated: (Float) -> Unit) {
        stopProgressUpdates()
        
        progressUpdateJob = scope.launch {
            while (isActive && isPlaying) {
                clip?.let {
                    if (totalFrames > 0) {
                        val progress = it.framePosition.toFloat() / totalFrames.toFloat()
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
    
    private fun releaseResources() {
        try {
            clip?.let {
                if (it.isRunning) {
                    it.stop()
                }
                it.close()
            }
            clip = null
            
            audioInputStream?.close()
            audioInputStream = null
        } catch (e: Exception) {
            Napier.e("DesktopAudioPlaybackManager: Error releasing resources", e)
        }
    }
}