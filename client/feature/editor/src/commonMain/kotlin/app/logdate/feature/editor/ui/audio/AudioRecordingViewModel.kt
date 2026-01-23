package app.logdate.feature.editor.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.media.audio.AudioRecordingManager as MediaAudioRecordingManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * View model for audio recording functionality.
 * Manages recording state, audio levels, duration, and transcription.
 * Extends ViewModel for proper lifecycle management.
 */
class AudioRecordingViewModel(
    private val audioRecordingManager: MediaAudioRecordingManager
) : ViewModel() {
    // StateFlow to expose immutable UI state
    private val _uiState = MutableStateFlow(AudioRecordingUiState())
    val uiState: StateFlow<AudioRecordingUiState> = _uiState.asStateFlow()
    private var audioLevelJob: Job? = null
    private var durationJob: Job? = null

    /**
     * Starts audio recording and updates the UI state.
     */
    fun startRecording() {
        viewModelScope.launch {
            Napier.d("AudioRecordingViewModel: Starting recording")
            try {
                val started = audioRecordingManager.startRecording()
                if (!started) {
                    _uiState.update { it.copy(isRecording = false, error = "Failed to start recording") }
                    return@launch
                }

                startRecordingCollectors()
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        isPaused = false,
                        audioLevels = emptyList(),
                        duration = Duration.ZERO,
                        error = null
                    )
                }
                Napier.d("AudioRecordingViewModel: Recording started")
            } catch (e: Exception) {
                Napier.e("Failed to start recording: ${e.message}", e)
                _uiState.update { it.copy(isRecording = false, error = "Failed to start recording") }
            }
        }
    }

    /**
     * Stops audio recording, saves the file, and updates the UI state.
     */
    fun stopRecording() {
        viewModelScope.launch {
            Napier.d("AudioRecordingViewModel: Stopping recording")
            try {
                val uri = audioRecordingManager.stopRecording()
                stopRecordingCollectors()
                
                _uiState.update { 
                    it.copy(
                        isRecording = false,
                        isPaused = false,
                        recordedAudioUri = uri,
                    )
                }
                
                // Start transcription process
                transcribeAudio(uri)
                Napier.d("AudioRecordingViewModel: Recording stopped, URI: $uri")
            } catch (e: Exception) {
                Napier.e("Failed to stop recording: ${e.message}", e)
                _uiState.update { it.copy(isRecording = false, error = "Failed to stop recording") }
            }
        }
    }

    /**
     * Transcribes the recorded audio.
     */
    private fun transcribeAudio(audioUri: String?) {
        viewModelScope.launch {
            if (audioUri == null) return@launch
            
            Napier.d("AudioRecordingViewModel: Starting transcription for $audioUri")
            try {
                // Simulate transcription for now
                _uiState.update { 
                    it.copy(
                        transcription = "This is a simulated transcription placeholder. It would be replaced with a real transcription API in production."
                    ) 
                }
                Napier.d("AudioRecordingViewModel: Transcription complete")
            } catch (e: Exception) {
                Napier.e("Failed to transcribe audio: ${e.message}", e)
                _uiState.update { it.copy(error = "Failed to transcribe audio") }
            }
        }
    }

    /**
     * Pauses the current recording.
     */
    fun pauseRecording() {
        if (_uiState.value.isRecording && !_uiState.value.isPaused) {
            viewModelScope.launch {
                Napier.d("AudioRecordingViewModel: Pausing recording")
                try {
                    val paused = audioRecordingManager.pauseRecording()
                    if (paused) {
                        _uiState.update { it.copy(isPaused = true, isRecording = true) }
                    }
                } catch (e: Exception) {
                    Napier.e("Failed to pause recording: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Restarts recording from scratch.
     */
    fun restartRecording() {
        viewModelScope.launch {
            Napier.d("AudioRecordingViewModel: Restarting recording")
            try {
                // Stop any existing recording
                if (_uiState.value.isRecording) {
                    audioRecordingManager.stopRecording()
                }
                
                // Clear state
                _uiState.update { 
                    AudioRecordingUiState()
                }
                
                // Start new recording
                startRecording()
            } catch (e: Exception) {
                Napier.e("Failed to restart recording: ${e.message}", e)
            }
        }
    }
    
    /**
     * Clears the current recording state.
     */
    fun clearRecording() {
        viewModelScope.launch {
            Napier.d("AudioRecordingViewModel: Clearing recording")
            try {
                _uiState.update {
                    AudioRecordingUiState()
                }
                stopRecordingCollectors()
                audioRecordingManager.release()
            } catch (e: Exception) {
                Napier.e("Failed to clear recording: ${e.message}", e)
            }
        }
    }
    
    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        Napier.d("AudioRecordingViewModel: Being cleared")
        try {
            stopRecordingCollectors()
            audioRecordingManager.release()
        } catch (e: Exception) {
            Napier.e("Error cleaning up audio resources: ${e.message}", e)
        }
    }

    private fun startRecordingCollectors() {
        audioLevelJob?.cancel()
        durationJob?.cancel()
        audioLevelJob = viewModelScope.launch {
            audioRecordingManager.getAudioLevelFlow().collect { level ->
                _uiState.update { state ->
                    val levels = (state.audioLevels + level).takeLast(50)
                    state.copy(audioLevels = levels)
                }
            }
        }
        durationJob = viewModelScope.launch {
            audioRecordingManager.getRecordingDurationFlow().collect { duration ->
                _uiState.update { it.copy(duration = duration) }
            }
        }
    }

    private fun stopRecordingCollectors() {
        audioLevelJob?.cancel()
        durationJob?.cancel()
        audioLevelJob = null
        durationJob = null
    }
}

/**
 * Data class representing the UI state for audio recording.
 */
data class AudioRecordingUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordedAudioUri: String? = null,
    val transcription: String? = null,
    val audioLevels: List<Float> = emptyList(),
    val duration: Duration = Duration.ZERO,
    val error: String? = null,
)
