package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.github.aakira.napier.Napier
import org.koin.compose.viewmodel.koinViewModel

/**
 * Audio editor content component that follows proper architecture patterns
 * with clear separation of UI and business logic.
 */
@Composable
fun AudioEditorContent(
    // Callback for when audio recording is completed with URI
    onSaveRecording: (String) -> Unit,
    // Modifier for layout customization
    modifier: Modifier = Modifier,
    // ViewModel - injected via Koin
    viewModel: AudioViewModel = koinViewModel(),
    // Optional callbacks
    onRecordingStarted: () -> Unit = {},
    onRecordingStopped: () -> Unit = {}
) {
    // Collect UI state from ViewModel
    val uiState by viewModel.uiState.collectAsState()
    
    // Effect to handle completed recordings
    LaunchedEffect(uiState.isRecording, uiState.recordedAudioUri) {
        // When recording stops and we have a valid URI
        if (!uiState.isRecording && uiState.recordedAudioUri != null) {
            uiState.recordedAudioUri?.let { uri ->
                onSaveRecording(uri)
            }
        }
    }
    
    // Effect to handle errors
    LaunchedEffect(uiState.error) {
        // If there's an error, show it
        if (uiState.error != null) {
            // In a real app, this would show a toast or dialog
            Napier.e("Audio recording error: ${uiState.error}")
        }
    }
    
    // Use platform-specific permission wrapper
    AudioPermissionWrapper {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable {
                    if (uiState.isRecording) {
                        viewModel.stopRecording()
                        onRecordingStopped()
                    } else {
                        viewModel.startRecording()
                        onRecordingStarted()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // When recording or has transcription, show content preview
            if (uiState.isRecording || uiState.transcription?.isNotEmpty() == true) {
                AudioPreviewComponent(
                    previewText = uiState.transcription ?: "Listening...",
                    isRecording = uiState.isRecording,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Show recording visualization when not active
                AudioRecordingDisplay(
                    audioLevels = uiState.audioLevels,
                    recordingDuration = uiState.duration,
                    isRecording = uiState.isRecording,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Instruction text when not recording and no transcription
            if (!uiState.isRecording && uiState.transcription?.isEmpty() != false) {
                Text(
                    text = "Tap to start recording",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}