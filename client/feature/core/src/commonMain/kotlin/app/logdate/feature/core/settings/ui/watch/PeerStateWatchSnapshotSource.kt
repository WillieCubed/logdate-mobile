package app.logdate.feature.core.settings.ui.watch

import app.logdate.client.sync.datalayer.v2.WearProtocolGate
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.StateMerger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reads the watch's report from the state documents the phone received, through [StateMerger] so a
 * phone-owned section a watch document claims is ignored and a stale presence reads as unknown.
 */
class PeerStateWatchSnapshotSource(
    private val gate: WearProtocolGate,
    private val peerStates: PeerStateStore,
    private val merger: StateMerger = StateMerger(),
) : WatchPeerSnapshotSource {
    override fun observe(): Flow<WatchPeerSnapshot> =
        combine(
            gate.isV2Active(),
            peerStates.latest(PeerRole.WATCH),
            peerStates.features(PeerRole.WATCH),
        ) { active, document, features ->
            WatchPeerSnapshot(
                v2Active = active,
                watch = merger.merge(phone = null, watch = document).watch,
                features = features,
            )
        }.distinctUntilChanged()
}
