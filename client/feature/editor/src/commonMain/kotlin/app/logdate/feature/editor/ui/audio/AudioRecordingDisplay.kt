package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import app.logdate.ui.common.MaterialCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Duration

/**
 * A wrapper component that combines the audio waveform visualization and recording time display.
 * This provides a complete audio recording interface with visual feedback.
 */
@Composable
fun AudioRecordingDisplay(
    audioLevels: List<Float>,
    recordingDuration: Duration,
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
    showTimeAboveWaveform: Boolean = true,
) {
    MaterialCard(
        modifier = modifier,
        containerColor = if (isRecording) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        else 
            MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showTimeAboveWaveform) {
                AudioTimeDisplay(
                    recordingDuration = recordingDuration,
                    isRecording = isRecording
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                AudioWaveformComponent(
                    audioLevels = audioLevels,
                    isRecording = isRecording,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                AudioWaveformComponent(
                    audioLevels = audioLevels,
                    isRecording = isRecording,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                AudioTimeDisplay(
                    recordingDuration = recordingDuration,
                    isRecording = isRecording
                )
            }
        }
    }
}

/**
 * Alternative layout that displays the time and waveform side by side.
 */
@Composable
fun AudioRecordingDisplayHorizontal(
    audioLevels: List<Float>,
    recordingDuration: Duration,
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
) {
    MaterialCard(
        modifier = modifier,
        containerColor = if (isRecording) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        else 
            MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AudioTimeDisplay(
                recordingDuration = recordingDuration,
                isRecording = isRecording
            )
            
            AudioWaveformComponent(
                audioLevels = audioLevels,
                isRecording = isRecording,
                modifier = Modifier.weight(1f)
            )
        }
    }
}