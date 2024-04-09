package app.logdate.feature.onboarding.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.ui.theme.Spacing
import java.util.concurrent.TimeUnit

data class AudioPreviewData(
    val currentText: String,
    val isPlaying: Boolean,
    val canUseAudio: Boolean,
    val currentDuration: Long,
) {
    companion object {
        val Empty = AudioPreviewData(
            currentText = "",
            isPlaying = false,
            canUseAudio = false,
            currentDuration = 0L,
        )
    }
}

@Composable
fun LiveAudioPreview(
    audioPreviewData: AudioPreviewData,
    modifier: Modifier = Modifier,
    showWaveform: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (audioPreviewData.canUseAudio) {
            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    text = audioPreviewData.currentText,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            if (showWaveform) {
                WaveformBlock(
                    currentDuration = audioPreviewData.currentDuration,
                    isPlaying = audioPreviewData.isPlaying,
                    modifier = Modifier.fillMaxWidth(1f),
                )
            }
        } else {
            Text("Audio permission not granted")
        }
    }
}

/**
 * Renders an audio waveform.
 */
@Composable
private fun WaveformBlock(
    currentDuration: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.md),
        ) {
            // Show a recording indicator if the audio is playing
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    Spacing.sm, Alignment.CenterHorizontally
                ),
            ) {
                Text(
                    text = if (isPlaying) "Playing" else "",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = currentDuration.toFormattedDuration(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(
                horizontalArrangement = Arrangement.Start,
            ) {
                // The actual waveform
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LiveAudioPreviewPreview() {
    LiveAudioPreview(
        audioPreviewData = AudioPreviewData.Empty.copy(
            currentText = "Public speaking sucks so much, you know? I had to give a presentation today on the ethics of AI, and it’s already bad enough this was a group project, but of course one of our team members just didn’t ",
            canUseAudio = true,
        ),
    )
}

/**
 * Returns a formatted duration string from a long value.
 *
 * This string is formatted as `hh:mm:ss`. If the duration is less than an hour, the hours part is
 * omitted.
 */
private fun Long.toFormattedDuration(): String {
    // TODO: Migrate this to kotlinx datetime
    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
