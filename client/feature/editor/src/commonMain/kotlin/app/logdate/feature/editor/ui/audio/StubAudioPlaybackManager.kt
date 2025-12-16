package app.logdate.feature.editor.ui.audio

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stub implementation of AudioPlaybackManager for development purposes.
 * This simulates audio playback without actually playing any audio.
 */
class StubAudioPlaybackManager : AudioPlaybackManager {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var playbackJob: Job? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()
    
    private var currentUri: String? = null
    private var playbackDuration: Duration = 30000.milliseconds
    
    override fun startPlayback(
        uri: String,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit
    ) {
        Napier.d("StubAudioPlaybackManager: Starting playback of $uri")
        
        // Stop any existing playback
        playbackJob?.cancel()
        
        currentUri = uri
        _isPlaying.value = true
        
        // Determine a "playback duration" based on the URI's hashcode for consistent behavior
        val hash = uri.hashCode()
        playbackDuration = (10000 + kotlin.math.abs(hash % 50000)).milliseconds
        
        // Simulate playback with progress updates
        playbackJob = scope.launch {
            val updateIntervalMs = 100L
            val totalSteps = (playbackDuration.inWholeMilliseconds / updateIntervalMs).toInt()
            
            _progress.value = 0f
            
            for (step in 1..totalSteps) {
                delay(updateIntervalMs)
                val newProgress = step.toFloat() / totalSteps
                _progress.value = newProgress
                onProgressUpdated(newProgress)
                
                // Add some randomness to simulate buffering or processing
                if (Random.nextInt(100) > 98) {
                    delay(200)
                }
            }
            
            // Finalize playback
            _progress.value = 1f
            onProgressUpdated(1f)
            _isPlaying.value = false
            onPlaybackCompleted()
            Napier.d("StubAudioPlaybackManager: Playback completed")
        }
    }
    
    override fun pausePlayback() {
        Napier.d("StubAudioPlaybackManager: Pausing playback")
        _isPlaying.value = false
        playbackJob?.cancel()
    }
    
    override fun stopPlayback() {
        Napier.d("StubAudioPlaybackManager: Stopping playback")
        _isPlaying.value = false
        _progress.value = 0f
        playbackJob?.cancel()
    }
    
    override fun seekTo(position: Float) {
        Napier.d("StubAudioPlaybackManager: Seeking to $position")
        _progress.value = position.coerceIn(0f, 1f)
    }
    
    override fun release() {
        Napier.d("StubAudioPlaybackManager: Releasing resources")
        _isPlaying.value = false
        _progress.value = 0f
        playbackJob?.cancel()
    }
}