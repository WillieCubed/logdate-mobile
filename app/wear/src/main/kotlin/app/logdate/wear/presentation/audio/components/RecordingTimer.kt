package app.logdate.wear.presentation.audio.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Recording timer display optimized for Wear OS.
 * Shows elapsed time with a pulsing record indicator.
 */
@Composable
fun RecordingTimer(
    durationMs: Long,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    // Format time as MM:SS
    val minutes = (durationMs / 60000).toInt()
    val seconds = ((durationMs % 60000) / 1000).toInt()
    val timeText = String.format("%02d:%02d", minutes, seconds)
    
    // Pulsing animation for record indicator
    var pulsate by remember { mutableStateOf(true) }
    val alpha by animateFloatAsState(
        targetValue = if (pulsate) 1f else 0.3f,
        animationSpec = tween(durationMillis = 500),
        label = "pulse animation"
    )
    
    // Animate indicator color based on recording state
    val color by animateColorAsState(
        targetValue = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
        label = "indicator color"
    )
    
    // Pulsing effect when recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                pulsate = !pulsate
                delay(500.milliseconds)
            }
        } else {
            pulsate = true
        }
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRecording) {
            // Show pulsing record icon when recording
            Icon(
                imageVector = Icons.Filled.FiberManualRecord,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(12.dp)
                    .alpha(alpha)
                    .padding(end = 4.dp)
            )
        }
        
        // Time display
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}