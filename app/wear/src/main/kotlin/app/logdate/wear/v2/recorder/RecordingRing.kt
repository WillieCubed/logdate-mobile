package app.logdate.wear.v2.recorder

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The input level as short bars around the edge of the screen, oldest at the top, going clockwise. */
@Composable
internal fun RecordingRing(
    levels: List<Float>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.testTag(RecorderTags.RING)) {
        val outer = size.minDimension / 2f - RING_INSET.toPx()
        val longest = RING_BAR_LENGTH.toPx()
        val stroke = RING_STROKE.toPx()
        levels.takeLast(RING_BARS).forEachIndexed { index, level ->
            val angle = index.toFloat() / RING_BARS * FULL_TURN - QUARTER_TURN
            val inner = outer - longest * level.coerceIn(MIN_LEVEL, 1f)
            val direction = Offset(cos(angle), sin(angle))
            drawLine(
                color = color,
                start = center + direction * inner,
                end = center + direction * outer,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val RING_BARS = 50
private const val MIN_LEVEL = 0.08f
private val FULL_TURN = (2 * PI).toFloat()
private val QUARTER_TURN = (PI / 2).toFloat()
private val RING_INSET = 4.dp
private val RING_BAR_LENGTH = 10.dp
private val RING_STROKE = 2.dp
