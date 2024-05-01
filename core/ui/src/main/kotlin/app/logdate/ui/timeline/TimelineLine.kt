package app.logdate.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val DOT_SIZE = 16.dp

@Composable
fun TimelineLine(
    modifier: Modifier = Modifier,
    showLine: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .width(DOT_SIZE)
            .fillMaxHeight()
    ) {
        val halfWidth = size.width / 2
        drawCircle(
            center = Offset(x = halfWidth, y = halfWidth),
            color = color,
            radius = halfWidth,
        )
        if (showLine) {
            drawLine(
                start = Offset(x = halfWidth, y = (DOT_SIZE + 8.dp).toPx()),
                // TODO: Fix this obvious hack of a solution and fix measured height
                end = Offset(x = halfWidth, y = size.height),
                strokeWidth = 2.dp.toPx(),
                color = color,
            )
        }
    }
}

@Preview
@Composable
private fun TimelineLinePreview() {
    TimelineLine()
}
