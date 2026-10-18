package app.logdate.feature.editor.ui.audio

import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.delegate.PendingAudioRecoverer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_error_recording_not_finalized
import logdate.client.feature.editor.generated.resources.editor_error_recording_not_recovered
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/** Keeps recovered and active takes serialized with recording start and stop transitions. */
internal class PendingAudioResolutionCoordinator(
    private val state: MutableStateFlow<AudioUiState>,
    private val mutex: Mutex,
    private val recoverer: PendingAudioRecoverer,
    private val stopRecording: suspend (publishRecording: Boolean) -> String?,
) {
    suspend fun resolve(
        blockId: Uuid,
        recordingOwner: Uuid?,
    ): AudioCaptureState? {
        if (recordingOwner != blockId) return null
        return mutex.withLock { resolveLocked(blockId) }
    }

    private suspend fun resolveLocked(blockId: Uuid): AudioCaptureState? {
        val initial = state.value
        val recoveryPath = initial.pendingRecoveryFilePath
        if (recoveryPath != null) {
            resolveRecovery(blockId, initial, recoveryPath)?.let { return it }
        }
        return resolveCompletedOrActiveRecording(initial)
    }

    private suspend fun resolveRecovery(
        blockId: Uuid,
        initial: AudioUiState,
        recoveryPath: String,
    ): AudioCaptureState? {
        val stopping = AudioCaptureState.Stopping(recoveryPath, initial.recordingFallbackFilePath)
        return when (val recovered = recoverer.recover(stopping)) {
            is AudioCaptureState.Ready -> settleRecovered(recovered)
            is AudioCaptureState.Recording -> stopAndResolve(blockId, initial, recoveryPath, recovered)
            is AudioCaptureState.Failed -> keepRecoveryPending(initial, recoveryPath, recovered)
            else -> null
        }
    }

    private fun settleRecovered(recovered: AudioCaptureState.Ready): AudioCaptureState.Ready {
        state.update {
            it.copy(
                pendingRecoveryFilePath = null,
                recordingFallbackFilePath = null,
                duration = recovered.durationMs.milliseconds,
                failedRecordingTargetNoteId = null,
                error = null,
            )
        }
        return recovered
    }

    private suspend fun stopAndResolve(
        blockId: Uuid,
        initial: AudioUiState,
        recoveryPath: String,
        recording: AudioCaptureState.Recording,
    ): AudioCaptureState {
        state.update {
            it.copy(
                isRecording = true,
                recordingTargetNoteId = blockId,
                recordingFilePath = recording.filePath,
                recordingFallbackFilePath = recording.fallbackFilePath,
                pendingRecoveryFilePath = null,
                error = null,
            )
        }
        val verified =
            try {
                stopRecording(false)
                recoverer.recover(AudioCaptureState.Stopping(recoveryPath, initial.recordingFallbackFilePath))
            } catch (cancellation: CancellationException) {
                restorePending(initial, recoveryPath)
                throw cancellation
            }
        if (verified is AudioCaptureState.Ready) return settleRecovered(verified)
        state.update {
            it.copy(
                pendingRecoveryFilePath = recoveryPath,
                recordingFallbackFilePath = initial.recordingFallbackFilePath,
                recordingFilePath = null,
                recordingTargetNoteId = null,
                recordedAudioUri = null,
            )
        }
        return AudioCaptureState.Failed(Res.string.editor_error_recording_not_recovered)
    }

    private fun keepRecoveryPending(
        initial: AudioUiState,
        recoveryPath: String,
        result: AudioCaptureState.Failed,
    ): AudioCaptureState.Failed {
        state.update {
            it.copy(
                pendingRecoveryFilePath = recoveryPath,
                recordingFallbackFilePath = initial.recordingFallbackFilePath,
                failedRecordingTargetNoteId = null,
                error = null,
            )
        }
        return result
    }

    private suspend fun resolveCompletedOrActiveRecording(initial: AudioUiState): AudioCaptureState? {
        val completedUri = initial.recordedAudioUri
        if (completedUri != null) {
            state.update { it.copy(recordedAudioUri = null) }
            return AudioCaptureState.Ready(uri = completedUri, durationMs = initial.duration.inWholeMilliseconds)
        }
        if (!initial.isRecording) return null
        stopRecording(true)
        val after = state.value
        val uri = after.recordedAudioUri ?: return AudioCaptureState.Failed(Res.string.editor_error_recording_not_finalized)
        state.update { it.copy(recordedAudioUri = null) }
        return AudioCaptureState.Ready(uri = uri, durationMs = after.duration.inWholeMilliseconds)
    }

    private fun restorePending(
        initial: AudioUiState,
        recoveryPath: String,
    ) {
        state.update {
            it.copy(
                pendingRecoveryFilePath = recoveryPath,
                recordingFallbackFilePath = initial.recordingFallbackFilePath,
                recordingFilePath = null,
                recordingTargetNoteId = null,
                recordedAudioUri = null,
            )
        }
    }

}
