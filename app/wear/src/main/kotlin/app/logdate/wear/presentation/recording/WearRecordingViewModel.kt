package app.logdate.wear.presentation.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.presentation.common.SaveFeedback
import app.logdate.wear.recording.WearAudioRecordingManager
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

enum class RecordingPhase {
    READY,
    RECORDING,
    PAUSED,
    SAVING,
    SAVED,
    TOO_SHORT,
    ERROR,
}

data class RecordingUiState(
    val phase: RecordingPhase = RecordingPhase.READY,
    val recordingDurationMs: Long = 0,
    val audioLevels: List<Float> = emptyList(),
    val savedDurationMs: Long = 0,
    val errorMessage: String? = null,
    val saveFeedback: SaveFeedback? = null,
)

sealed interface RecordingScreenEvent {
    data object NavigateBack : RecordingScreenEvent
}

class WearRecordingViewModel(
    private val recordingManager: WearAudioRecordingManager,
    private val notesRepository: JournalNotesRepository,
    private val storageChecker: StorageSpaceChecker,
    private val noteHealthAnnotator: NoteHealthAnnotator,
    private val dataLayerClient: WearDataLayerClient,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    companion object {
        const val MIN_DURATION_MS = 500L
        const val MAX_DURATION_MS = 60_000L
        private const val ONE_MINUTE_RECORDING_SIZE_BYTES = 960 * 1024L
        private const val BUFFER_SPACE_BYTES = 512 * 1024L
        private const val TOO_SHORT_DISPLAY_MS = 1200L
        private const val SAVED_DISPLAY_MS = 800L
    }

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecordingScreenEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<RecordingScreenEvent> = _events.asSharedFlow()

    private var recordingStartTimeMs: Long = 0
    private var accumulatedDurationMs: Long = 0
    private var audioLevelJob: Job? = null
    private var autoStopJob: Job? = null

    override fun onCleared() {
        audioLevelJob?.cancel()
        autoStopJob?.cancel()
        recordingManager.release()
        super.onCleared()
    }

    fun onTouchDown() {
        val currentPhase = _uiState.value.phase
        when (currentPhase) {
            RecordingPhase.READY -> startFreshRecording()
            RecordingPhase.PAUSED -> resumeRecording()
            else -> return
        }
    }

    private fun startFreshRecording() {
        viewModelScope.launch {
            try {
                val availableSpace = storageChecker.getAvailableStorageSpace()
                val requiredSpace = ONE_MINUTE_RECORDING_SIZE_BYTES + BUFFER_SPACE_BYTES
                if (availableSpace < requiredSpace) {
                    _uiState.update {
                        it.copy(
                            phase = RecordingPhase.ERROR,
                            errorMessage = "Not enough storage space",
                        )
                    }
                    return@launch
                }

                val started = recordingManager.startRecording()
                if (!started) {
                    _uiState.update {
                        it.copy(
                            phase = RecordingPhase.ERROR,
                            errorMessage = "Failed to start recording",
                        )
                    }
                    return@launch
                }

                recordingStartTimeMs = clock.now().toEpochMilliseconds()
                accumulatedDurationMs = 0
                _uiState.update {
                    it.copy(
                        phase = RecordingPhase.RECORDING,
                        recordingDurationMs = 0,
                        audioLevels = emptyList(),
                    )
                }

                startAudioLevelCollection()
                startAutoStopTimer()

                Napier.d("Push-to-record recording started")
            } catch (e: Exception) {
                Napier.e("Failed to start recording", e)
                _uiState.update {
                    it.copy(
                        phase = RecordingPhase.ERROR,
                        errorMessage = "Failed to start: ${e.message}",
                    )
                }
            }
        }
    }

    private fun resumeRecording() {
        viewModelScope.launch {
            try {
                val resumed = recordingManager.resumeRecording()
                if (!resumed) {
                    _uiState.update {
                        it.copy(
                            phase = RecordingPhase.ERROR,
                            errorMessage = "Failed to resume recording",
                        )
                    }
                    return@launch
                }

                recordingStartTimeMs = clock.now().toEpochMilliseconds()
                _uiState.update {
                    it.copy(phase = RecordingPhase.RECORDING)
                }

                startAudioLevelCollection()
                startAutoStopTimer()

                Napier.d("Push-to-record recording resumed")
            } catch (e: Exception) {
                Napier.e("Failed to resume recording", e)
                _uiState.update {
                    it.copy(
                        phase = RecordingPhase.ERROR,
                        errorMessage = "Failed to resume: ${e.message}",
                    )
                }
            }
        }
    }

    fun onTouchUp() {
        if (_uiState.value.phase != RecordingPhase.RECORDING) return

        autoStopJob?.cancel()
        autoStopJob = null

        val segmentDurationMs = clock.now().toEpochMilliseconds() - recordingStartTimeMs
        val totalDurationMs = accumulatedDurationMs + segmentDurationMs

        viewModelScope.launch {
            try {
                if (totalDurationMs < MIN_DURATION_MS) {
                    recordingManager.stopRecording()
                    stopAudioLevelCollection()
                    Napier.d("Recording too short: ${totalDurationMs}ms")
                    _uiState.update { it.copy(phase = RecordingPhase.TOO_SHORT) }
                    delay(TOO_SHORT_DISPLAY_MS)
                    _uiState.update { RecordingUiState(phase = RecordingPhase.READY) }
                    return@launch
                }

                val paused = recordingManager.pauseRecording()
                if (!paused) {
                    _uiState.update {
                        it.copy(
                            phase = RecordingPhase.ERROR,
                            errorMessage = "Failed to pause recording",
                        )
                    }
                    return@launch
                }

                stopAudioLevelCollection()
                accumulatedDurationMs = totalDurationMs

                _uiState.update {
                    it.copy(
                        phase = RecordingPhase.PAUSED,
                        recordingDurationMs = totalDurationMs,
                    )
                }

                Napier.d("Recording paused at ${totalDurationMs}ms")
            } catch (e: Exception) {
                Napier.e("Failed to pause recording", e)
                _uiState.update {
                    it.copy(
                        phase = RecordingPhase.ERROR,
                        errorMessage = "Failed to pause: ${e.message}",
                    )
                }
            }
        }
    }

    fun save() {
        if (_uiState.value.phase != RecordingPhase.PAUSED) return

        viewModelScope.launch {
            try {
                val filePath = recordingManager.stopRecording()

                if (filePath == null) {
                    _uiState.update {
                        it.copy(
                            phase = RecordingPhase.ERROR,
                            errorMessage = "Failed to save recording",
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(phase = RecordingPhase.SAVING) }

                val now = clock.now()
                val audioNote =
                    JournalNote.Audio(
                        mediaRef = filePath,
                        uid = Uuid.random(),
                        creationTimestamp = now,
                        lastUpdated = now,
                        durationMs = accumulatedDurationMs,
                    )
                notesRepository.create(audioNote)
                noteHealthAnnotator.annotate(audioNote.uid)

                val feedback =
                    if (dataLayerClient.isPhoneConnected()) {
                        SaveFeedback.SYNCING_TO_PHONE
                    } else {
                        SaveFeedback.SAVED_LOCALLY
                    }

                _uiState.update {
                    it.copy(
                        phase = RecordingPhase.SAVED,
                        savedDurationMs = accumulatedDurationMs,
                        saveFeedback = feedback,
                    )
                }

                Napier.d("Audio note saved: $filePath (${accumulatedDurationMs}ms)")

                delay(SAVED_DISPLAY_MS)
                _events.emit(RecordingScreenEvent.NavigateBack)
            } catch (e: Exception) {
                Napier.e("Failed to save recording", e)
                _uiState.update {
                    it.copy(
                        phase = RecordingPhase.ERROR,
                        errorMessage = "Failed to save: ${e.message}",
                    )
                }
            }
        }
    }

    fun discard() {
        val phase = _uiState.value.phase
        if (phase != RecordingPhase.PAUSED && phase != RecordingPhase.RECORDING) return

        viewModelScope.launch {
            autoStopJob?.cancel()
            autoStopJob = null
            stopAudioLevelCollection()
            recordingManager.stopRecording()
            accumulatedDurationMs = 0
            _uiState.update { RecordingUiState(phase = RecordingPhase.READY) }
            Napier.d("Recording discarded")
        }
    }

    fun onNavigatedBack() {
        _uiState.update { RecordingUiState(phase = RecordingPhase.READY) }
    }

    private fun startAudioLevelCollection() {
        audioLevelJob?.cancel()
        audioLevelJob =
            viewModelScope.launch {
                @OptIn(kotlinx.coroutines.FlowPreview::class)
                recordingManager
                    .getAudioLevelFlow()
                    .sample(periodMillis = 100)
                    .collect { level ->
                        _uiState.update { state ->
                            val levels = (state.audioLevels + level).takeLast(50)
                            val segmentMs = clock.now().toEpochMilliseconds() - recordingStartTimeMs
                            val durationMs = accumulatedDurationMs + segmentMs
                            state.copy(
                                audioLevels = levels,
                                recordingDurationMs = durationMs,
                            )
                        }
                    }
            }
    }

    private fun stopAudioLevelCollection() {
        audioLevelJob?.cancel()
        audioLevelJob = null
    }

    private fun startAutoStopTimer() {
        autoStopJob?.cancel()
        val remainingMs = MAX_DURATION_MS - accumulatedDurationMs
        autoStopJob =
            viewModelScope.launch {
                delay(remainingMs)
                Napier.d("Auto-pause at ${MAX_DURATION_MS}ms")
                onTouchUp()
            }
    }
}
