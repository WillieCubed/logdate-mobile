package app.logdate.feature.core.settings.ui.watch

import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.WatchState

/**
 * Combines the phone's view of the connection with the watch's own report into a [WatchStatus].
 *
 * The watch's report counts only while version 2 is active, and each part of it only under the
 * feature that makes it meaningful: outbox counts under receipts, the library under the window, and
 * the link under cloud linking. A watch that publishes defaults it does not track yet lists none of
 * these, so none of its defaults reach the screen.
 */
object WatchStatusResolver {
    fun resolve(
        connection: WatchConnectionState,
        peer: WatchPeerSnapshot,
        windowDays: Int,
    ): WatchStatus? =
        when (connection) {
            WatchConnectionState.Loading -> null
            WatchConnectionState.NoPairedWatch -> WatchStatus.NoWatch
            is WatchConnectionState.NeedsAssociation -> WatchStatus.SettingUp(WatchSetupStep.PairWithPhone(connection.watchName))
            WatchConnectionState.AssociationPending -> WatchStatus.SettingUp(WatchSetupStep.ConfirmOnPhone)
            is WatchConnectionState.AppNotInstalled -> WatchStatus.NotInstalled(connection.watchName)
            is WatchConnectionState.Connected -> installed(connection.watchName, nearby = true, peer, windowDays)
            is WatchConnectionState.OutOfRange -> installed(connection.watchName, nearby = false, peer, windowDays)
        }

    private fun installed(
        watchName: String,
        nearby: Boolean,
        peer: WatchPeerSnapshot,
        windowDays: Int,
    ): WatchStatus {
        if (!peer.v2Active) return WatchStatus.Ready(watchName = watchName, nearby = nearby)
        val watch = peer.watch
        val features = peer.features
        issue(watch, features)?.let { return WatchStatus.NeedsAttention(watchName = watchName, nearby = nearby, issue = it) }
        return WatchStatus.Ready(
            watchName = watchName,
            nearby = nearby,
            link = linkLine(watch, features),
            sync = watch?.takeIf { WearFeatures.RECEIPTS in features }?.let { WatchSyncLine(waiting = it.outboxCount) },
            window = WatchWindowLine(days = windowDays).takeIf { WearFeatures.WINDOW in features },
            capabilities = features,
        )
    }

    private fun issue(
        watch: WatchState?,
        features: Set<String>,
    ): WatchIssue? {
        if (watch == null) return null
        if (WearFeatures.LINK_CLOUD in features && watch.link == PeerLinkState.NEEDS_RELINK) return WatchIssue.SetUpAgain
        if (WearFeatures.RECEIPTS in features && watch.deadLetterCount > 0) return WatchIssue.CapturesNotSent(watch.deadLetterCount)
        return null
    }

    private fun linkLine(
        watch: WatchState?,
        features: Set<String>,
    ): WatchLinkLine? {
        if (watch == null || WearFeatures.LINK_CLOUD !in features) return null
        return when (watch.link) {
            PeerLinkState.PAIRED -> WatchLinkLine.THROUGH_PHONE
            PeerLinkState.LINKED -> WatchLinkLine.ON_ITS_OWN
            PeerLinkState.UNLINKED, PeerLinkState.NEEDS_RELINK -> null
        }
    }
}
