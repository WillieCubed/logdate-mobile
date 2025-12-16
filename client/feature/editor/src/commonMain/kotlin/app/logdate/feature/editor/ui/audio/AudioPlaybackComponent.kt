package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.aakira.napier.Napier
import org.koin.compose.koinInject
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


/**
 * A component that displays and controls audio playback.
 * Includes a play/pause button, progress slider, and time display.
 * 
 * This implementation connects to the AudioViewModel to use the platform-specific
 * audio playback implementation instead of simulating playback.
 */
@Composable
fun AudioPlaybackComponent(
    audioUri: String,
    modifier: Modifier = Modifier,
    initiallyPlaying: Boolean = false,
    waveformEnabled: Boolean = true,
    showTranscription: Boolean = false,
    transcriptionText: String? = null,
    viewModel: AudioViewModel = koinInject()
) {
    // Get the current UI state from the view model
    val uiState by viewModel.uiState.collectAsState()
    
    // Create playback state that manages playback
    val playbackState = remember {
        // Create the state with a non-circular reference to the seek operation
        AudioPlaybackState(
            audioUri = audioUri,
            initialIsPlaying = initiallyPlaying,
            initialTotalDuration = Duration.parse("3m0s"),
            onStartPlayback = { viewModel.startPlayback(audioUri) },
            onStopPlayback = { viewModel.pausePlayback() },
            onSeek = { duration, totalDuration ->
                val ratio = if (totalDuration > Duration.ZERO) {
                    (duration.inWholeMilliseconds.toFloat() / 
                     totalDuration.inWholeMilliseconds.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                viewModel.seekTo(ratio)
            }
        )
    }
    
    // Connect to platform-specific implementation
    LaunchedEffect(audioUri) {
        playbackState.updateLoadingState(true)
        
        // We already set the initial duration in the rememberAudioPlaybackState call
        playbackState.updateLoadingState(false)
        
        // Start playback if initially playing
        if (initiallyPlaying) {
            playbackState.startPlayback()
        }
    }
    
    // Observe the view model's playback progress
    LaunchedEffect(uiState) {
        // Update progress state from view model
        if (uiState.currentUri == audioUri) {
            playbackState.updatePlaybackState(uiState.isPlaying)
            if (uiState.playbackProgress > 0f && playbackState.totalDuration > Duration.ZERO) {
                val progressDuration = (uiState.playbackProgress * playbackState.totalDuration.inWholeMilliseconds).toLong()
                val newDuration = Duration.parse(progressDuration.toString() + "ms")
                playbackState.updateProgress(newDuration)
            }
        }
    }
    
    // Handle errors from the view model
    LaunchedEffect(uiState.error) {
        if (!uiState.error.isNullOrBlank()) {
            playbackState.setError(true)
        }
    }
    
    // Clean up on disposal
    DisposableEffect(Unit) {
        onDispose {
            if (playbackState.isPlaying) {
                playbackState.stopPlayback()
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Optional transcription display
        if (showTranscription) {
            when {
                uiState.transcriptionInProgress -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generating transcription...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                transcriptionText != null || uiState.transcription != null -> {
                    Text(
                        text = transcriptionText ?: uiState.transcription ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
        
        // Audio waveform display if enabled
        if (waveformEnabled) {
            // Just a placeholder for now - could be a real waveform visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(bottom = 8.dp)
            ) {
                // Show progress line within waveform
                Slider(
                    value = playbackState.progressPercentage,
                    onValueChange = { playbackState.seekToPercentage(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Playback controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/pause button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .pointerInput(playbackState) {
                        detectTapGestures {
                            playbackState.togglePlayback()
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (playbackState.isLoading || (uiState.currentUri == audioUri && uiState.isPlaying != playbackState.isPlaying)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = if (playbackState.isPlaying) 
                                Icons.Filled.Pause 
                            else 
                                Icons.Filled.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) 
                                "Pause" 
                            else 
                                "Play",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Time display
            Text(
                text = formatDuration(playbackState.progress),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(40.dp)
            )
            
            if (!waveformEnabled) {
                // Only show slider if waveform is disabled
                Slider(
                    value = playbackState.progressPercentage,
                    onValueChange = { playbackState.seekToPercentage(it) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // Total duration
            Text(
                text = formatDuration(playbackState.totalDuration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

/**
 * Formats a Duration into MM:SS format
 */
private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    
    // Format without using String.format
    val minutesStr = if (minutes < 10) "0$minutes" else "$minutes"
    val secondsStr = if (seconds < 10) "0$seconds" else "$seconds"
    
    return "$minutesStr:$secondsStr"
}