@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.foldable.rememberWindowOrigin

/** System clearance intersecting this scene; backgrounds never apply it. */
val LocalWorkspacePlaybackInsets = staticCompositionLocalOf { WindowInsets(0, 0, 0, 0) }

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
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
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
            val outside =
                with(density) {
                    WindowInsets(
                        left = (origin.x + focusBounds.x).roundToPx().coerceAtLeast(0),
                        top = (origin.y + focusBounds.y).roundToPx().coerceAtLeast(0),
                        right = (window.width - (origin.x + focusBounds.x + focusBounds.width).roundToPx()).coerceAtLeast(0),
                        bottom = (window.height - (origin.y + focusBounds.y + focusBounds.height).roundToPx()).coerceAtLeast(0),
                    )
                }
            CompositionLocalProvider(LocalWorkspacePlaybackInsets provides WindowInsets.safeDrawing.exclude(outside)) {
                focus()
                controls()
            }
        }
    }
}
