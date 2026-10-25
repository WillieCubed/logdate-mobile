package app.logdate.ui.workspace

import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldablePosture
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceNavigationModeTest {
    private fun mode(
        width: Int,
        height: Int,
        posture: FoldablePosture = FoldablePosture.Standard,
    ) = workspaceNavigationMode(width.dp, height.dp, posture)

    @Test fun wideWorkspaceUsesAnExpandedSidebar() {
        assertEquals(WorkspaceNavigationMode.Sidebar, mode(1280, 800))
    }

    @Test fun sidebarStartsAtTwelveHundredDp() {
        assertEquals(WorkspaceNavigationMode.Rail, mode(1199, 800))
        assertEquals(WorkspaceNavigationMode.Sidebar, mode(1200, 800))
    }

    @Test fun shortDesktopWindowKeepsTheSidebar() {
        assertEquals(WorkspaceNavigationMode.Sidebar, mode(1280, 400))
    }

    @Test fun portraitTabletKeepsTheCompactRail() {
        assertEquals(WorkspaceNavigationMode.Rail, mode(840, 1180))
    }

    @Test fun portraitPhoneStacksIconsAboveLabels() {
        assertEquals(WorkspaceNavigationMode.ShortBar, mode(411, 900))
    }

    @Test fun railStartsAtSixHundredDp() {
        assertEquals(WorkspaceNavigationMode.ShortBar, mode(599, 900))
        assertEquals(WorkspaceNavigationMode.Rail, mode(600, 900))
    }

    @Test fun landscapePhonePutsIconsBesideLabels() {
        assertEquals(WorkspaceNavigationMode.ShortBarHorizontal, mode(915, 412))
    }

    @Test fun compactHeightEndsAtFourHundredEightyDp() {
        assertEquals(WorkspaceNavigationMode.ShortBarHorizontal, mode(900, 479))
        assertEquals(WorkspaceNavigationMode.Rail, mode(900, 480))
    }

    @Test fun narrowAndShortWindowStacksIconsAboveLabels() {
        assertEquals(WorkspaceNavigationMode.ShortBar, mode(500, 170))
    }

    @Test fun tabletopKeepsTheShortBar() {
        assertEquals(WorkspaceNavigationMode.ShortBar, mode(1280, 600, FoldablePosture.Tabletop))
    }

    @Test fun bookPostureKeepsTheRail() {
        assertEquals(WorkspaceNavigationMode.Rail, mode(1280, 900, FoldablePosture.Book))
        assertEquals(WorkspaceNavigationMode.Rail, mode(900, 412, FoldablePosture.Book))
    }
}
