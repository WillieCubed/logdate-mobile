package app.logdate.wear.sync

import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.tasks.await

/**
 * Picks the phone a message or file from the watch goes to.
 *
 * The phone running LogDate advertises [WearDataLayerClient.PHONE_CAPABILITY]. Among reachable nodes
 * that do, one connected directly over Bluetooth is preferred to one reached through the cloud. Phone
 * builds whose capability file sat where Android does not read it advertise nothing, so when no node
 * advertises the capability the watch falls back to a connected node, nearby first, which is where
 * every message went before this selector existed.
 */
class PhoneNodeSelector(
    private val capabilityClient: CapabilityClient,
    private val nodeClient: NodeClient,
) {
    /** The phone to send to, or null when no phone is connected at all. */
    suspend fun select(): Node? {
        val advertising =
            capabilityClient
                .getCapability(WearDataLayerClient.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        nearestOf(advertising)?.let { return it }
        return nearestOf(nodeClient.connectedNodes.await())?.also { node ->
            Napier.i("No reachable node advertises ${WearDataLayerClient.PHONE_CAPABILITY}; using connected node ${node.displayName}")
        }
    }

    private fun nearestOf(nodes: Collection<Node>): Node? = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
}
