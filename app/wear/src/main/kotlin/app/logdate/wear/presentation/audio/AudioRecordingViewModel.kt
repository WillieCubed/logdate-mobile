package app.logdate.wear.presentation.audio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.recording.WearAudioRecordingManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * ViewModel for the Wear OS audio recording screen.
 * Manages recording state and handles storage space validation.
 */
class AudioRecordingViewModel(
    application: Application,
    private val recordingManager: WearAudioRecordingManager,
    private val notesRepository: JournalNotesRepository,
    private val storageChecker: StorageSpaceChecker
) : AndroidViewModel(application) {

    companion object {
        // Estimate size of 1-minute audio recording (AAC format, 128kbps)
        // 128 kilobits per second * 60 seconds / 8 bits per byte = 960 kilobytes
        private const val ONE_MINUTE_RECORDING_SIZE_BYTES = 960 * 1024L
        
        // Buffer space to leave free (0.5 MB)
        private const val BUFFER_SPACE_BYTES = 512 * 1024L
    }
    
    // UI state exposed to the Composable
    private val _uiState = MutableStateFlow(AudioRecordingUiState())
    val uiState: StateFlow<AudioRecordingUiState> = _uiState
    
    // Recording metadata
    private var audioPath: String? = null
    private var audioLevelJob: Job? = null
    private var durationJob: Job? = null
    
    // Clean up resources when ViewModel is cleared
    override fun onCleared() {
        stopRecordingCollectors()
        recordingManager.release()
        super.onCleared()
    }
    
    /**
     * Start audio recording with storage space validation
     */
    fun startRecording() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                
                // Check available storage space first
                val hasEnoughSpace = checkStorageSpace()
                if (!hasEnoughSpace) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            errorMessage = "Not enough storage space for recording"
                        ) 
                    }
                    return@launch
                }
                
                // Start recording
                val started = recordingManager.startRecording()
                if (!started) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to start recording"
                        )
                    }
                    return@launch
                }

                startRecordingCollectors()
                
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        isLoading = false,
                        isPaused = false,
                        audioLevels = emptyList(),
                        durationMs = 0
                    ) 
                }
                
                Napier.d("Recording started on Wear OS")
            } catch (e: Exception) {
                Napier.e("Failed to start recording", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to start: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * Stop recording and save the note
     */
    fun stopRecording() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // Stop recording and get file path
                audioPath = recordingManager.stopRecording()
                stopRecordingCollectors()
                
                // Check if recording was successful
                if (audioPath != null) {
                    // Create and save the audio note
                    val now = Clock.System.now()
                    val audioNote = JournalNote.Audio(
                        mediaRef = audioPath!!,
                        uid = Uuid.random(),
                        creationTimestamp = now,
                        lastUpdated = now,
                        durationMs = _uiState.value.durationMs
                    )
                    
                    // Save to repository
                    notesRepository.create(audioNote)
                    
                    // Navigate back
                    _uiState.update { 
                        it.copy(
                            isRecording = false,
                            isLoading = false,
                            navigateBack = true
                        ) 
                    }
                    
                    Napier.d("Recording saved: $audioPath")
                } else {
                    _uiState.update { 
                        it.copy(
                            isRecording = false,
                            isLoading = false,
                            errorMessage = "Failed to save recording"
                        ) 
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to stop recording", e)
                _uiState.update { 
                    it.copy(
                        isRecording = false,
                        isLoading = false,
                        errorMessage = "Failed to save: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * Pause the current recording
     */
    fun pauseRecording() {
        viewModelScope.launch {
            try {
                val paused = recordingManager.pauseRecording()
                if (paused) {
                    _uiState.update { it.copy(isPaused = true) }
                    Napier.d("Recording paused")
                }
            } catch (e: Exception) {
                Napier.e("Failed to pause recording", e)
            }
        }
    }
    
    /**
     * Resume a paused recording
     */
    fun resumeRecording() {
        viewModelScope.launch {
            try {
                val resumed = recordingManager.resumeRecording()
                if (resumed) {
                    _uiState.update { it.copy(isPaused = false) }
                    Napier.d("Recording resumed")
                }
            } catch (e: Exception) {
                Napier.e("Failed to resume recording", e)
            }
        }
    }
    
    /**
     * Cancel the current recording
     */
    fun cancelRecording() {
        viewModelScope.launch {
            try {
                stopRecordingCollectors()
                recordingManager.release()
                _uiState.update { 
                    it.copy(
                        isRecording = false,
                        isPaused = false,
                        audioLevels = emptyList(),
                        durationMs = 0,
                        navigateBack = true
                    ) 
                }
                Napier.d("Recording cancelled")
            } catch (e: Exception) {
                Napier.e("Failed to cancel recording", e)
                _uiState.update { 
                    it.copy(
                        isRecording = false,
                        errorMessage = "Failed to cancel: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * Check if there's enough storage space for a 1-minute recording
     */
    private suspend fun checkStorageSpace(): Boolean {
        val requiredSpace = ONE_MINUTE_RECORDING_SIZE_BYTES + BUFFER_SPACE_BYTES
        val availableSpace = storageChecker.getAvailableStorageSpace()
        
        Napier.d("Available storage: ${availableSpace / 1024}KB, Required: ${requiredSpace / 1024}KB")
        
        return availableSpace >= requiredSpace
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startRecordingCollectors() {
        audioLevelJob?.cancel()
        durationJob?.cancel()

        audioLevelJob = viewModelScope.launch {
            combine(
                recordingManager.getAudioLevelFlow(),
                recordingManager.getRecordingDurationFlow(),
            ) { level, duration ->
                level to duration.inWholeMilliseconds
            }
                .sample(periodMillis = 100)
                .collect { (level, durationMs) ->
                    _uiState.update { state ->
                        val levels = (state.audioLevels + level).takeLast(50)
                        state.copy(
                            audioLevels = levels,
                            durationMs = durationMs,
                        )
                    }
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
 * UI state for audio recording screen
 */
data class AudioRecordingUiState(
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isLoading: Boolean = false,
    val audioLevels: List<Float> = emptyList(),
    val durationMs: Long = 0,
    val errorMessage: String? = null,
    val navigateBack: Boolean = false
)
