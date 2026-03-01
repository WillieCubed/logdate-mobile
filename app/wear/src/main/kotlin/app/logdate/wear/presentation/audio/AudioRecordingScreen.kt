@file:Suppress("DEPRECATION")

package app.logdate.wear.presentation.audio

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.SwipeToDismissValue
import androidx.wear.compose.material.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.*
import app.logdate.wear.R
import app.logdate.wear.presentation.audio.components.AudioWaveform
import app.logdate.wear.presentation.audio.components.RecordButton
import app.logdate.wear.presentation.audio.components.RecordingTimer
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.res.stringResource

/**
 * Main screen for audio recording on Wear OS.
 * Reuses the AudioViewModel from the mobile app but with a simplified UI optimized for watches.
 */
@Composable
fun AudioRecordingScreen(
    viewModel: AudioRecordingViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val swipeToDismissBoxState = rememberSwipeToDismissBoxState()
    
    // Handle swipe to dismiss
    LaunchedEffect(swipeToDismissBoxState.currentValue) {
        if (swipeToDismissBoxState.currentValue == SwipeToDismissValue.Dismissed) {
            if (uiState.isRecording) {
                viewModel.stopRecording()
            }
            onNavigateBack()
        }
    }
    
    // Detect successful recording completion
    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            delay(300)
            onNavigateBack()
        }
    }
    
    SwipeToDismissBox(
        state = swipeToDismissBoxState,
        backgroundKey = uiState,
        modifier = Modifier.fillMaxSize()
    ) {
        ScreenScaffold(
            timeText = { TimeText() }
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
                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage!!,
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
                            durationMs = uiState.durationMs,
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
                                onClick = {
                                    if (uiState.isPaused) {
                                        viewModel.resumeRecording()
                                    } else {
                                        viewModel.pauseRecording()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.size(48.dp)
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
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cancel)
                                )
                            }
                        }
                    }
                }
                
                // Loading indicator (for storage check or initialization)
                if (!uiState.isRecording && uiState.errorMessage == null && !uiState.navigateBack) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        colors = ProgressIndicatorDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }
            }
        }
    }
}
