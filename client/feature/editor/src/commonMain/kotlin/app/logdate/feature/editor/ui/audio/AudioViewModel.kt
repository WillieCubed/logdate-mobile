package app.logdate.feature.editor.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.transcription.TranscriptionService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import app.logdate.client.media.audio.AudioRecordingManager as MediaAudioRecordingManager

/**
 * Unified ViewModel for managing both audio recording and playback functionality.
 * Handles recording state, playback, audio levels, duration, and transcription.
 * Now supports pause/resume functionality for recording.
 */
class AudioViewModel(
    private val audioRecordingManager: MediaAudioRecordingManager,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val audioDurationResolver: AudioDurationResolver,
    private val transcriptionService: TranscriptionService,
) : ViewModel() {
    // StateFlow to expose immutable UI state
    private val _uiState = MutableStateFlow(AudioUiState())
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()
    private var audioLevelJob: Job? = null
    private var durationJob: Job? = null
    private var transcriptionJob: Job? = null

    init {
        audioRecordingManager.setTranscriptionService(transcriptionService)
        startTranscriptionCollector()
    }

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
                val transcriptionState =
                    if (
                        transcriptionService.supportsLiveTranscription ||
                        transcriptionService.supportsFileTranscription
                    ) {
                        AudioUiState.TranscriptionState.InProgress
                    } else {
                        AudioUiState.TranscriptionState.NotRequested
                    }
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        isPaused = false,
                        isPlaying = false,
                        audioLevels = emptyList(),
                        duration = Duration.ZERO,
                        transcriptionState = transcriptionState,
                        error = null,
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
        viewModelScope.launch { stopRecordingInternal() }
    }

    private suspend fun stopRecordingInternal() {
        Napier.d("AudioViewModel: Stopping recording")
        try {
            val uri = audioRecordingManager.stopRecording()
            stopRecordingCollectors()
            val resolvedDuration =
                uri
                    ?.let { audioDurationResolver.resolveDurationMs(it) }
                    ?.milliseconds

            _uiState.update {
                val updatedTranscription =
                    when (val state = it.transcriptionState) {
                        is AudioUiState.TranscriptionState.Success -> state
                        else ->
                            if (transcriptionService.supportsFileTranscription) {
                                AudioUiState.TranscriptionState.InProgress
                            } else {
                                AudioUiState.TranscriptionState.NotRequested
                            }
                    }
                it.copy(
                    isRecording = false,
                    isPaused = false,
                    recordedAudioUri = uri,
                    duration = resolvedDuration ?: it.duration,
                    transcriptionState = updatedTranscription,
                )
            }

            Napier.d("AudioViewModel: Recording stopped, URI: $uri")
        } catch (e: Exception) {
            Napier.e("Failed to stop recording: ${e.message}", e)
            _uiState.update { it.copy(isRecording = false, isPaused = false, error = "Failed to stop recording") }
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
     * Restarts the recording from scratch, clearing accumulated transcription.
     */
    fun restartRecording() {
        viewModelScope.launch {
            stopRecordingInternal()
            audioRecordingManager.resetTranscription()
            _uiState.update {
                it.copy(
                    transcriptionState = AudioUiState.TranscriptionState.NotRequested,
                    audioLevels = emptyList(),
                    duration = Duration.ZERO,
                    recordedAudioUri = null,
                )
            }
            startRecording()
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
    fun togglePlayback(
        uri: String,
        metadata: AudioPlaybackMetadata? = null,
    ) {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback(uri, metadata)
        }
    }

    /**
     * Starts playing the audio from the given URI.
     */
    fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata? = null,
    ) {
        viewModelScope.launch {
            Napier.d("AudioViewModel: Starting playback of $uri")
            try {
                // Stop any ongoing recording and await completion before starting playback
                if (_uiState.value.isRecording) {
                    stopRecordingInternal()
                }

                _uiState.update {
                    it.copy(
                        isPlaying = true,
                        currentUri = uri,
                        isRecording = false,
                        isPaused = false,
                    )
                }

                audioPlaybackManager.startPlayback(
                    uri = uri,
                    metadata = metadata,
                    onProgressUpdated = { progress ->
                        _uiState.update { it.copy(playbackProgress = progress) }
                    },
                    onPlaybackCompleted = {
                        _uiState.update { it.copy(isPlaying = false, playbackProgress = 0f) }
                    },
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
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        Napier.d("AudioViewModel: Being cleared")
        try {
            stopRecordingCollectors()
            transcriptionJob?.cancel()
            audioRecordingManager.release()
            audioPlaybackManager.release()
        } catch (e: Exception) {
            Napier.e("Error cleaning up audio resources: ${e.message}", e)
        }
    }

    fun clearRecordedAudio() {
        _uiState.update { it.copy(recordedAudioUri = null) }
    }

    /**
     * Resets playback state for a new block. Call when the block being displayed changes
     * (e.g. after deleting a recording and opening another one).
     */
    fun resetPlaybackState() {
        stopRecordingCollectors()
        if (_uiState.value.isPlaying) {
            audioPlaybackManager.stopPlayback()
        }
        _uiState.update {
            it.copy(
                isPlaying = false,
                playbackProgress = 0f,
                currentUri = null,
                recordedAudioUri = null,
                duration = Duration.ZERO,
                audioLevels = emptyList(),
                // Preserve InProgress transcription — a file-transcription job may still be running
                transcriptionState =
                    if (it.transcriptionState is AudioUiState.TranscriptionState.InProgress) {
                        it.transcriptionState
                    } else {
                        AudioUiState.TranscriptionState.NotRequested
                    },
                error = null,
            )
        }
    }

    private fun startRecordingCollectors() {
        audioLevelJob?.cancel()
        durationJob?.cancel()
        audioLevelJob =
            viewModelScope.launch {
                audioRecordingManager.getAudioLevelFlow().collect { level ->
                    _uiState.update { state ->
                        val levels = (state.audioLevels + level).takeLast(50)
                        state.copy(audioLevels = levels)
                    }
                }
            }
        durationJob =
            viewModelScope.launch {
                audioRecordingManager.getRecordingDurationFlow().collect { duration ->
                    _uiState.update { it.copy(duration = duration) }
                }
            }
        startTranscriptionCollector()
    }

    private fun stopRecordingCollectors() {
        audioLevelJob?.cancel()
        durationJob?.cancel()
        transcriptionJob?.cancel()
        audioLevelJob = null
        durationJob = null
        transcriptionJob = null
    }

    private fun startTranscriptionCollector() {
        if (transcriptionJob != null) return
        transcriptionJob =
            viewModelScope.launch {
                audioRecordingManager.getTranscriptionFlow().collect { text ->
                    if (!text.isNullOrBlank()) {
                        _uiState.update { it.copy(transcriptionState = AudioUiState.TranscriptionState.Success(text)) }
                    }
                }
            }
    }
}
