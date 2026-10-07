package app.logdate.feature.editor.ui.audio

import app.logdate.client.media.audio.transcription.TranscriptionResult
import kotlin.time.Duration
import kotlin.uuid.Uuid

internal fun AudioUiState.withAttachedRecording(
    targetNoteId: Uuid?,
    filePath: String?,
    paused: Boolean,
    starting: Boolean = false,
    clearRecordedUri: Boolean = false,
): AudioUiState =
    copy(
        isRecording = !starting,
        isStartingRecording = starting,
        isPaused = paused,
        recordingTargetNoteId = targetNoteId,
        recordingFilePath = filePath,
        recordedAudioUri = if (clearRecordedUri) null else recordedAudioUri,
        failedRecordingTargetNoteId = if (clearRecordedUri) null else failedRecordingTargetNoteId,
        error = null,
    )

internal fun AudioUiState.withStartedRecording(
    targetNoteId: Uuid?,
    filePath: String?,
    transcriptionAvailable: Boolean,
): AudioUiState =
    copy(
        isRecording = true,
        isStartingRecording = false,
        isPaused = false,
        isPlaying = false,
        audioLevels = emptyList(),
        duration = Duration.ZERO,
        transcriptionState =
            when {
                transcriptionState is AudioUiState.TranscriptionState.Error -> transcriptionState
                transcriptionAvailable -> AudioUiState.TranscriptionState.InProgress
                else -> AudioUiState.TranscriptionState.NotRequested
            },
        error = null,
        recordingFilePath = filePath,
        recordingTargetNoteId = targetNoteId,
    )

internal fun AudioUiState.withFailedRecordingStart(
    message: String,
    lastTargetNoteId: Uuid?,
    fallback: AudioUiState?,
): AudioUiState =
    copy(
        isRecording = false,
        isStartingRecording = false,
        failedRecordingTargetNoteId = if (fallback == null) recordingTargetNoteId ?: lastTargetNoteId else null,
        recordingTargetNoteId = null,
        recordingFilePath = null,
        recordedAudioUri = fallback?.recordedAudioUri ?: recordedAudioUri,
        duration = fallback?.duration ?: duration,
        transcriptionState = fallback?.transcriptionState ?: transcriptionState,
        error = message,
    )

internal fun AudioUiState.withStoppedRecording(
    uri: String?,
    targetNoteId: Uuid?,
    publishRecording: Boolean,
    resolvedDuration: Duration?,
    fileTranscriptionAvailable: Boolean,
): AudioUiState =
    copy(
        isRecording = false,
        isPaused = false,
        recordedAudioUri = if (publishRecording) uri else null,
        recordingFilePath = if (publishRecording) null else uri,
        recordingTargetNoteId = if (publishRecording || uri == null) null else targetNoteId,
        duration = resolvedDuration ?: duration,
        transcriptionState =
            when {
                transcriptionState is AudioUiState.TranscriptionState.Success -> transcriptionState
                fileTranscriptionAvailable -> AudioUiState.TranscriptionState.InProgress
                else -> AudioUiState.TranscriptionState.NotRequested
            },
        failedRecordingTargetNoteId = if (uri == null) targetNoteId else null,
        error = if (uri == null) "Recording could not be saved" else error,
    )

internal fun AudioUiState.withFailedRecordingStop(targetNoteId: Uuid?): AudioUiState =
    copy(
        isRecording = false,
        isPaused = false,
        recordingFilePath = null,
        recordingTargetNoteId = null,
        failedRecordingTargetNoteId = targetNoteId,
        error = "Recording could not be saved",
    )

internal fun AudioUiState.withRestoredRecording(previous: AudioUiState): AudioUiState =
    copy(
        isRecording = false,
        isStartingRecording = false,
        isPaused = false,
        recordingTargetNoteId = null,
        recordingFilePath = null,
        recordedAudioUri = previous.recordedAudioUri,
        duration = previous.duration,
        transcriptionState = previous.transcriptionState,
        failedRecordingTargetNoteId = null,
        error = previous.error,
    )

internal fun AudioUiState.withTranscriptionResult(result: TranscriptionResult): AudioUiState =
    when (result) {
        is TranscriptionResult.Success ->
            copy(
                transcriptionState =
                    AudioUiState.TranscriptionState.Success(
                        text = result.text,
                        timedTranscript = result.timedTranscript,
                        isFinal = result.isFinal,
                        isRefining = result.isRefining,
                    ),
            )
        is TranscriptionResult.Error -> copy(transcriptionState = AudioUiState.TranscriptionState.Error(result.reason))
        is TranscriptionResult.InProgress ->
            if (transcriptionState is AudioUiState.TranscriptionState.Success) {
                this
            } else {
                copy(transcriptionState = AudioUiState.TranscriptionState.InProgress)
            }
        TranscriptionResult.Cancelled -> copy(transcriptionState = AudioUiState.TranscriptionState.NotRequested)
    }
