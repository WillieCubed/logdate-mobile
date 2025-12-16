package app.logdate.feature.editor.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for managing audio playback.
 * Handles playback state, progress tracking, and playback controls.
 */
class AudioPlaybackViewModel(
    private val audioPlaybackManager: AudioPlaybackManager
) : ViewModel() {
    // Internal mutable state
    private val _uiState = MutableStateFlow(AudioPlaybackUiState())
    
    // Exposed immutable state
    val uiState: StateFlow<AudioPlaybackUiState> = _uiState.asStateFlow()
    
    /**
     * Toggles playback between playing and paused states.
     */
    fun togglePlayback(uri: String) {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback(uri)
        }
    }
    
    /**
     * Starts playing the audio from the given URI.
     */
    fun startPlayback(uri: String) {
        viewModelScope.launch {
            Napier.d("AudioPlaybackViewModel: Starting playback of $uri")
            try {
                _uiState.update { it.copy(isPlaying = true, currentUri = uri) }
                
                audioPlaybackManager.startPlayback(
                    uri = uri,
                    onProgressUpdated = { progress ->
                        _uiState.update { it.copy(progress = progress) }
                    },
                    onPlaybackCompleted = {
                        _uiState.update { it.copy(isPlaying = false, progress = 0f) }
                    }
                )
            } catch (e: Exception) {
                Napier.e("Failed to start playback: ${e.message}", e)
                _uiState.update { it.copy(isPlaying = false, error = "Failed to start playback") }
            }
        }
    }
    
    /**
     * Pauses the current playback.
     */
    fun pausePlayback() {
        viewModelScope.launch {
            Napier.d("AudioPlaybackViewModel: Pausing playback")
            try {
                audioPlaybackManager.pausePlayback()
                _uiState.update { it.copy(isPlaying = false) }
            } catch (e: Exception) {
                Napier.e("Failed to pause playback: ${e.message}", e)
            }
        }
    }
    
    /**
     * Stops the current playback and resets progress.
     */
    fun stopPlayback() {
        viewModelScope.launch {
            Napier.d("AudioPlaybackViewModel: Stopping playback")
            try {
                audioPlaybackManager.stopPlayback()
                _uiState.update { it.copy(isPlaying = false, progress = 0f) }
            } catch (e: Exception) {
                Napier.e("Failed to stop playback: ${e.message}", e)
            }
        }
    }
    
    /**
     * Seeks to a specific position in the audio.
     */
    fun seekTo(position: Float) {
        viewModelScope.launch {
            Napier.d("AudioPlaybackViewModel: Seeking to $position")
            try {
                audioPlaybackManager.seekTo(position)
                _uiState.update { it.copy(progress = position) }
            } catch (e: Exception) {
                Napier.e("Failed to seek: ${e.message}", e)
            }
        }
    }
    
    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        Napier.d("AudioPlaybackViewModel: Being cleared")
        try {
            audioPlaybackManager.release()
        } catch (e: Exception) {
            Napier.e("Error cleaning up audio playback resources: ${e.message}", e)
        }
    }
}

/**
 * Data class representing the UI state for audio playback.
 */
data class AudioPlaybackUiState(
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentUri: String? = null,
    val error: String? = null
)