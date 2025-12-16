package app.logdate.wear.presentation.audio

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.SwipeToDismissBox
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material.icons.Icons
import androidx.wear.compose.material.icons.filled.Close
import androidx.wear.compose.material.icons.filled.Pause
import androidx.wear.compose.material.icons.filled.PlayArrow
import app.logdate.feature.editor.ui.audio.AudioViewModel
import app.logdate.wear.presentation.audio.components.AudioWaveform
import app.logdate.wear.presentation.audio.components.RecordButton
import app.logdate.wear.presentation.audio.components.RecordingTimer
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * Main screen for audio recording on Wear OS.
 * Reuses the AudioViewModel from the mobile app but with a simplified UI optimized for watches.
 */
@Composable
fun AudioRecordingScreen(
    viewModel: AudioViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val swipeToDismissBoxState = rememberSwipeToDismissBoxState()
    
    // Handle swipe to dismiss
    LaunchedEffect(swipeToDismissBoxState.currentValue) {
        if (swipeToDismissBoxState.currentValue == androidx.wear.compose.foundation.SwipeToDismissValue.Dismissed) {
            if (uiState.isRecording) {
                viewModel.stopRecording()
            }
            onNavigateBack()
        }
    }
    
    // Detect successful recording completion
    val recordedAudioUri = uiState.recordedAudioUri
    LaunchedEffect(recordedAudioUri) {
        if (recordedAudioUri != null && !uiState.isRecording) {
            // Short delay to allow user to see completion state
            kotlinx.coroutines.delay(500)
            onNavigateBack()
        }
    }
    
    SwipeToDismissBox(
        state = swipeToDismissBoxState,
        backgroundKey = uiState,
        modifier = Modifier.fillMaxSize()
    ) {
        ScreenScaffold(
            timeText = { 
                // Show compact TimeText to save space
                TimeText(timeTextStyle = TimeTextDefaults.timeTextStyle(fontSize = MaterialTheme.typography.labelMedium.fontSize)) 
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp), // Reduced padding for small screens
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Error message
                    if (uiState.error != null) {
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.labelSmall, // Smaller text for Wear OS
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    
                    // Audio waveform visualization
                    if (uiState.isRecording) {
                        AudioWaveform(
                            audioLevels = uiState.audioLevels,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp) // Reduced height for small screens
                                .padding(bottom = 4.dp)
                        )
                    }
                    
                    // Recording timer
                    if (uiState.isRecording) {
                        RecordingTimer(
                            durationMs = uiState.duration.inWholeMilliseconds,
                            isRecording = uiState.isRecording,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    // Record/Stop button - large touch target
                    RecordButton(
                        isRecording = uiState.isRecording,
                        onClick = {
                            if (uiState.isRecording) {
                                viewModel.stopRecording()
                            } else {
                                viewModel.startRecording()
                            }
                        },
                        modifier = Modifier.size(64.dp)
                    )
                    
                    // Secondary controls
                    if (uiState.isRecording) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Pause/Resume button
                            Button(
                                onClick = { viewModel.toggleRecordingPause() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPaused) 
                                        Icons.Filled.PlayArrow 
                                    else 
                                        Icons.Filled.Pause,
                                    contentDescription = if (uiState.isPaused) "Resume" else "Pause"
                                )
                            }
                            
                            // Cancel button
                            Button(
                                onClick = {
                                    viewModel.stopRecording()
                                    onNavigateBack()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cancel"
                                )
                            }
                        }
                    }
                }
                
                // Loading indicator (for storage check or initialization)
                if (!uiState.isRecording && uiState.recordedAudioUri == null && uiState.error == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}