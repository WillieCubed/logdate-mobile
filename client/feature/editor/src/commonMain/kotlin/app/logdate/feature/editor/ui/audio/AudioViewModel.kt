package app.logdate.feature.editor.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.media.audio.AudioRecordingManager as MediaAudioRecordingManager
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * Unified ViewModel for managing both audio recording and playback functionality.
 * Handles recording state, playback, audio levels, duration, and transcription.
 * Now supports pause/resume functionality for recording.
 */
class AudioViewModel(
    private val audioRecordingManager: MediaAudioRecordingManager,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val transcriptionRepository: TranscriptionRepository
) : ViewModel() {
    // StateFlow to expose immutable UI state
    private val _uiState = MutableStateFlow(AudioUiState())
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()
    private var audioLevelJob: Job? = null
    private var durationJob: Job? = null

    /**
     * Starts audio recording and updates the UI state.
     */
    fun startRecording() {
        viewModelScope.launch {
            Napier.d("AudioViewModel: Starting recording")
            try {
                // Stop any ongoing playback
                if (_uiState.value.isPlaying) {
                    audioPlaybackManager.stopPlayback()
                }
                
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
                        isPlaying = false,
                        audioLevels = emptyList(),
                        duration = Duration.ZERO,
                        error = null
                    )
                }
                Napier.d("AudioViewModel: Recording started")
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
            Napier.d("AudioViewModel: Stopping recording")
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
                
                // Auto-start transcription if URI and note ID are available
                if (uri != null && _uiState.value.currentNoteId != null) {
                    requestTranscription()
                }
                Napier.d("AudioViewModel: Recording stopped, URI: $uri")
            } catch (e: Exception) {
                Napier.e("Failed to stop recording: ${e.message}", e)
                _uiState.update { it.copy(isRecording = false, isPaused = false, error = "Failed to stop recording") }
            }
        }
    }
    
    /**
     * Pauses the current recording.
     * Only works on platforms that support pause functionality.
     */
    fun pauseRecording() {
        viewModelScope.launch {
            Napier.d("AudioViewModel: Pausing recording")
            try {
                val success = audioRecordingManager.pauseRecording()
                
                if (success) {
                    _uiState.update { it.copy(isPaused = true, isRecording = true) }
                    Napier.d("Recording paused successfully")
                } else {
                    Napier.w("Failed to pause recording - functionality may not be supported on this platform")
                }
            } catch (e: Exception) {
                Napier.e("Error pausing recording: ${e.message}", e)
            }
        }
    }
    
    /**
     * Resumes a paused recording.
     * Only works on platforms that support resume functionality.
     */
    fun resumeRecording() {
        viewModelScope.launch {
            Napier.d("AudioViewModel: Resuming recording")
            try {
                val success = audioRecordingManager.resumeRecording()
                
                if (success) {
                    _uiState.update { it.copy(isPaused = false, isRecording = true) }
                    Napier.d("Recording resumed successfully")
                } else {
                    Napier.w("Failed to resume recording - functionality may not be supported on this platform")
                }
            } catch (e: Exception) {
                Napier.e("Error resuming recording: ${e.message}", e)
            }
        }
    }
    
    /**
     * Toggles recording between recording and paused states.
     */
    fun toggleRecordingPause() {
        if (_uiState.value.isRecording) {
            if (_uiState.value.isPaused) {
                resumeRecording()
            } else {
                pauseRecording()
            }
        }
    }

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
            Napier.d("AudioViewModel: Starting playback of $uri")
            try {
                // Stop any ongoing recording
                if (_uiState.value.isRecording) {
                    stopRecording()
                }
                
                _uiState.update { it.copy(
                    isPlaying = true, 
                    currentUri = uri,
                    isRecording = false,
                    isPaused = false
                )}
                
                audioPlaybackManager.startPlayback(
                    uri = uri,
                    onProgressUpdated = { progress ->
                        _uiState.update { it.copy(playbackProgress = progress) }
                    },
                    onPlaybackCompleted = {
                        _uiState.update { it.copy(isPlaying = false, playbackProgress = 0f) }
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
            Napier.d("AudioViewModel: Pausing playback")
            try {
                audioPlaybackManager.pausePlayback()
                _uiState.update { it.copy(isPlaying = false) }
            } catch (e: Exception) {
                Napier.e("Failed to pause playback: ${e.message}", e)
            }
        }
    }
    
    /**
     * Seeks to a specific position in the audio.
     */
    fun seekTo(position: Float) {
        viewModelScope.launch {
            Napier.d("AudioViewModel: Seeking to $position")
            try {
                audioPlaybackManager.seekTo(position)
                _uiState.update { it.copy(playbackProgress = position) }
            } catch (e: Exception) {
                Napier.e("Failed to seek: ${e.message}", e)
            }
        }
    }

    /**
     * Sets the current note ID for transcription.
     * This should be called before requesting transcription.
     * 
     * @param noteId The UUID of the audio note.
     */
    fun setCurrentNoteId(noteId: Uuid) {
        _uiState.update { it.copy(currentNoteId = noteId) }
        
        // Begin observing transcription for this note ID
        viewModelScope.launch {
            transcriptionRepository.observeTranscription(noteId)
                .catch { e ->
                    Napier.e("Error observing transcription", e)
                }
                .collect { transcriptionData ->
                    if (transcriptionData != null) {
                        _uiState.update { currentState ->
                            val transcriptionUiState = when (transcriptionData.status) {
                                TranscriptionStatus.PENDING -> AudioUiState.TranscriptionState.Pending
                                TranscriptionStatus.IN_PROGRESS -> AudioUiState.TranscriptionState.InProgress
                                TranscriptionStatus.COMPLETED -> {
                                    val text = transcriptionData.text
                                    if (text.isNullOrBlank()) {
                                        AudioUiState.TranscriptionState.Error("No transcription text available")
                                    } else {
                                        AudioUiState.TranscriptionState.Success(text)
                                    }
                                }
                                TranscriptionStatus.FAILED -> {
                                    AudioUiState.TranscriptionState.Error(
                                        transcriptionData.errorMessage ?: "Transcription failed"
                                    )
                                }
                            }
                            currentState.copy(transcriptionState = transcriptionUiState)
                        }
                    } else {
                        _uiState.update { it.copy(transcriptionState = AudioUiState.TranscriptionState.NotRequested) }
                    }
                }
        }
    }
    
    /**
     * Requests transcription for the current audio note.
     * The note ID must be set using setCurrentNoteId before calling this method.
     */
    fun requestTranscription() {
        val noteId = _uiState.value.currentNoteId
        val audioUri = _uiState.value.recordedAudioUri ?: _uiState.value.currentUri
        
        if (noteId == null) {
            Napier.e("Cannot request transcription: No note ID set")
            _uiState.update { it.copy(error = "Cannot request transcription: No note ID set") }
            return
        }
        
        if (audioUri == null) {
            Napier.e("Cannot request transcription: No audio URI available")
            _uiState.update { it.copy(error = "Cannot request transcription: No audio URI available") }
            return
        }
        
        // Update UI state immediately to show requesting
        _uiState.update { it.copy(transcriptionState = AudioUiState.TranscriptionState.InProgress) }
        
        // Request transcription
        viewModelScope.launch {
            try {
                val result = transcriptionRepository.requestTranscription(noteId)
                if (!result) {
                    Napier.e("Failed to request transcription for note $noteId")
                    _uiState.update { it.copy(
                        transcriptionState = AudioUiState.TranscriptionState.Error("Failed to request transcription")
                    )}
                }
            } catch (e: Exception) {
                Napier.e("Error requesting transcription", e)
                _uiState.update { it.copy(
                    transcriptionState = AudioUiState.TranscriptionState.Error(e.message ?: "Unknown error")
                )}
            }
        }
    }

    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        Napier.d("AudioViewModel: Being cleared")
        try {
            stopRecordingCollectors()
            audioRecordingManager.release()
            audioPlaybackManager.release()
        } catch (e: Exception) {
            Napier.e("Error cleaning up audio resources: ${e.message}", e)
        }
    }

    fun clearRecordedAudio() {
        _uiState.update { it.copy(recordedAudioUri = null) }
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
