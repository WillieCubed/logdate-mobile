package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.RecordingState
import io.github.aakira.napier.Napier
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
    val recordingState = when {
        isRecording -> RecordingState.RECORDING
        else -> RecordingState.INACTIVE
    }
    
    // Debug logging to see what's happening
    Napier.d("AudioBlockEditor - hasExistingAudio: $hasExistingAudio, isRecording: $isRecording, URI: ${block.uri}, recordingState: $recordingState")
    
    // Convert milliseconds duration to a list format for visualization
    val audioLevels = remember(audioUiState.audioLevels) {
        audioUiState.audioLevels.ifEmpty { 
            List(20) { 0.1f } 
        }
    }
    
    // Function to handle saving a recorded audio file
    val handleSaveRecording = { uri: String ->
        Napier.d("Audio recording saved: $uri")
        onBlockUpdated(block.copy(uri = uri))
    }
    
    // If we get a recording URI from the viewModel, update the block
    if (audioUiState.recordedAudioUri != null && block.uri == null) {
        handleSaveRecording(audioUiState.recordedAudioUri!!)
    }
    
    // Show appropriate audio UI based on the current state
    // Use AudioPermissionWrapper to handle permissions
    AudioPermissionWrapper {
        if (hasExistingAudio) {
            AudioBlockContent(
                block = block,
                isExpanded = true,
                playbackProgress = audioUiState.playbackProgress,
                onPlayPauseClicked = {
                    // Handle play/pause toggle here in the intermediate component
                    if (block.uri != null) {
                        audioViewModel.togglePlayback(block.uri)
                    }
                    onBlockUpdated(block.copy(isPlaying = !block.isPlaying))
                },
                onSeekPositionChanged = { position ->
                    // Handle seek position change here
                    if (block.uri != null) {
                        audioViewModel.seekTo(position)
                    }
                },
                onDeleteClicked = onDeleteRequested,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            AudioRecordingControls(
                recordingState = recordingState,
                audioLevels = audioLevels,
                recordingDuration = audioUiState.duration,
                onStartRecording = {
                    audioViewModel.startRecording()
                },
                onStopRecording = {
                    audioViewModel.stopRecording()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}