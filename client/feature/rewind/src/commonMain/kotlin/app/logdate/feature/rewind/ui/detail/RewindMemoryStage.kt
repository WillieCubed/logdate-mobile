@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.workspace.LocalWorkspacePlaybackInsets

/** Full scene backdrop and a separate, centered region for the complete memory object. */
@Composable
fun RewindMemoryStage(
    modifier: Modifier = Modifier,
    content: @Composable (Dp) -> Unit,
) {
    val insets = LocalWorkspacePlaybackInsets.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safeTop = with(density) { insets.getTop(this).toDp() }
    val safeBottom = with(density) { insets.getBottom(this).toDp() }
    val safeLeft = with(density) { insets.getLeft(this, direction).toDp() }
    val safeRight = with(density) { insets.getRight(this, direction).toDp() }
    val top = maxOf(safeTop + 60.dp, LocalRewindChromeHeight.current + 12.dp)
    val bottom = safeBottom + 112.dp
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val regionHeight = minOf(maxHeight * 0.82f, (maxHeight - top - bottom).coerceAtLeast(1.dp))
        val regionWidth = minOf(maxWidth * 0.76f, (maxWidth - safeLeft - safeRight).coerceAtLeast(1.dp))
        Box(
            Modifier.fillMaxSize().absolutePadding(left = safeLeft, right = safeRight, top = top, bottom = bottom),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.widthIn(max = regionWidth).fillMaxWidth().heightIn(max = regionHeight),
                contentAlignment = Alignment.Center,
            ) { content(regionHeight) }
        }
    }
}
