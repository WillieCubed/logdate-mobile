package app.logdate.ui.workspace

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldablePosture

enum class WorkspaceNavigationMode { Bottom, Rail, Sidebar }

fun workspaceNavigationMode(
    width: Dp,
    posture: FoldablePosture,
): WorkspaceNavigationMode =
    when {
        width < 600.dp || posture == FoldablePosture.Tabletop -> WorkspaceNavigationMode.Bottom
        width >= 1200.dp && posture == FoldablePosture.Standard -> WorkspaceNavigationMode.Sidebar
        else -> WorkspaceNavigationMode.Rail
    }
