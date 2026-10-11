package app.logdate.client.sync.v2.link

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.WatchState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests [PhoneLinkDecision], which says per watch whether the phone links it now, waits, or confirms. */
class PhoneLinkDecisionTest {
    private val pair = LinkPlan(LinkMode.PAIR)
    private val linked =
        PhoneWatchLink(
            nodeId = "w",
            displayName = "Watch",
            mode = LinkMode.PAIR,
            linkedAt = 100,
            confirmedAt = 100,
            lastAttemptAt = 100,
            seenWatchDocAt = 100,
        )

    @Test
    fun `a watch with no record is linked now`() {
        assertEquals(PhoneLinkDecision.Exchange, PhoneLinkDecision.decide(null, pair, null, now = 1_000))
    }

    @Test
    fun `a linked watch that agrees is left alone`() {
        assertEquals(PhoneLinkDecision.Wait, PhoneLinkDecision.decide(linked, pair, document(PeerLinkState.PAIRED, at = 200), now = 1_000))
    }

    @Test
    fun `a watch that reports losing its link after the grace is linked again`() {
        val now = 100 + PhoneLinkDecision.RELINK_GRACE_MS
        assertEquals(
            PhoneLinkDecision.Exchange,
            PhoneLinkDecision.decide(linked, pair, document(PeerLinkState.NEEDS_RELINK, at = now), now),
        )
    }

    @Test
    fun `a report that arrives within the grace waits`() {
        assertEquals(PhoneLinkDecision.Wait, PhoneLinkDecision.decide(linked, pair, document(PeerLinkState.UNLINKED, at = 150), now = 200))
    }

    @Test
    fun `a failed watch waits until its next attempt`() {
        val failed = linked.copy(failure = PhoneLinkFailure.WATCH_UNREACHABLE, nextAttemptAt = 5_000)

        assertEquals(PhoneLinkDecision.Wait, PhoneLinkDecision.decide(failed, pair, null, now = 4_999))
        assertEquals(PhoneLinkDecision.Exchange, PhoneLinkDecision.decide(failed, pair, null, now = 5_000))
    }

    @Test
    fun `a new plan links the watch again`() {
        assertEquals(PhoneLinkDecision.Exchange, PhoneLinkDecision.decide(linked, LinkPlan(LinkMode.CLOUD, "account"), null, now = 1_000))
    }

    @Test
    fun `an unconfirmed link is confirmed once the watch reports it`() {
        val unconfirmed = linked.copy(mode = LinkMode.CLOUD, accountId = "account", confirmedAt = null)

        assertEquals(
            PhoneLinkDecision.Confirm,
            PhoneLinkDecision.decide(
                unconfirmed,
                LinkPlan(LinkMode.CLOUD, "account"),
                document(PeerLinkState.LINKED, at = 300),
                now = 1_000,
            ),
        )
    }

    @Test
    fun `a changed plan does not inherit another plans retry wait`() {
        val failed = linked.copy(failure = PhoneLinkFailure.WATCH_UNREACHABLE, nextAttemptAt = 5_000)
        assertEquals(
            PhoneLinkDecision.Exchange,
            PhoneLinkDecision.decide(failed, LinkPlan(LinkMode.CLOUD, "account"), null, now = 1_000),
        )
    }

    @Test
    fun `an unlinked report does not bypass the retry wait`() {
        val failed = linked.copy(failure = PhoneLinkFailure.WATCH_UNREACHABLE, nextAttemptAt = 1_000_000)
        val now = 100 + PhoneLinkDecision.RELINK_GRACE_MS
        assertEquals(
            PhoneLinkDecision.Wait,
            PhoneLinkDecision.decide(failed, pair, document(PeerLinkState.UNLINKED, at = now), now),
        )
    }

    private fun document(
        link: PeerLinkState,
        at: Long,
    ) = StateDocument(role = PeerRole.WATCH, writtenAt = at, watch = WatchState(link = link))
}
