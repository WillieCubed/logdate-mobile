package app.logdate.feature.timeline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

val DOT_SIZE = 16.dp

@Composable
internal fun TimelineLine(
    modifier: Modifier = Modifier,
    showLine: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(DOT_SIZE)
            .heightIn(min = DOT_SIZE)
    ) {
        drawCircle(color, radius = size.width / 2)
        if (showLine) {
            drawLine(
                start = Offset(x = (DOT_SIZE / 2).toPx(), y = 0f),
                end = Offset(x = (DOT_SIZE / 2).toPx(), y = size.height),
                strokeWidth = 2.dp.toPx(),
                color = color,
            )
        }
    }
}

@Preview
@Composable
fun TimelineLinePreview() {
    TimelineLine()
}
