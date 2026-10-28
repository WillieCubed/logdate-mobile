@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import app.logdate.ui.workspace.LocalWorkspacePlaybackInsets

/** Restore the host icon appearance when playback leaves the scene. */
@Composable
internal expect fun RewindSystemBars()

/** Contrast only where system UI intersects the scene, with no scene padding or gradient. */
@Composable
internal fun RewindSystemBarScrims() {
    val insets = LocalWorkspacePlaybackInsets.current.asPaddingValues()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val top = with(density) { insets.calculateTopPadding().toPx() }
    val bottom = with(density) { insets.calculateBottomPadding().toPx() }
    val left = with(density) { insets.calculateLeftPadding(direction).toPx() }
    val right = with(density) { insets.calculateRightPadding(direction).toPx() }
    Canvas(Modifier.fillMaxSize()) {
        val tint = Color.Black.copy(alpha = 0.58f)
        drawRect(tint, size = Size(size.width, top))
        drawRect(tint, topLeft = Offset(0f, size.height - bottom), size = Size(size.width, bottom))
        drawRect(tint, size = Size(left, size.height))
        drawRect(tint, topLeft = Offset(size.width - right, 0f), size = Size(right, size.height))
    }
}
