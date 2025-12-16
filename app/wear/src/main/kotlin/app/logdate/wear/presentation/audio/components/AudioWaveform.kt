package app.logdate.wear.presentation.audio.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme

/**
 * Audio waveform visualization optimized for Wear OS.
 * Displays audio levels as vertical bars with a simplified design suitable for small screens.
 */
@Composable
fun AudioWaveform(
    audioLevels: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    barWidth: Float = 3f
) {
    // For very small screens, we use a simpler representation with fewer bars
    val levels = if (audioLevels.isEmpty()) {
        listOf(0f)
    } else {
        audioLevels
    }
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        // For a single audio level (most common case), create a symmetrical waveform
        if (levels.size == 1) {
            val level = levels[0]
            // Animate the level for smooth transitions
            val animatedLevel by animateFloatAsState(
                targetValue = level,
                animationSpec = tween(durationMillis = 100),
                label = "audio level"
            )
            
            // Number of bars in the waveform - adapt based on screen width
            val barCount = (width / (barWidth * 2)).toInt().coerceAtMost(15)
            
            // Create a simple waveform with a center peak
            for (i in 0 until barCount) {
                // Calculate bar height based on position (center bars higher)
                val distanceFromCenter = kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                val barLevel = animatedLevel * (1f - distanceFromCenter * 0.8f)
                
                // Calculate x position
                val x = width * i / barCount + barWidth
                
                // Draw vertical line with varying height
                drawLine(
                    color = barColor,
                    start = Offset(x, centerY + height * 0.4f * barLevel),
                    end = Offset(x, centerY - height * 0.4f * barLevel),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }
        } else {
            // If we have multiple levels, draw them directly (more complex visualization)
            val barSpacing = width / levels.size.coerceAtLeast(1)
            
            levels.forEachIndexed { index, level ->
                val animatedLevel by animateFloatAsState(
                    targetValue = level,
                    animationSpec = tween(durationMillis = 100),
                    label = "audio level $index"
                )
                
                val x = barSpacing * (index + 0.5f)
                
                drawLine(
                    color = barColor,
                    start = Offset(x, centerY + height * 0.4f * animatedLevel),
                    end = Offset(x, centerY - height * 0.4f * animatedLevel),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}