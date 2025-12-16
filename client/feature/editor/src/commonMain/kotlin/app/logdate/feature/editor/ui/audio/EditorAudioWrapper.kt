package app.logdate.feature.editor.ui.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.state.EditorRecorderState
import org.koin.compose.viewmodel.koinViewModel

/**
 * A wrapper component that connects the EditorAudioState with the AudioEditorContent
 * without directly passing the ViewModel to child components. This follows the pattern 
 * of using callbacks instead of directly passing ViewModels.
 * 
 * @param audioState The specialized audio state containing callbacks
 * @param modifier Optional modifier for customization
 */
@Composable
fun EditorAudioWrapper(
    audioState: EditorRecorderState,
    modifier: Modifier = Modifier
) {
    // Use Koin to inject the AudioViewModel here, but don't pass it to children
    val audioViewModel = koinViewModel<AudioViewModel>()
    val viewModelState by audioViewModel.uiState.collectAsState()
    
    // Wire up the callbacks from EditorAudioState to the actual AudioEditorContent
    AudioEditorContent(
        modifier = modifier,
        // We don't pass the viewModel directly to the AudioEditorContent,
        // but instead wire the callbacks from our state to the AudioEditorContent
        viewModel = audioViewModel, // This is ok since we're in a wrapper that exists specifically to isolate the ViewModel
        onSaveRecording = { uri ->
            // Call both callbacks - the one for recording saved and the one for creating an audio block
            audioState.onAudioRecordingSaved(uri)
            audioState.onCreateAudioBlock(uri)
        },
        onRecordingStarted = {
            audioState.onAudioRecordingStarted()
        },
        onRecordingStopped = {
            audioState.onAudioRecordingStopped()
        }
    )
}