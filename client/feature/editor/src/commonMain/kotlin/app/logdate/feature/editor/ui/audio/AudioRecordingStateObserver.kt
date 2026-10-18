package app.logdate.feature.editor.ui.audio

import app.logdate.client.media.audio.AudioRecordingManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/** Binds a screen's state to its process-owned recording manager while the editor is visible. */
internal class AudioRecordingStateObserver(
    private val scope: CoroutineScope,
    private val manager: AudioRecordingManager,
    private val state: MutableStateFlow<AudioUiState>,
    private val recordingOwner: () -> Uuid?,
    private val stopRecording: () -> Unit,
    private val failStart: () -> Unit,
) {
    private var audioLevelJob: Job? = null
    private var durationJob: Job? = null
    private var recordingStateJob: Job? = null
    private var recordingStartingJob: Job? = null
    private var recordingPausedJob: Job? = null
    private var transcriptionJob: Job? = null

    fun start() {
        stop()
        recordingStateJob = scope.launch { observeRecordingState() }
        recordingStartingJob = scope.launch { observeRecordingStart() }
        recordingPausedJob =
            scope.launch { manager.getRecordingPausedFlow().collect { paused -> state.update { it.copy(isPaused = paused) } } }
        audioLevelJob = scope.launch { observeAudioLevels() }
        durationJob =
            scope.launch { manager.getRecordingDurationFlow().collect { duration -> state.update { it.copy(duration = duration) } } }
        transcriptionJob = scope.launch { observeTranscription() }
    }

    fun stop() {
        audioLevelJob?.cancel()
        durationJob?.cancel()
        recordingStateJob?.cancel()
        recordingStartingJob?.cancel()
        recordingPausedJob?.cancel()
        transcriptionJob?.cancel()
        audioLevelJob = null
        durationJob = null
        recordingStateJob = null
        recordingStartingJob = null
        recordingPausedJob = null
        transcriptionJob = null
    }

    private suspend fun observeRecordingState() {
        manager
            .getRecordingStateFlow()
            .dropWhile { active -> !active }
            .collect { active ->
                val owner = manager.currentRecordingTargetNoteId
                if (active && state.value.isStartingRecording && belongsToScreen(owner)) {
                    state.update { it.copy(isRecording = true, isStartingRecording = false) }
                } else if (!active && state.value.isRecording) {
                    Napier.d("AudioViewModel: Recording ended outside the editor; finalizing")
                    stopRecording()
                }
            }
    }

    private suspend fun observeRecordingStart() {
        manager.getRecordingStartingFlow().collect { starting ->
            if (!starting && state.value.isStartingRecording) completeAttachedStart()
        }
    }

    private fun completeAttachedStart() {
        val owner = manager.currentRecordingTargetNoteId
        if (manager.isRecording && belongsToScreen(owner)) {
            state.update { it.copy(isRecording = true, isStartingRecording = false) }
        } else if (owner == recordingOwner() && manager.currentRecordingPath != null) {
            state.update { it.copy(isRecording = true, isStartingRecording = false) }
            stopRecording()
        } else {
            stop()
            failStart()
        }
    }

    private fun belongsToScreen(owner: Uuid?): Boolean = owner == null || owner == recordingOwner()

    private suspend fun observeAudioLevels() {
        manager.getAudioLevelFlow().collect { level ->
            state.update { current -> current.copy(audioLevels = (current.audioLevels + level).takeLast(50)) }
        }
    }

    private suspend fun observeTranscription() {
        manager
            .getStructuredTranscriptionFlow()
            .distinctUntilChanged()
            .collect { result -> state.update { it.withTranscriptionResult(result) } }
    }
}
