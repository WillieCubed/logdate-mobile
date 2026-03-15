package app.logdate.wear.presentation.audio.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * Recording timer display optimized for Wear OS.
 * Shows elapsed time with a pulsing record indicator.
 *
 * Uses [rememberInfiniteTransition] instead of a coroutine loop to pulse the
 * recording dot, avoiding repeated state toggles that trigger parent recompositions.
 */
@Composable
fun RecordingTimer(
    durationMs: Long,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
) {
    val minutes = (durationMs / 60000).toInt()
    val seconds = ((durationMs % 60000) / 1000).toInt()
    val timeText = String.format("%02d:%02d", minutes, seconds)

    val color by animateColorAsState(
        targetValue = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
        label = "indicator color",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRecording) {
            val transition = rememberInfiniteTransition(label = "recording_pulse")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulse_alpha",
            )

            Icon(
                imageVector = Icons.Filled.FiberManualRecord,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(12.dp)
                    .alpha(alpha)
                    .padding(end = 4.dp),
            )
        }

        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
