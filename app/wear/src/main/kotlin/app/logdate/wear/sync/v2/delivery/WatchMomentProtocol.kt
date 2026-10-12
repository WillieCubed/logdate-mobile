package app.logdate.wear.sync.v2.delivery

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import kotlinx.coroutines.flow.first

class WatchMomentProtocol(
    private val enabled: suspend () -> Boolean,
    private val peers: WatchDeliveryPeer,
    private val states: PeerStateStore,
    private val storage: KeyValueStorage,
) {
    suspend fun accepts(peer: PeerDeliveryContext): Boolean {
        if (!enabled() || !peers.isCurrent(peer)) return false
        val identity = WearJson.encode(PeerDeliveryContext.serializer(), peer)
        val state = states.documents(PeerRole.PHONE).first()[peer.phoneNode]?.phone
        if (state != null) {
            if (WearFeatures.MOMENTS !in state.features || WearFeatures.OPERATION_RECEIPTS !in state.features) {
                if (storage.getString(KEY) == identity) storage.remove(KEY)
                return false
            }
            if (!enabled() || !peers.isCurrent(peer)) return false
            if (storage.getString(KEY) != identity) storage.putString(KEY, identity)
            val latest = states.documents(PeerRole.PHONE).first()[peer.phoneNode]?.phone
            if (latest != null && (WearFeatures.MOMENTS !in latest.features || WearFeatures.OPERATION_RECEIPTS !in latest.features)) {
                if (storage.getString(KEY) == identity) storage.remove(KEY)
                return false
            }
        }
        return storage.getString(KEY) == identity && enabled() && peers.isCurrent(peer)
    }

    private companion object {
        const val KEY = "watch.capture.moment_protocol.v1"
    }
}
