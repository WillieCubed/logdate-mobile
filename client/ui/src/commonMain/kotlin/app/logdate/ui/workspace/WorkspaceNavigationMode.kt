package app.logdate.ui.workspace

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import app.logdate.ui.foldable.FoldablePosture

enum class WorkspaceNavigationMode {
    /** Short bar with icons above labels, for narrow windows. */
    ShortBar,

    /** Short bar with icons beside labels, for landscape phones and tabletop posture. */
    ShortBarHorizontal,
    Rail,
    Sidebar,
    ;

    val isBar: Boolean get() = this == ShortBar || this == ShortBarHorizontal
}

private val ExpandedRailWidth = 1200.dp

/**
 * Material picks the bar or the collapsed rail from the space the workspace has; only the expanded
 * rail on wide windows is ours, because the library never expands it on its own.
 */
fun workspaceNavigationMode(
    width: Dp,
    height: Dp,
    posture: FoldablePosture,
): WorkspaceNavigationMode {
    if (width >= ExpandedRailWidth && posture == FoldablePosture.Standard) return WorkspaceNavigationMode.Sidebar
    val space =
        WindowAdaptiveInfo(
            WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(width.value, height.value),
            Posture(isTabletop = posture == FoldablePosture.Tabletop),
        )
    return when (NavigationSuiteScaffoldDefaults.navigationSuiteType(space)) {
        NavigationSuiteType.ShortNavigationBarCompact -> WorkspaceNavigationMode.ShortBar
        NavigationSuiteType.ShortNavigationBarMedium -> WorkspaceNavigationMode.ShortBarHorizontal
        else -> WorkspaceNavigationMode.Rail
    }
}
