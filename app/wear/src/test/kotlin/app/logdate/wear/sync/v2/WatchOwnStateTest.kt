package app.logdate.wear.sync.v2

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.WatchState
import app.logdate.wear.sync.v2.link.WatchLinkRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests [WatchOwnState]: the watch reports its link, counts and features as one section. */
class WatchOwnStateTest {
    @Test
    fun `no link record is reported as unlinked`() =
        runTest {
            assertEquals(WatchState(), WatchOwnState(links = flowOf(null)).observe().first())
        }

    @Test
    fun `a pairing is reported with the features this build has`() =
        runTest {
            val own = WatchOwnState(links = flowOf(WatchLinkRecord(LinkMode.PAIR, "phone", 1)), features = setOf("prefs.v1"))

            assertEquals(WatchState(link = PeerLinkState.PAIRED, features = setOf("prefs.v1")), own.observe().first())
        }

    @Test
    fun `a stale pairing asks to be linked again`() =
        runTest {
            val own = WatchOwnState(links = flowOf(WatchLinkRecord(LinkMode.PAIR, "phone", 1, stale = true)))

            assertEquals(PeerLinkState.NEEDS_RELINK, own.observe().first().link)
        }

    @Test
    fun `the outbox counts are reported`() =
        runTest {
            val own = WatchOwnState(links = flowOf(null), counts = flowOf(WatchCounts(outbox = 3, deadLetters = 1, freeBytes = 9)))

            assertEquals(WatchState(outboxCount = 3, deadLetterCount = 1, freeBytes = 9), own.observe().first())
        }
}
