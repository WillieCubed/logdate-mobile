@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.foldable.rememberWindowOrigin

/** Deliberate immersive playback keeps controls over the focus on phones and beside it when possible. */
@Composable
fun WorkspacePlaybackLayout(
    modifier: Modifier = Modifier,
    focus: @Composable () -> Unit,
    controls: @Composable (Boolean) -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val foldable = rememberFoldableLayoutInfo()
    val (origin, originModifier) = rememberWindowOrigin()
    BoxWithConstraints(modifier.fillMaxSize().then(originModifier), contentAlignment = AbsoluteAlignment.TopLeft) {
        val layout =
            resolveWorkspaceComposition(
                maxWidth,
                maxHeight,
                PanelConstraints.Visual,
                PanelConstraints.Browse,
                foldable = foldable,
                origin = origin,
                layoutDirection = direction,
            )
        val focusBounds = layout.focus
        Box(
            Modifier
                .absoluteOffset {
                    IntOffset(focusBounds.x.roundToPx(), focusBounds.y.roundToPx())
                }.size(focusBounds.width, focusBounds.height),
        ) { focus() }
        val controlsBounds = layout.browse ?: layout.focus
        Box(
            Modifier
                .absoluteOffset {
                    IntOffset(controlsBounds.x.roundToPx(), controlsBounds.y.roundToPx())
                }.size(controlsBounds.width, controlsBounds.height),
        ) {
            controls(layout.browse != null)
        }
    }
}
