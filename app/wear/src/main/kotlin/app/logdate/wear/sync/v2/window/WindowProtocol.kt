package app.logdate.wear.sync.v2.window

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import kotlinx.coroutines.flow.first

/** Remembers negotiation across phone unavailability so old deletes cannot bypass the window. */
class WindowProtocol(
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
        if (!supports(peer)) return false
        storage.putString(KEY, identity)
        return peers.isCurrent(peer) && enabled()
    }

    suspend fun supports(peer: PeerDeliveryContext): Boolean {
        if (!enabled() || !peers.isCurrent(peer)) return false
        val phone = states.documents(PeerRole.PHONE).first()[peer.phoneNode]?.phone
        if (WearFeatures.WINDOW !in phone?.features.orEmpty() || !peers.isCurrent(peer) || !enabled()) return false
        val identity = WearJson.encode(PeerDeliveryContext.serializer(), peer)
        if (storage.getString(KEY) != identity) storage.putString(KEY, identity)
        return peers.isCurrent(peer) && enabled()
    }

    suspend fun dayStartHour(): Int? {
        val peer = peers.current() ?: return null
        if (!supports(peer)) return null
        val hour =
            states
                .documents(PeerRole.PHONE)
                .first()[peer.phoneNode]
                ?.phone
                ?.dayStartHour
        return hour?.takeIf { it in 0..23 && peers.isCurrent(peer) }
    }

    private companion object {
        const val KEY = "watch.window.protocol.v2"
    }
}
