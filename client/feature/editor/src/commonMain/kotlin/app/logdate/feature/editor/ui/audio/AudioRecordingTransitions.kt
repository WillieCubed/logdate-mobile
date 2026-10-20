package app.logdate.feature.editor.ui.audio
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

internal fun AudioViewModel.settleCancelledStart(
    targetNoteId: Uuid?,
    fallbackRecording: AudioUiState?,
) {
    val managerOwnsTarget =
        if (targetNoteId != null) {
            audioRecordingManager.currentRecordingTargetNoteId == targetNoteId
        } else {
            val preparedPath = preparedRecordingPath
            preparedPath != null && audioRecordingManager.currentRecordingPath == preparedPath
        }
    if (managerOwnsTarget && (audioRecordingManager.isRecording || audioRecordingManager.isStartingRecording)) {
        val starting = audioRecordingManager.isStartingRecording && !audioRecordingManager.isRecording
        mutableUiState.update {
            it.withAttachedRecording(
                targetNoteId = targetNoteId,
                filePath = audioRecordingManager.currentRecordingPath,
                paused = audioRecordingManager.currentRecordingPaused,
                starting = starting,
                fallbackFilePath = fallbackRecording?.recordedAudioUri,
            )
        }
        startRecordingCollectors()
    } else {
        preparedRecordingPath = null
        mutableUiState.update { it.withCancelledRecordingStart() }
    }
}

/** Must be called with [recordingMutex] held. A stop while idle is ignored. */
internal suspend fun AudioViewModel.stopRecordingInternal(publishRecording: Boolean = true): String? {
    val session = mutableUiState.value
    val activeOwner = audioRecordingManager.currentRecordingTargetNoteId
    if (activeOwner != null && activeOwner != lastTargetNoteId) {
        stopRecordingCollectors()
        mutableUiState.update { it.copy(isRecording = false, isStartingRecording = false, isPaused = false) }
        return null
    }
    if (!session.isRecording) {
        Napier.d("AudioViewModel: Ignoring stop while not recording")
        return null
    }
    Napier.d("AudioViewModel: Stopping recording")
    var completedUri: String? = null
    try {
        val uri = audioRecordingManager.stopRecording()
        completedUri = uri
        stopRecordingCollectors()
        val resolvedDuration =
            uri
                ?.let { audioDurationResolver.resolveDurationMs(it) }
                ?.milliseconds
        if (uri == null || resolvedDuration == null) {
            preserveFailedRecordingStop(
                session = session,
                pendingFilePath = uri ?: session.recordingFilePath,
            )
            return null
        }
        applyStoppedRecording(uri, session.recordingTargetNoteId, publishRecording, resolvedDuration)
        Napier.d("AudioViewModel: Recording stopped, URI: $uri")
        return uri
    } catch (cancellation: CancellationException) {
        settleCancelledStop(session, completedUri)
        throw cancellation
    } catch (error: Exception) {
        settleFailedStop(session, completedUri, error)
        return null
    }
}

internal suspend fun AudioViewModel.settleCancelledStop(
    session: AudioUiState,
    completedUri: String?,
) {
    val activePath = audioRecordingManager.currentRecordingPath
    val sameSessionStillActive =
        audioRecordingManager.isRecording &&
            session.recordingFilePath != null &&
            activePath?.removePrefix("file://") == session.recordingFilePath.removePrefix("file://")
    if (sameSessionStillActive) {
        mutableUiState.update {
            it.copy(
                isRecording = true,
                isPaused = audioRecordingManager.currentRecordingPaused,
                recordingTargetNoteId = session.recordingTargetNoteId,
                recordingFilePath = activePath,
                recordingFallbackFilePath = session.recordingFallbackFilePath,
                pendingRecoveryFilePath = null,
                recordedAudioUri = null,
                failedRecordingTargetNoteId = null,
                error = null,
            )
        }
    } else {
        preserveFailedRecordingStop(session, completedUri ?: session.recordingFilePath)
    }
}

internal suspend fun AudioViewModel.settleFailedStop(
    session: AudioUiState,
    completedUri: String?,
    error: Exception,
) {
    Napier.e("Failed to stop recording: ${error.message}", error)
    stopRecordingCollectors()
    preserveFailedRecordingStop(session, completedUri ?: session.recordingFilePath)
}

internal suspend fun AudioViewModel.preserveFailedRecordingStop(
    session: AudioUiState,
    pendingFilePath: String?,
) {
    val fallbackUri = session.recordingFallbackFilePath
    val fallbackDuration =
        try {
            fallbackUri
                ?.let { audioDurationResolver.resolveDurationMs(it) }
                ?.milliseconds
        } catch (cancellation: CancellationException) {
            mutableUiState.update {
                it.withFailedRecordingStop(
                    targetNoteId = session.recordingTargetNoteId,
                    fallbackFilePath = fallbackUri,
                    fallbackDuration = null,
                    pendingFilePath = pendingFilePath,
                )
            }
            throw cancellation
        } catch (error: Exception) {
            Napier.w("Could not validate the previous audio take while preserving a failed stop", error)
            null
        }
    mutableUiState.update {
        it.withFailedRecordingStop(
            targetNoteId = session.recordingTargetNoteId,
            fallbackFilePath = fallbackUri,
            fallbackDuration = fallbackDuration,
            pendingFilePath = pendingFilePath,
        )
    }
}

/**
 * Combines the two services' download status flows so the editor can
 * render a single banner. The flows live on each service's own
 * application-scoped state, so a download started in one editor session
 * is still visible the next time the user opens an audio note.
 */
internal fun AudioViewModel.observeEnhancedModelDownloads() {
    viewModelScope.launch {
        combine(
            transcriptionService.offlineModelDownloadStatus,
            audioTaggingService.modelDownloadStatus,
        ) { transcription, tagging ->
            deriveEnhancedAudioModelStatus(transcription, tagging)
        }.distinctUntilChanged()
            .collect { combined ->
                mutableUiState.update { it.copy(enhancedModelStatus = combined) }
            }
    }
}

internal fun AudioViewModel.restoreCancelledRestart(previousRecording: AudioUiState) {
    val owner = audioRecordingManager.currentRecordingTargetNoteId
    val managerPath = audioRecordingManager.currentRecordingPath
    val preparedPath = mutableUiState.value.recordingFilePath
    val ownedSession =
        if (lastTargetNoteId != null) {
            owner == lastTargetNoteId
        } else {
            preparedPath != null && managerPath == preparedPath
        }
    if (ownedSession && (audioRecordingManager.isRecording || managerPath != null)) {
        mutableUiState.update {
            it.withAttachedRecording(
                targetNoteId = lastTargetNoteId,
                filePath = managerPath,
                paused = audioRecordingManager.currentRecordingPaused,
                fallbackFilePath = previousRecording.recordedAudioUri,
                clearRecordedUri = true,
            )
        }
        startRecordingCollectors()
        if (!audioRecordingManager.isRecording) stopRecording()
    } else {
        mutableUiState.update { it.withRestoredRecording(previousRecording) }
    }
}
