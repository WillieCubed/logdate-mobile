package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.feature.editor.audio.AudioLabelResolver
import app.logdate.feature.editor.audio.formatAudioLabel
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.RecordingState
import app.logdate.util.formatDateLocalized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * A component that handles audio recording and playback within the editor.
 *
 * This component connects the UI with the ViewModel for audio operations,
 * while keeping proper separation of concerns. It manages state at this level
 * and passes down only the necessary state and callbacks to child components.
 *
 * @param block The audio block state
 * @param onBlockUpdated Callback when the block is updated
 * @param onDeleteRequested Callback when the block should be deleted
 * @param modifier Modifier for layout customization
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun AudioBlockEditor(
    block: AudioBlockUiState,
    onBlockUpdated: (AudioBlockUiState) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelResolver = remember { AudioLabelResolver() }
    val labelResult =
        remember(block.caption, block.timestamp, block.location) {
            labelResolver.resolve(
                createdAt = block.timestamp,
                caption = block.caption,
                latitude = block.location?.latitude,
                longitude = block.location?.longitude,
            )
        }
    val resolvedTitle = formatAudioLabel(labelResult)

    // Get the ViewModel at this level, not in child composables
    val audioViewModel: AudioViewModel = koinViewModel()
    // Collect audio state from ViewModel
    val audioUiState by audioViewModel.uiState.collectAsState()

    // Determine if we're in recording mode or playback mode
    val existingAudioUri = block.uri
    val hasExistingAudio = existingAudioUri != null
    val isRecording = audioUiState.isRecording

    // Determine current recording state
    val recordingState =
        when {
            isRecording -> RecordingState.RECORDING
            else -> RecordingState.INACTIVE
        }

    val audioLevels = audioUiState.audioLevels

    val playbackMetadata =
        remember(block, resolvedTitle) {
            val subtitle = formatDateLocalized(block.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date)
            AudioPlaybackMetadata(
                title = resolvedTitle,
                subtitle = subtitle,
                noteId = block.id,
            )
        }

    // Function to handle saving a recorded audio file
    val handleSaveRecording = { uri: String ->
        onBlockUpdated(
            block.copy(
                captureState =
                    AudioCaptureState.Ready(
                        uri = uri,
                        durationMs = audioUiState.duration.inWholeMilliseconds,
                    ),
                transcription = audioUiState.transcription ?: block.transcription,
            ),
        )
    }

    LaunchedEffect(audioUiState.recordedAudioUri, block.uri) {
        val recordedUri = audioUiState.recordedAudioUri
        if (recordedUri != null && block.uri == null) {
            handleSaveRecording(recordedUri)
            audioViewModel.clearRecordedAudio()
        }
    }

    LaunchedEffect(
        audioUiState.isRecording,
        audioUiState.recordingTargetNoteId,
        audioUiState.recordingFilePath,
        block.captureState,
    ) {
        val ownsActiveRecording =
            audioUiState.isRecording && audioUiState.recordingTargetNoteId == block.id
        if (!ownsActiveRecording) return@LaunchedEffect
        val livePath = audioUiState.recordingFilePath
        val current = block.captureState
        val needsUpdate =
            current !is AudioCaptureState.Recording || current.filePath != livePath
        if (needsUpdate) {
            onBlockUpdated(block.copy(captureState = AudioCaptureState.Recording(filePath = livePath)))
        }
    }

    LaunchedEffect(audioUiState.transcription, block.uri, block.transcription) {
        val latestTranscript = audioUiState.transcription?.trim().orEmpty()
        if (block.uri != null && latestTranscript.isNotBlank() && latestTranscript != block.transcription) {
            onBlockUpdated(block.copy(transcription = latestTranscript))
        }
    }

    // Reset stale playback state when the active block changes, but not on initial composition
    // (LaunchedEffect always fires on first composition regardless of key).
    val previousBlockId = remember { mutableStateOf<kotlin.uuid.Uuid?>(null) }
    LaunchedEffect(block.id) {
        if (previousBlockId.value != null && previousBlockId.value != block.id) {
            audioViewModel.resetPlaybackState()
        }
        previousBlockId.value = block.id
    }

    // Show appropriate audio UI based on the current state
    // Use AudioPermissionWrapper to handle permissions
    Box(modifier = modifier) {
        AudioPermissionWrapper {
            if (existingAudioUri != null) {
                AudioBlockContent(
                    block = block,
                    isExpanded = true,
                    isPlaying = audioUiState.isPlaying,
                    timedTranscript = audioUiState.timedTranscript,
                    playbackProgress = audioUiState.playbackProgress,
                    onPlayPauseClicked = {
                        audioViewModel.togglePlayback(existingAudioUri, playbackMetadata)
                    },
                    onSeekPositionChanged = { position ->
                        audioViewModel.seekTo(position)
                    },
                    onSeekTimestampClicked = { positionMs ->
                        audioViewModel.seekToPositionMs(positionMs, block.duration)
                    },
                    onDeleteClicked = onDeleteRequested,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (isRecording) {
                ActiveRecordingDisplay(
                    audioLevels = audioLevels,
                    recordingDuration = audioUiState.duration,
                    transcriptionText = audioUiState.transcription,
                    transcriptionIsFinal =
                        (audioUiState.transcriptionState as? AudioUiState.TranscriptionState.Success)?.isFinal == true,
                    transcriptionIsRefining =
                        (audioUiState.transcriptionState as? AudioUiState.TranscriptionState.Success)?.isRefining == true,
                    isPaused = audioUiState.isPaused,
                    onRestart = { audioViewModel.restartRecording() },
                    onPause = { audioViewModel.toggleRecordingPause() },
                    onFinish = { audioViewModel.stopRecording() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AudioRecordingControls(
                    recordingState = recordingState,
                    audioLevels = audioLevels,
                    recordingDuration = audioUiState.duration,
                    onStartRecording = { audioViewModel.startRecording(targetNoteId = block.id) },
                    onStopRecording = { audioViewModel.stopRecording() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
