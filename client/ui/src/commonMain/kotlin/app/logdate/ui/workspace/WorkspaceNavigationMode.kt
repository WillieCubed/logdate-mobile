package app.logdate.ui.workspace

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldablePosture

enum class WorkspaceNavigationMode {
    /** Short bar with icons above labels, for narrow windows and tabletop posture. */
    ShortBar,

    /** Short bar with icons beside labels, for landscape phones. */
    ShortBarHorizontal,
    Rail,
    Sidebar,
    ;

    val isBar: Boolean get() = this == ShortBar || this == ShortBarHorizontal
}

private val MediumWidth = 600.dp
private val ExpandedWidth = 1200.dp
private val CompactHeight = 480.dp

fun workspaceNavigationMode(
    width: Dp,
    height: Dp,
    posture: FoldablePosture,
): WorkspaceNavigationMode =
    when {
        width < MediumWidth || posture == FoldablePosture.Tabletop -> WorkspaceNavigationMode.ShortBar
        width >= ExpandedWidth && posture == FoldablePosture.Standard -> WorkspaceNavigationMode.Sidebar
        height < CompactHeight && posture == FoldablePosture.Standard -> WorkspaceNavigationMode.ShortBarHorizontal
        else -> WorkspaceNavigationMode.Rail
    }
