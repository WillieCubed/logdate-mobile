package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.logdate.feature.editor.ui.formatMediaDuration
import kotlin.time.Duration

/**
 * A component that displays the current recording time in MM:SS format.
 * Uses a monospace font for consistent digit alignment.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun AudioTimeDisplay(
    recordingDuration: Duration,
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
) {
    Box(
        modifier = modifier.padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatMediaDuration(recordingDuration.inWholeMilliseconds, true),
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                ),
            color =
                if (isRecording) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}
