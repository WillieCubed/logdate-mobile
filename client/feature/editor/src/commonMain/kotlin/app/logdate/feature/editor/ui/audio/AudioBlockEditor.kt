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
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
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
    // Get the ViewModel at this level, not in child composables
    val audioViewModel: AudioViewModel = koinViewModel()
    // Collect audio state from ViewModel
    val audioUiState by audioViewModel.uiState.collectAsState()

    // Determine if we're in recording mode or playback mode
    val hasExistingAudio = block.uri != null
    val isRecording = audioUiState.isRecording

    // Determine current recording state
    val recordingState =
        when {
            isRecording -> RecordingState.RECORDING
            else -> RecordingState.INACTIVE
        }

    val audioLevels = audioUiState.audioLevels

    val playbackMetadata =
        remember(block) {
            val subtitle = formatDateLocalized(block.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date)
            AudioPlaybackMetadata(
                title = block.caption.ifBlank { "Audio Recording" },
                subtitle = subtitle,
                noteId = block.id,
            )
        }

    // Function to handle saving a recorded audio file
    val handleSaveRecording = { uri: String ->
        onBlockUpdated(
            block.copy(
                uri = uri,
                duration = audioUiState.duration.inWholeMilliseconds,
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
            if (hasExistingAudio) {
                AudioBlockContent(
                    block = block,
                    isExpanded = true,
                    isPlaying = audioUiState.isPlaying,
                    playbackProgress = audioUiState.playbackProgress,
                    onPlayPauseClicked = {
                        audioViewModel.togglePlayback(block.uri, playbackMetadata)
                    },
                    onSeekPositionChanged = { position ->
                        audioViewModel.seekTo(position)
                    },
                    onDeleteClicked = onDeleteRequested,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (isRecording) {
                ActiveRecordingDisplay(
                    audioLevels = audioLevels,
                    recordingDuration = audioUiState.duration,
                    transcriptionText = audioUiState.transcription,
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
                    onStartRecording = { audioViewModel.startRecording() },
                    onStopRecording = { audioViewModel.stopRecording() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
