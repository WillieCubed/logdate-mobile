package app.logdate.client.sync.v2.link

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.StateDocument

/** What the phone does next about one watch. */
sealed interface PhoneLinkDecision {
    data object Wait : PhoneLinkDecision

    data object Exchange : PhoneLinkDecision

    /** The watch reports the link the phone made; the phone shows its one confirmation. */
    data object Confirm : PhoneLinkDecision

    companion object {
        /** A report of no link written this soon after an exchange is the watch's state from before it. */
        const val RELINK_GRACE_MS = 2 * 60_000L

        private val RELINK_STATES = setOf(PeerLinkState.UNLINKED, PeerLinkState.NEEDS_RELINK)

        fun decide(
            record: PhoneWatchLink?,
            plan: LinkPlan,
            document: StateDocument?,
            now: Long,
        ): PhoneLinkDecision {
            if (record == null) return Exchange
            if (record.mode != plan.mode || record.accountId != plan.accountId) return Exchange
            if (record.failure != null) return if (now >= record.nextAttemptAt) Exchange else Wait
            val watch = document?.watch
            val fresh = document != null && document.writtenAt > record.seenWatchDocAt
            val watchAsks = fresh && watch != null && watch.link in RELINK_STATES && now - record.lastAttemptAt >= RELINK_GRACE_MS
            if (watchAsks) return Exchange
            if (fresh && record.confirmedAt == null && watch?.link == linkedState(record.mode)) return Confirm
            return Wait
        }

        private fun linkedState(mode: LinkMode): PeerLinkState =
            when (mode) {
                LinkMode.PAIR -> PeerLinkState.PAIRED
                LinkMode.CLOUD -> PeerLinkState.LINKED
            }
    }
}
