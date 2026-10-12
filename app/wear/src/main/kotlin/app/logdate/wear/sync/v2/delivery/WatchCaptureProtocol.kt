package app.logdate.wear.sync.v2.delivery

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import kotlinx.coroutines.flow.first

/** Remembers operation-receipt negotiation for one verified exchange, independently of reachability. */
class WatchCaptureProtocol(
    private val enabled: suspend () -> Boolean,
    private val peers: WatchDeliveryPeer,
    private val states: PeerStateStore,
    private val storage: KeyValueStorage,
) {
    suspend fun active(): Boolean {
        if (!enabled()) return false
        val remembered = storage.getString(KEY)
        val peer = peers.current() ?: return remembered != null && enabled()
        val identity = WearJson.encode(PeerDeliveryContext.serializer(), peer)
        if (remembered == identity) return peers.isCurrent(peer) && enabled()
        val document = states.documents(PeerRole.PHONE).first()[peer.phoneNode]
        if (WearFeatures.OPERATION_RECEIPTS !in document?.phone?.features.orEmpty()) return false
        if (!peers.isCurrent(peer) || !enabled()) return false
        storage.putString(KEY, identity)
        return peers.isCurrent(peer) && enabled()
    }

    private companion object {
        const val KEY = "watch.capture.operation_protocol.v1"
    }
}
