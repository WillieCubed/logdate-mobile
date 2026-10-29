package app.logdate.ui.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceEntryPullStateTest {
    @Test
    fun belowThresholdReleaseAndArmedReversalCreateNoCommit() {
        val pull = WorkspaceEntryPullState(100f)
        assertEquals(0f, pull.drag(-40f))
        assertTrue(pull.drag(80f) > 0f)
        assertFalse(pull.release())
        assertEquals(0f, pull.distance)
        pull.drag(250f)
        assertTrue(pull.armed)
        pull.drag(-240f)
        assertFalse(pull.release())
        assertEquals(0f, pull.distance)
    }

    @Test
    fun armedReleaseCommitsOnceAndPreservesItsPaintedSourceUntilReturn() {
        val pull = WorkspaceEntryPullState(100f)
        pull.drag(250f)
        val sourceDistance = pull.distance
        assertTrue(pull.release())
        assertEquals(sourceDistance, pull.distance)
        assertFalse(pull.release())
        assertEquals(0f, pull.drag(80f))
        pull.reset()
        pull.drag(250f)
        assertTrue(pull.release())
    }

    @Test
    fun returningHomeKeepsTheCommittedSourceUntilItsLeaseRotates() {
        val pull = WorkspaceEntryPullState(100f)
        pull.bindSource("first")
        pull.drag(250f)
        assertTrue(pull.release())
        val painted = pull.distance
        pull.bindSource("first")
        assertEquals(painted, pull.distance)
        assertFalse(pull.release())
        pull.bindSource("second")
        assertEquals(0f, pull.distance)
        pull.drag(250f)
        assertTrue(pull.release())
    }

    @Test
    fun cancellationAndDisposalCannotCommitAnArmedGesture() {
        val cancelled = WorkspaceEntryPullState(100f)
        cancelled.drag(250f)
        cancelled.abortGesture()
        assertFalse(cancelled.release())
        val disposed = WorkspaceEntryPullState(100f)
        disposed.drag(250f)
        disposed.dispose()
        assertFalse(disposed.release())
        assertEquals(0f, disposed.drag(250f))
    }
}
