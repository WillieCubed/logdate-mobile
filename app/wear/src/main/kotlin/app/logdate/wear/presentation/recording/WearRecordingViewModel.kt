package app.logdate.wear.presentation.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SystemCaptureTimeZone
import app.logdate.wear.haptic.WearHapticEngine
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.presentation.common.SaveFeedback
import app.logdate.wear.recording.RecordingStartFailure
import app.logdate.wear.recording.WearAudioRecordingManager
import app.logdate.wear.recording.WearRecorder
import app.logdate.wear.sync.WearDataLayerClient
import app.logdate.wear.sync.WearNoteRemovalNotifier
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

enum class RecordingPhase {
    READY,
    STARTING,
    RECORDING,
    PAUSED,
    SAVING,
    SAVED,
    TOO_SHORT,
    ERROR,
}

/** What went wrong, for the screen to explain in words. */
enum class RecordingError {
    MICROPHONE_PERMISSION_DENIED,
    NOT_ENOUGH_STORAGE,
    RECORDER_UNAVAILABLE,

    /** The recording finished but could not be stored. Its file is intact and pressing again saves it. */
    SAVE_FAILED,

    /** The recorder produced no usable file, so there is nothing to save. */
    RECORDING_LOST,
}

data class RecordingUiState(
    val phase: RecordingPhase = RecordingPhase.READY,
    val recordingDurationMs: Long = 0,
    val audioLevels: List<Float> = emptyList(),
    /** True once a tap started the recording, so it keeps going after the finger lifts. */
    val isLatched: Boolean = false,
    val pausedByInterruption: Boolean = false,
    val savedDurationMs: Long = 0,
    /** The note just saved, while it can still be undone. */
    val undoableNoteId: Uuid? = null,
    val saveFeedback: SaveFeedback? = null,
    val error: RecordingError? = null,
    val showGestureHint: Boolean = false,
)

/** Remembers whether the tap-or-hold hint has already been shown. */
interface RecordingHintStore {
    fun hasSeenHint(): Boolean

    fun markHintSeen()
}

/**
 * Drives the watch's voice recorder.
 *
 * Pressing the record surface starts recording at once. Lifting the finger quickly (a tap) latches
 * the recording so it continues until the next tap; holding past [HOLD_THRESHOLD_MS] is
 * push-to-talk and saves on release. While a latched recording is paused, the record surface saves
 * it and the side control resumes. A saved note stays undoable for [UNDO_WINDOW_MS].
 *
 * Every phase change happens before the recorder is asked to do anything, so the recorder's own
 * state flow can never be mistaken for the recorder ending a session by itself.
 *
 * Leaving the screen never drops a recording. A recording still running is stopped and saved on
 * [applicationScope], which outlives this ViewModel, and one that failed to save is saved again.
 */
class WearRecordingViewModel(
    private val recorder: WearRecorder,
    private val notesRepository: JournalNotesRepository,
    private val durationResolver: AudioDurationResolver,
    private val noteHealthAnnotator: NoteHealthAnnotator,
    private val dataLayerClient: WearDataLayerClient,
    private val locationCaptureCoordinator: WearLocationCaptureCoordinator,
    private val haptics: WearHapticEngine,
    private val hintStore: RecordingHintStore,
    private val removalNotifier: WearNoteRemovalNotifier,
    private val applicationScope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val deleteFile: (String) -> Unit = { path -> File(path).delete() },
) : ViewModel() {
    companion object {
        const val HOLD_THRESHOLD_MS = 400L
        const val MIN_DURATION_MS = 500L
        const val UNDO_WINDOW_MS = 5_000L
        const val TOO_SHORT_DISPLAY_MS = 1_200L
        const val LOCATION_TIMEOUT_MS = 3_000L
        private const val MAX_LEVEL_SAMPLES = 50
        private val NEAR_LIMIT = WearAudioRecordingManager.MAX_RECORDING_DURATION - 1.minutes
    }

    private val _uiState = MutableStateFlow(RecordingUiState(showGestureHint = !hintStore.hasSeenHint()))
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var pressedAtMs = 0L
    private var releasedAtMs: Long? = null
    private var nearLimitWarned = false
    private var undoJob: Job? = null

    /** A finalized recording that could not be stored. The next press saves it instead of starting a new one. */
    private var unsavedPath: String? = null

    init {
        observeRecorder()
    }

    override fun onCleared() {
        when (_uiState.value.phase) {
            RecordingPhase.RECORDING, RecordingPhase.PAUSED -> applicationScope.launch { stopAndSaveDetached() }
            RecordingPhase.STARTING -> recorder.requestStop()
            RecordingPhase.ERROR -> unsavedPath?.let { path -> applicationScope.launch { saveDetached(path) } }
            else -> Unit
        }
        super.onCleared()
    }

    fun onPress() {
        when (_uiState.value.phase) {
            RecordingPhase.READY -> beginRecording()
            RecordingPhase.ERROR -> retrySaveOrBeginRecording()
            RecordingPhase.SAVED -> {
                // Recording again keeps the last note; the undo window ends.
                undoJob?.cancel()
                beginRecording()
            }
            RecordingPhase.RECORDING -> if (_uiState.value.isLatched) stopAndSave()
            RecordingPhase.PAUSED -> stopAndSave()
            RecordingPhase.STARTING, RecordingPhase.SAVING, RecordingPhase.TOO_SHORT -> Unit
        }
    }

    fun onRelease() {
        val state = _uiState.value
        val recorderRunning = state.phase == RecordingPhase.RECORDING || state.phase == RecordingPhase.PAUSED
        when {
            state.phase == RecordingPhase.STARTING -> releasedAtMs = now()
            recorderRunning && !state.isLatched -> applyRelease(now())
        }
    }

    fun onPauseToggle() {
        when (_uiState.value.phase) {
            RecordingPhase.RECORDING -> viewModelScope.launch { recorder.pause() }
            RecordingPhase.PAUSED -> resume()
            else -> Unit
        }
    }

    fun onDiscard() {
        val phase = _uiState.value.phase
        if (phase != RecordingPhase.RECORDING && phase != RecordingPhase.PAUSED) return
        _uiState.update { RecordingUiState(showGestureHint = it.showGestureHint) }
        viewModelScope.launch { recorder.discard() }
    }

    fun onUndo() {
        val noteId = _uiState.value.undoableNoteId ?: return
        if (_uiState.value.phase != RecordingPhase.SAVED) return
        undoJob?.cancel()
        _uiState.update { RecordingUiState(showGestureHint = it.showGestureHint) }
        viewModelScope.launch {
            try {
                val mediaRef = (notesRepository.getNoteById(noteId) as? JournalNote.Audio)?.mediaRef
                notesRepository.removeById(noteId)
                if (mediaRef != null && notesRepository.getNoteById(noteId) == null) deleteFile(mediaRef)
                // The outbox drops a delete that meets a pending create, but the phone may already have
                // the note, so it is told directly.
                removalNotifier.notifyRemoved(noteId)
                haptics.rejection()
            } catch (e: Exception) {
                Napier.e("Failed to undo saved recording $noteId", e)
            }
        }
    }

    private fun retrySaveOrBeginRecording() {
        val path = unsavedPath ?: return beginRecording()
        _uiState.update { it.copy(phase = RecordingPhase.SAVING, error = null) }
        viewModelScope.launch { saveRecording(path) }
    }

    private fun beginRecording() {
        unsavedPath = null
        pressedAtMs = now()
        releasedAtMs = null
        nearLimitWarned = false
        _uiState.update { RecordingUiState(phase = RecordingPhase.STARTING, showGestureHint = it.showGestureHint) }
        viewModelScope.launch {
            if (!recorder.start()) {
                _uiState.update { it.copy(phase = RecordingPhase.ERROR, error = recorder.lastStartFailure.toError()) }
                haptics.rejection()
                return@launch
            }
            haptics.startRecording()
            _uiState.update { it.copy(phase = RecordingPhase.RECORDING) }
            // The recorder can end between start() returning and the phase changing, and the flow does not repeat itself.
            if (!recorder.isRecording.value) {
                stopAndSave()
                return@launch
            }
            releasedAtMs?.let(::applyRelease)
        }
    }

    private fun applyRelease(releasedAt: Long) {
        if (releasedAt - pressedAtMs < HOLD_THRESHOLD_MS) {
            _uiState.update { it.copy(isLatched = true) }
        } else {
            stopAndSave()
        }
    }

    private fun resume() {
        viewModelScope.launch { recorder.resume() }
    }

    private fun stopAndSave() {
        _uiState.update { it.copy(phase = RecordingPhase.SAVING) }
        viewModelScope.launch {
            // Stopping and saving finish even if the screen is cleared partway through.
            withContext(NonCancellable) {
                haptics.stopRecording()
                val path = recorder.stop()
                if (path == null) {
                    Napier.e("Recording ended without a file")
                    _uiState.update { it.copy(phase = RecordingPhase.ERROR, error = RecordingError.RECORDING_LOST) }
                    return@withContext
                }
                saveRecording(path)
            }
        }
    }

    /**
     * How long the recording is. The file's own length wins when it reads as a real recording. A
     * missing or empty reading falls back to the recorder's clock, so a metadata hiccup cannot make a
     * long recording look too short to keep.
     */
    private suspend fun recordedDurationMs(path: String): Long {
        val resolved = durationResolver.resolveDurationMs(path)
        if (resolved != null && resolved >= MIN_DURATION_MS) return resolved
        return maxOf(resolved ?: 0L, recorder.elapsed.value.inWholeMilliseconds)
    }

    private suspend fun persistNote(
        path: String,
        durationMs: Long,
    ): JournalNote.Audio {
        val now = clock.now()
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { locationCaptureCoordinator.captureForJournalEntry() }
        val note =
            JournalNote.Audio(
                mediaRef = path,
                uid = Uuid.random(),
                creationTimestamp = now,
                lastUpdated = now,
                durationMs = durationMs,
                location = location,
                timeZoneId = SystemCaptureTimeZone.currentTimeZoneId(),
            )
        notesRepository.create(note)
        // The raw file marks a recording as unsaved for startup recovery, so it goes once the note exists.
        deleteRawRecording(path)
        noteHealthAnnotator.annotate(note.uid)
        return note
    }

    /** Stops the recorder and stores its file after the screen is gone, so there is no state to update. */
    private suspend fun stopAndSaveDetached() {
        val path = recorder.stop()
        if (path == null) {
            Napier.e("Recording ended without a file when the screen was cleared")
            return
        }
        saveDetached(path)
    }

    private suspend fun saveDetached(path: String) {
        val durationMs = recordedDurationMs(path)
        if (durationMs < MIN_DURATION_MS) {
            deleteRecording(path)
            return
        }
        try {
            persistNote(path, durationMs)
        } catch (e: Exception) {
            Napier.e("Failed to save recording $path after the screen was cleared", e)
        }
    }

    private suspend fun saveRecording(path: String) {
        val durationMs = recordedDurationMs(path)
        if (durationMs < MIN_DURATION_MS) {
            discardTooShort(path)
            return
        }
        try {
            val note = persistNote(path, durationMs)
            onSaved(note, durationMs)
        } catch (e: Exception) {
            Napier.e("Failed to save recording $path", e)
            unsavedPath = path
            _uiState.update { it.copy(phase = RecordingPhase.ERROR, error = RecordingError.SAVE_FAILED) }
        }
    }

    private suspend fun onSaved(
        note: JournalNote.Audio,
        durationMs: Long,
    ) {
        unsavedPath = null
        val feedback =
            if (dataLayerClient.isPhoneConnected()) SaveFeedback.SYNCING_TO_PHONE else SaveFeedback.SAVED_LOCALLY
        hintStore.markHintSeen()
        haptics.success()
        _uiState.update {
            RecordingUiState(
                phase = RecordingPhase.SAVED,
                savedDurationMs = durationMs,
                undoableNoteId = note.uid,
                saveFeedback = feedback,
            )
        }
        undoJob?.cancel()
        undoJob =
            viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                _uiState.update { RecordingUiState() }
            }
    }

    private fun deleteRecording(path: String) {
        deleteFile(path)
        deleteRawRecording(path)
    }

    private fun deleteRawRecording(path: String) {
        val raw = CrashSafeRecording.inFlightFile(File(path))
        if (raw.isFile) deleteFile(raw.path)
    }

    private suspend fun discardTooShort(path: String) {
        unsavedPath = null
        deleteRecording(path)
        haptics.rejection()
        _uiState.update { it.copy(phase = RecordingPhase.TOO_SHORT) }
        delay(TOO_SHORT_DISPLAY_MS)
        _uiState.update { RecordingUiState(showGestureHint = it.showGestureHint) }
    }

    private fun observeRecorder() {
        viewModelScope.launch {
            recorder.audioLevel.collect { level ->
                _uiState.update { state ->
                    if (state.phase != RecordingPhase.RECORDING) return@update state
                    state.copy(audioLevels = (state.audioLevels + level).takeLast(MAX_LEVEL_SAMPLES))
                }
            }
        }
        viewModelScope.launch {
            recorder.elapsed.collect { elapsed ->
                _uiState.update { state ->
                    val live = state.phase == RecordingPhase.RECORDING || state.phase == RecordingPhase.PAUSED
                    if (live) state.copy(recordingDurationMs = elapsed.inWholeMilliseconds) else state
                }
                if (!nearLimitWarned && _uiState.value.phase == RecordingPhase.RECORDING && elapsed >= NEAR_LIMIT) {
                    nearLimitWarned = true
                    haptics.warning()
                }
            }
        }
        viewModelScope.launch {
            combine(recorder.isPaused, recorder.pausedByInterruption) { paused, interrupted -> paused to interrupted }
                .collect { (paused, interrupted) -> onPauseStateChanged(paused, interrupted) }
        }
        viewModelScope.launch {
            recorder.isRecording.collect { recording ->
                val phase = _uiState.value.phase
                if (!recording && (phase == RecordingPhase.RECORDING || phase == RecordingPhase.PAUSED)) stopAndSave()
            }
        }
    }

    private fun onPauseStateChanged(
        paused: Boolean,
        interrupted: Boolean,
    ) {
        val phase = _uiState.value.phase
        when {
            paused && phase == RecordingPhase.RECORDING -> {
                haptics.pause()
                _uiState.update { it.copy(phase = RecordingPhase.PAUSED, pausedByInterruption = interrupted) }
            }
            paused && phase == RecordingPhase.PAUSED -> {
                _uiState.update { it.copy(pausedByInterruption = interrupted) }
            }
            !paused && phase == RecordingPhase.PAUSED -> {
                haptics.resume()
                _uiState.update { it.copy(phase = RecordingPhase.RECORDING, pausedByInterruption = false) }
            }
        }
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private fun RecordingStartFailure?.toError(): RecordingError =
        when (this) {
            RecordingStartFailure.MICROPHONE_PERMISSION_DENIED -> RecordingError.MICROPHONE_PERMISSION_DENIED
            RecordingStartFailure.NOT_ENOUGH_STORAGE -> RecordingError.NOT_ENOUGH_STORAGE
            RecordingStartFailure.RECORDER_UNAVAILABLE, null -> RecordingError.RECORDER_UNAVAILABLE
        }
}
