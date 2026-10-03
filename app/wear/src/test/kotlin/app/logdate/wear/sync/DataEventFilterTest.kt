package app.logdate.wear.sync

import com.google.android.gms.wearable.DataEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [isChangeFromAnotherDevice], which decides which data events the watch acts on.
 */
class DataEventFilterTest {
    @Test
    fun `a change the phone made is acted on`() {
        assertTrue(isChangeFromAnotherDevice(DataEvent.TYPE_CHANGED, sourceNodeId = "phone-node", localNodeId = "watch-node"))
    }

    @Test
    fun `a change the watch made itself is ignored`() {
        assertFalse(isChangeFromAnotherDevice(DataEvent.TYPE_CHANGED, sourceNodeId = "watch-node", localNodeId = "watch-node"))
    }

    @Test
    fun `a deletion is ignored`() {
        assertFalse(isChangeFromAnotherDevice(DataEvent.TYPE_DELETED, sourceNodeId = "phone-node", localNodeId = "watch-node"))
    }

    @Test
    fun `every change is acted on when the watch's own node is unknown`() {
        assertTrue(isChangeFromAnotherDevice(DataEvent.TYPE_CHANGED, sourceNodeId = "watch-node", localNodeId = null))
    }
}
