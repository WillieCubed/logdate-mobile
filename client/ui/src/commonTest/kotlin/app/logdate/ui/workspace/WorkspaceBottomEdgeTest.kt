package app.logdate.ui.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceBottomEdgeTest {
    private fun WorkspaceBottomEdge.scroll(
        consumed: Float,
        available: Float,
    ) {
        connection.onPostScroll(Offset(0f, consumed), Offset(0f, available), NestedScrollSource.UserInput)
    }

    @Test fun startsAwayFromTheBottom() {
        assertFalse(WorkspaceBottomEdge().reached)
    }

    @Test fun reachesTheBottomWhenForwardScrollHasNothingLeftToConsume() {
        val edge = WorkspaceBottomEdge()
        edge.scroll(consumed = -40f, available = 0f)
        assertFalse(edge.reached)
        edge.scroll(consumed = -12f, available = -28f)
        assertTrue(edge.reached)
    }

    @Test fun leavesTheBottomWhenScrollingBack() {
        val edge = WorkspaceBottomEdge(initiallyReached = true)
        edge.scroll(consumed = 18f, available = 0f)
        assertFalse(edge.reached)
    }

    @Test fun ignoresHorizontalScrolling() {
        val edge = WorkspaceBottomEdge(initiallyReached = true)
        edge.connection.onPostScroll(Offset(30f, 0f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(edge.reached)
    }

    @Test fun staysAtTheBottomWhenFurtherForwardScrollIsRefused() {
        val edge = WorkspaceBottomEdge(initiallyReached = true)
        edge.scroll(consumed = 0f, available = -30f)
        assertTrue(edge.reached)
    }
}
