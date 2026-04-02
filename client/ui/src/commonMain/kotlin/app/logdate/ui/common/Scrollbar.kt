package app.logdate.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a vertical scrollbar alongside scrollable content.
 *
 * The scrollbar fades in when scrolling and fades out after scrolling stops.
 * Styled to match Material 3 guidelines.
 */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f),
): Modifier =
    composed {
        val targetAlpha = if (state.isScrollInProgress) 1f else 0f
        val alpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(durationMillis = if (targetAlpha > 0f) 150 else 500),
            label = "scrollbar-alpha",
        )

        drawWithContent {
            drawContent()

            val totalItems = state.layoutInfo.totalItemsCount
            val visibleItems = state.layoutInfo.visibleItemsInfo.size
            if (totalItems <= 0 || visibleItems >= totalItems || alpha <= 0f) return@drawWithContent

            val scrollFraction = state.firstVisibleItemIndex.toFloat() / (totalItems - visibleItems)
            val thumbHeight = (visibleItems.toFloat() / totalItems) * size.height
            val thumbY = scrollFraction * (size.height - thumbHeight)

            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha),
                topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), thumbY),
                size = Size(width.toPx(), thumbHeight.coerceAtLeast(24.dp.toPx())),
                cornerRadius = CornerRadius(width.toPx() / 2f),
            )
        }
    }

/**
 * Draws a vertical scrollbar for a [LazyGridState].
 */
fun Modifier.verticalScrollbar(
    state: LazyGridState,
    width: Dp = 4.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f),
): Modifier =
    composed {
        val targetAlpha = if (state.isScrollInProgress) 1f else 0f
        val alpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(durationMillis = if (targetAlpha > 0f) 150 else 500),
            label = "scrollbar-alpha",
        )

        drawWithContent {
            drawContent()

            val totalItems = state.layoutInfo.totalItemsCount
            val visibleItems = state.layoutInfo.visibleItemsInfo.size
            if (totalItems <= 0 || visibleItems >= totalItems || alpha <= 0f) return@drawWithContent

            val scrollFraction = state.firstVisibleItemIndex.toFloat() / (totalItems - visibleItems)
            val thumbHeight = (visibleItems.toFloat() / totalItems) * size.height
            val thumbY = scrollFraction * (size.height - thumbHeight)

            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha),
                topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), thumbY),
                size = Size(width.toPx(), thumbHeight.coerceAtLeast(24.dp.toPx())),
                cornerRadius = CornerRadius(width.toPx() / 2f),
            )
        }
    }
