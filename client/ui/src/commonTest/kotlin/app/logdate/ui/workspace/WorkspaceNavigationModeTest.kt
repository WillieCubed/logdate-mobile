package app.logdate.ui.workspace

import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldablePosture
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceNavigationModeTest {
    @Test fun wideWorkspaceUsesAnExpandedSidebar() {
        assertEquals(WorkspaceNavigationMode.Sidebar, workspaceNavigationMode(1280.dp, FoldablePosture.Standard))
    }

    @Test fun portraitTabletKeepsTheCompactRail() {
        assertEquals(WorkspaceNavigationMode.Rail, workspaceNavigationMode(840.dp, FoldablePosture.Standard))
    }

    @Test fun phoneAndTabletopKeepBottomNavigation() {
        assertEquals(WorkspaceNavigationMode.Bottom, workspaceNavigationMode(411.dp, FoldablePosture.Standard))
        assertEquals(WorkspaceNavigationMode.Bottom, workspaceNavigationMode(1280.dp, FoldablePosture.Tabletop))
    }
}
