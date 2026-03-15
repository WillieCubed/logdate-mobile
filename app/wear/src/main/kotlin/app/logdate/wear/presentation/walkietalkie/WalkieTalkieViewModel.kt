package app.logdate.wear.presentation.walkietalkie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.recording.WearAudioRecordingManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

enum class WalkieTalkiePhase {
    READY,
    RECORDING,
    SAVING,
    SAVED,
    TOO_SHORT,
    ERROR,
}

data class WalkieTalkieUiState(
    val phase: WalkieTalkiePhase = WalkieTalkiePhase.READY,
    val recordingDurationMs: Long = 0,
    val audioLevels: List<Float> = emptyList(),
    val savedDurationMs: Long = 0,
    val errorMessage: String? = null,
)

sealed interface WalkieTalkieEvent {
    data object NavigateBack : WalkieTalkieEvent
}

class WalkieTalkieViewModel(
    private val recordingManager: WearAudioRecordingManager,
    private val notesRepository: JournalNotesRepository,
    private val storageChecker: StorageSpaceChecker,
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

    private val _uiState = MutableStateFlow(WalkieTalkieUiState())
    val uiState: StateFlow<WalkieTalkieUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WalkieTalkieEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<WalkieTalkieEvent> = _events.asSharedFlow()

    private var recordingStartTimeMs: Long = 0
    private var audioLevelJob: Job? = null
    private var autoStopJob: Job? = null

    override fun onCleared() {
        audioLevelJob?.cancel()
        autoStopJob?.cancel()
        recordingManager.release()
        super.onCleared()
    }

    fun onTouchDown() {
        if (_uiState.value.phase != WalkieTalkiePhase.READY) return

        viewModelScope.launch {
            try {
                val availableSpace = storageChecker.getAvailableStorageSpace()
                val requiredSpace = ONE_MINUTE_RECORDING_SIZE_BYTES + BUFFER_SPACE_BYTES
                if (availableSpace < requiredSpace) {
                    _uiState.update {
                        it.copy(
                            phase = WalkieTalkiePhase.ERROR,
                            errorMessage = "Not enough storage space",
                        )
                    }
                    return@launch
                }

                val started = recordingManager.startRecording()
                if (!started) {
                    _uiState.update {
                        it.copy(
                            phase = WalkieTalkiePhase.ERROR,
                            errorMessage = "Failed to start recording",
                        )
                    }
                    return@launch
                }

                recordingStartTimeMs = clock.now().toEpochMilliseconds()
                _uiState.update {
                    it.copy(
                        phase = WalkieTalkiePhase.RECORDING,
                        recordingDurationMs = 0,
                        audioLevels = emptyList(),
                    )
                }

                startAudioLevelCollection()
                startAutoStopTimer()

                Napier.d("Walkie-talkie recording started")
            } catch (e: Exception) {
                Napier.e("Failed to start walkie-talkie recording", e)
                _uiState.update {
                    it.copy(
                        phase = WalkieTalkiePhase.ERROR,
                        errorMessage = "Failed to start: ${e.message}",
                    )
                }
            }
        }
    }

    fun onTouchUp() {
        if (_uiState.value.phase != WalkieTalkiePhase.RECORDING) return

        autoStopJob?.cancel()
        autoStopJob = null

        viewModelScope.launch {
            try {
                val filePath = recordingManager.stopRecording()
                stopAudioLevelCollection()

                val durationMs = clock.now().toEpochMilliseconds() - recordingStartTimeMs

                if (durationMs < MIN_DURATION_MS) {
                    Napier.d("Recording too short: ${durationMs}ms")
                    _uiState.update { it.copy(phase = WalkieTalkiePhase.TOO_SHORT) }
                    delay(TOO_SHORT_DISPLAY_MS)
                    _uiState.update {
                        WalkieTalkieUiState(phase = WalkieTalkiePhase.READY)
                    }
                    return@launch
                }

                if (filePath == null) {
                    _uiState.update {
                        it.copy(
                            phase = WalkieTalkiePhase.ERROR,
                            errorMessage = "Failed to save recording",
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(phase = WalkieTalkiePhase.SAVING) }

                val now = clock.now()
                val audioNote = JournalNote.Audio(
                    mediaRef = filePath,
                    uid = Uuid.random(),
                    creationTimestamp = now,
                    lastUpdated = now,
                    durationMs = durationMs,
                )
                notesRepository.create(audioNote)

                _uiState.update {
                    it.copy(
                        phase = WalkieTalkiePhase.SAVED,
                        savedDurationMs = durationMs,
                    )
                }

                Napier.d("Walkie-talkie note saved: $filePath (${durationMs}ms)")

                delay(SAVED_DISPLAY_MS)
                _events.emit(WalkieTalkieEvent.NavigateBack)
            } catch (e: Exception) {
                Napier.e("Failed to save walkie-talkie recording", e)
                _uiState.update {
                    it.copy(
                        phase = WalkieTalkiePhase.ERROR,
                        errorMessage = "Failed to save: ${e.message}",
                    )
                }
            }
        }
    }

    fun onNavigatedBack() {
        _uiState.update { WalkieTalkieUiState(phase = WalkieTalkiePhase.READY) }
    }

    private fun startAudioLevelCollection() {
        audioLevelJob?.cancel()
        audioLevelJob = viewModelScope.launch {
            recordingManager.getAudioLevelFlow().collect { level ->
                _uiState.update { state ->
                    val levels = (state.audioLevels + level).takeLast(50)
                    val durationMs = clock.now().toEpochMilliseconds() - recordingStartTimeMs
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
        autoStopJob = viewModelScope.launch {
            delay(MAX_DURATION_MS)
            Napier.d("Walkie-talkie auto-stop at ${MAX_DURATION_MS}ms")
            onTouchUp()
        }
    }
}
