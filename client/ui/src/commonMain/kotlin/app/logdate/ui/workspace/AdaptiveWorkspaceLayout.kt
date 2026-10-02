@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.foldable.rememberWindowOrigin

/** Owns composition; content responds only to its panel's measured bounds. */
@Composable
fun AdaptiveWorkspaceLayout(
    modifier: Modifier = Modifier,
    focusConstraints: PanelConstraints = PanelConstraints.Reading,
    browseConstraints: PanelConstraints = PanelConstraints.Browse,
    inspectorConstraints: PanelConstraints = PanelConstraints.Inspector,
    browseOnStart: Boolean = false,
    browse: (@Composable () -> Unit)? = null,
    inspector: (@Composable () -> Unit)? = null,
    focus: @Composable () -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val foldable = rememberFoldableLayoutInfo()
    val (origin, originModifier) = rememberWindowOrigin()
    BoxWithConstraints(modifier.then(originModifier), contentAlignment = AbsoluteAlignment.TopLeft) {
        val composition =
            resolveWorkspaceComposition(
                maxWidth,
                maxHeight,
                focusConstraints,
                browseConstraints.takeIf { browse != null },
                inspectorConstraints.takeIf { inspector != null },
                foldable,
                origin,
                browseOnStart,
                direction,
            )
        val placement =
            if (composition.focus.x == DpOffset.Zero.x && composition.browse == null && composition.inspector == null) {
                PanelPlacement.EdgeAttached
            } else {
                PanelPlacement.Floating
            }
        PositionedPanel(composition.focus, PanelRole.Focus, placement, focus)
        composition.browse?.let { bounds -> PositionedPanel(bounds, PanelRole.Browse, PanelPlacement.Floating, browse!!) }
        composition.inspector?.let { bounds -> PositionedPanel(bounds, PanelRole.Inspector, PanelPlacement.Floating, inspector!!) }
    }
}

@Composable
private fun PositionedPanel(
    bounds: PanelBounds,
    role: PanelRole,
    placement: PanelPlacement,
    content: @Composable () -> Unit,
) {
    Box(Modifier.absoluteOffset { IntOffset(bounds.x.roundToPx(), bounds.y.roundToPx()) }.size(bounds.width, bounds.height)) {
        CompositionLocalProvider(LocalPanelLayoutInfo provides PanelLayoutInfo(role, placement, bounds.width, bounds.height)) {
            content()
        }
    }
}
