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

/** Playback overlays controls on the story, spanning narrow book folds to preserve readable width. */
@Composable
fun WorkspacePlaybackLayout(
    modifier: Modifier = Modifier,
    focus: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val foldable = rememberFoldableLayoutInfo()
    val (origin, originModifier) = rememberWindowOrigin()
    BoxWithConstraints(modifier.fillMaxSize().then(originModifier), contentAlignment = AbsoluteAlignment.TopLeft) {
        val focusBounds =
            resolveWorkspacePlaybackBounds(
                maxWidth,
                maxHeight,
                foldable = foldable,
                origin = origin,
                layoutDirection = direction,
            )
        Box(
            Modifier
                .absoluteOffset {
                    IntOffset(focusBounds.x.roundToPx(), focusBounds.y.roundToPx())
                }.size(focusBounds.width, focusBounds.height),
        ) {
            focus()
            controls()
        }
    }
}
