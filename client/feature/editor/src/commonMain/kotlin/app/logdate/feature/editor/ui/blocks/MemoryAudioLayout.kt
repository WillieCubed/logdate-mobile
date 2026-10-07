package app.logdate.feature.editor.ui.blocks

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun unfinishedAudioHeight(
    viewportHeight: Dp,
    viewportWidth: Dp,
    footerHeight: Dp = 56.dp,
): Dp {
    val spacing = if (footerHeight > 0.dp) 24.dp else 8.dp
    val height = (viewportHeight - spacing - footerHeight).coerceAtLeast(420.dp)
    return if (viewportWidth >= 600.dp) height.coerceAtMost(640.dp) else height
}
