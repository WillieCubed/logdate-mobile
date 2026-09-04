package app.logdate.feature.core.account

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `InitialSyncProgressScreen` only showed "N of M synced" while the sync was still [Running] --
 * the moment it flipped to [InitialSyncStatus.TimedOut], the screen dropped that count and fell
 * back to a purely generic message, right when showing how far the run actually got would be
 * most reassuring. These tests cover the message-selection logic in isolation from Compose.
 */
class InitialSyncMessageTest {
    @Test
    fun `running with a known total reports progress`() {
        val message = initialSyncMessageFor(InitialSyncStatus.Running, total = 10, completed = 4)

        assertEquals(InitialSyncMessage.Progress(completed = 4, total = 10), message)
    }

    @Test
    fun `running with no total yet falls back to the indeterminate message`() {
        val message = initialSyncMessageFor(InitialSyncStatus.Running, total = null, completed = 0)

        assertEquals(InitialSyncMessage.Running, message)
    }

    @Test
    fun `timing out with a known total keeps reporting how far it got`() {
        val message = initialSyncMessageFor(InitialSyncStatus.TimedOut, total = 312, completed = 247)

        assertEquals(InitialSyncMessage.TimedOut(completed = 247, total = 312), message)
    }

    @Test
    fun `timing out with no total falls back to the generic message`() {
        val message = initialSyncMessageFor(InitialSyncStatus.TimedOut, total = null, completed = 0)

        assertEquals(InitialSyncMessage.TimedOut(completed = 0, total = null), message)
    }
}
