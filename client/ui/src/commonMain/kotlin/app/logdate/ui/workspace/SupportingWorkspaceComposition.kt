package app.logdate.ui.workspace

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.ui.foldable.FoldableLayoutInfo

fun resolveSupportingWorkspaceComposition(
    width: Dp,
    height: Dp,
    foldable: FoldableLayoutInfo = FoldableLayoutInfo(),
    origin: DpOffset = DpOffset.Zero,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): WorkspaceComposition {
    val split =
        resolveWorkspaceComposition(
            width,
            height,
            PanelConstraints.Visual,
            PanelConstraints.Browse,
            foldable = foldable,
            origin = origin,
            layoutDirection = layoutDirection,
        )
    val browse = split.browse ?: return split
    if (foldable.hinge?.isSeparating == true) return split
    // A map needs a useful shape and visual dominance, beyond simply fitting two minima.
    if (split.focus.width >= browse.width * 1.6f && split.focus.width >= split.focus.height * .85f) return split
    return resolveWorkspaceComposition(
        width,
        height,
        PanelConstraints.Visual,
        layoutDirection = layoutDirection,
    )
}
