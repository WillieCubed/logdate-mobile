package app.logdate.feature.rewind.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal data class RewindCoverLayout(
    val width: Dp,
    val height: Dp,
    val framing: Dp,
)

internal fun rewindCoverLayout(
    width: Dp,
    height: Dp,
): RewindCoverLayout {
    val coverWidth = (width - 48.dp).coerceIn(1.dp, 520.dp)
    val coverHeight = (coverWidth * 1.4f).coerceAtMost(height * .74f).coerceAtLeast(320.dp)
    return RewindCoverLayout(coverWidth, coverHeight, ((height - coverHeight) / 2).coerceAtLeast(16.dp))
}

internal fun rewindCoverElevation(
    distanceFromCenter: Float,
    reduceMotion: Boolean,
): Dp {
    val distance = if (reduceMotion) 0f else abs(distanceFromCenter).coerceIn(0f, 1f)
    return (2f - distance).dp
}
