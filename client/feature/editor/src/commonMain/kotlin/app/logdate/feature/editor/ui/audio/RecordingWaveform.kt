package app.logdate.feature.editor.ui.audio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun RecordingWaveform(
    levels: List<Float>,
    isRecording: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollOffset = remember { Animatable(0f) }
    LaunchedEffect(levels, isRecording) {
        if (!isRecording || levels.isEmpty()) {
            scrollOffset.snapTo(0f)
        } else {
            scrollOffset.snapTo(1f)
            scrollOffset.animateTo(0f, tween(durationMillis = 100, easing = LinearEasing))
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.clipToBounds().semantics { contentDescription = "Audio waveform" }) {
        val pitch = 10.dp.toPx()
        val stroke = (if (focused) 4.dp else 3.dp).toPx()
        val visible = recordingWaveformWindow(levels, (size.width / pitch).toInt() + 1)
        visible.forEachIndexed { index, level ->
            val x = size.width - (visible.size - index - .5f - scrollOffset.value) * pitch
            val height = (level * size.height * .8f).coerceAtLeast(stroke)
            val alpha = .45f + .55f * (index + 1f) / visible.size
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(x, (size.height - height) / 2f),
                end = Offset(x, (size.height + height) / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

internal fun recordingWaveformWindow(
    levels: List<Float>,
    capacity: Int,
): List<Float> = levels.takeLast(capacity.coerceAtLeast(0)).map { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
