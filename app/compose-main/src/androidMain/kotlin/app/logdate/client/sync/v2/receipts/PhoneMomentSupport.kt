package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import kotlinx.coroutines.flow.first

class PhoneMomentSupport(
    private val enabled: suspend () -> Boolean,
    private val states: PeerStateStore,
) {
    suspend fun accepts(peer: PeerDeliveryContext): Boolean {
        if (!enabled()) return false
        val state = states.documents(PeerRole.WATCH).first()[peer.watchNode]?.watch ?: return false
        return state.linkId == peer.linkId &&
            WearFeatures.MOMENTS in state.features &&
            WearFeatures.OPERATION_RECEIPTS in state.features &&
            enabled()
    }
}
