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
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Duration

// TODO: Implement live audio entries
//@Composable
//fun LiveAudioPreview(
//    audioPreviewData: AudioPreviewData,
//    modifier: Modifier = Modifier,
//    showWaveform: Boolean = true,
//) {
//    Column(
//        modifier = modifier,
//        verticalArrangement = Arrangement.spacedBy(app.logdate.ui.theme.Spacing.sm),
//    ) {
//        if (audioPreviewData.canUseAudio) {
//            Column(
//                Modifier.weight(1f)
//            ) {
//                Text(
//                    text = audioPreviewData.currentText,
//                    style = MaterialTheme.typography.headlineSmall,
//                )
//            }
//            if (showWaveform) {
//                WaveformBlock(
//                    currentDuration = audioPreviewData.currentDuration,
//                    isPlaying = audioPreviewData.isPlaying,
//                    modifier = Modifier.fillMaxWidth(1f),
//                )
//            }
//        } else {
//            Text("Audio permission not granted")
//        }
//    }
//}

/**
 * Renders an audio waveform.
 */
@Composable
private fun WaveformBlock(
    currentDuration: Duration,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(app.logdate.ui.theme.Spacing.sm),
            modifier = Modifier.padding(app.logdate.ui.theme.Spacing.md),
        ) {
            // Show a recording indicator if the audio is playing
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    app.logdate.ui.theme.Spacing.sm, Alignment.CenterHorizontally
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

@Preview(
//    showBackground = true
)
//@Composable
//private fun LiveAudioPreviewPreview() {
//    LiveAudioPreview(
//        audioPreviewData = AudioPreviewData.Empty.copy(
//            currentText = "Public speaking sucks so much, you know? I had to give a presentation today on the ethics of AI, and it’s already bad enough this was a group project, but of course one of our team members just didn’t ",
//            canUseAudio = true,
//        ),
//    )
//}

/**
 * Returns a formatted duration string from a Duration value.
 *
 * This string is formatted as `hh:mm:ss` for durations of 1 hour or longer,
 * or `mm:ss` for shorter durations.
 *
 * Examples:
 * - 5400000ms -> "1:30:00"
 * - 45000ms -> "00:45"
 *
 * @throws IllegalArgumentException if the duration is negative
 */
fun Duration.toFormattedDuration(): String {
    require(this.inWholeMilliseconds >= 0) {
        "Duration must be non-negative, but was: $this"
    }

    // Convert to total seconds since it's our smallest display unit
    val totalSeconds = this.inWholeSeconds

    // Extract time components using integer arithmetic
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    // The padStart function ensures we have leading zeros where needed
    // We convert each number to string and pad with '0' to required width
    return if (hours > 0) {
        // For durations with hours, we don't pad the hours component
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        // For durations under an hour, we show only minutes and seconds
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}