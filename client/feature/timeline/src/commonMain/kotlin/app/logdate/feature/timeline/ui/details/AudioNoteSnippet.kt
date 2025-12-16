package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.audio.LocalTranscriptionState
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.util.toReadableDateTimeShort
import kotlin.time.Duration.Companion.milliseconds

/**
 * Displays an audio note in the timeline with enhanced playback controls and transcription.
 * Uses the app-wide audio playback provider to ensure only one audio can play at a time.
 */
@Composable
fun AudioNoteSnippet(
    uiState: AudioNoteUiState,
    modifier: Modifier = Modifier
) {
    // Get the global audio playback state
    val audioPlaybackState = LocalAudioPlaybackState.current
    
    // Get the transcription state
    val transcriptionState = LocalTranscriptionState.current
    
    // Check if this specific note is currently playing
    val isThisPlaying = remember(audioPlaybackState.currentlyPlayingId, audioPlaybackState.isPlaying) {
        audioPlaybackState.currentlyPlayingId == uiState.noteId && audioPlaybackState.isPlaying
    }
    
    // Check if this note is the current one (even if paused)
    val isThisCurrent = remember(audioPlaybackState.currentlyPlayingId) {
        audioPlaybackState.currentlyPlayingId == uiState.noteId
    }
    
    // Create a duration from the milliseconds
    val duration = remember(uiState.duration) {
        uiState.duration.milliseconds
    }
    
    Column(
        modifier = modifier.padding(vertical = Spacing.xs),
    ) {
        // Date/time displayed above the card
        Text(
            text = uiState.timestamp.toReadableDateTimeShort(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isThisPlaying) {
                        audioPlaybackState.pause()
                    } else {
                        audioPlaybackState.play(uiState.noteId, uiState.uri)
                    }
                },
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
            ) {
                // Audio note header with controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Play/pause button
                    IconButton(
                        onClick = { 
                            if (isThisPlaying) {
                                audioPlaybackState.pause()
                            } else {
                                audioPlaybackState.play(uiState.noteId, uiState.uri)
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isThisPlaying) "Pause" else "Play",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Audio note title
                        Text(
                            text = "Voice Note",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        
                        // Duration and status
                        Text(
                            text = if (isThisPlaying) {
                                "Playing • ${duration.inWholeMinutes}:${(duration.inWholeSeconds % 60).toString().padStart(2, '0')}"
                            } else {
                                "${duration.inWholeMinutes}:${(duration.inWholeSeconds % 60).toString().padStart(2, '0')}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Stop button (only show if this audio is current)
                    if (isThisCurrent) {
                        IconButton(
                            onClick = { audioPlaybackState.stop() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // Progress bar and seeking (only show if this audio is current)
                if (isThisCurrent) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm)
                    ) {
                        // Seekable progress bar
                        Slider(
                            value = audioPlaybackState.progress,
                            onValueChange = { newProgress ->
                                audioPlaybackState.seekTo(newProgress)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Time labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val currentTime = (audioPlaybackState.progress * duration.inWholeSeconds).toInt()
                            Text(
                                text = "${currentTime / 60}:${(currentTime % 60).toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${duration.inWholeMinutes}:${(duration.inWholeSeconds % 60).toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Transcription section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md)
                ) {
                    // Get the necessary info for this note from the transcription state
                    val transcriptionText = transcriptionState.getTranscriptionText(uiState.noteId)
                    val isTranscriptionInProgress = transcriptionState.isTranscriptionInProgress(uiState.noteId)
                    val transcriptionError = transcriptionState.getTranscriptionError(uiState.noteId)
                        
                        when {
                            // Transcription is in progress
                            isTranscriptionInProgress -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Converting to text...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // Transcription completed successfully
                            transcriptionText != null -> {
                                Text(
                                    text = "Transcript:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = Spacing.xs)
                                )
                                
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = transcriptionText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(Spacing.md)
                                    )
                                }
                            }
                            
                            // Transcription failed
                            transcriptionError != null -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Text(
                                        text = "Transcription failed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    
                                    Button(
                                        onClick = { transcriptionState.requestTranscription(uiState.noteId) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                            
                            // No transcription exists yet
                            else -> {
                                Button(
                                    onClick = { transcriptionState.requestTranscription(uiState.noteId) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Convert to Text")
                                }
                            }
                        }
                    }
            }
        }
    }
}