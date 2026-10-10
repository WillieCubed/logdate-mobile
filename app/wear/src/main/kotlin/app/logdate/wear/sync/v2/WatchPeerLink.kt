package app.logdate.wear.sync.v2

import android.net.Uri
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerCapabilityPresence
import app.logdate.client.sync.peer.state.PeerDocument
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import app.logdate.client.sync.peer.state.StateDocumentCodec
import app.logdate.wear.sync.WearDataLayerClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.WearableStatusCodes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The watch's Play services side of the state document: publishes through [WearDataLayerClient], reads
 * the phone's document from [DataClient], and manages this watch's dynamic capabilities.
 */
class WatchPeerLink(
    private val dataLayerClient: WearDataLayerClient,
    private val dataClient: DataClient,
    private val capabilityClient: CapabilityClient,
    private val nodeClient: NodeClient,
) : StateDocumentChannel,
    LocalCapabilities,
    PeerCapabilityPresence {
    override suspend fun publish(document: StateDocument): Boolean {
        val data =
            try {
                StateDocumentCodec.toDataMap(document)
            } catch (e: IllegalArgumentException) {
                Napier.e("This watch's state document is too large to publish", e)
                return false
            }
        return dataLayerClient.putDataItem(WearPaths.STATE, data, urgent = true)
    }

    override suspend fun peerDocuments(): List<PeerDocument> {
        val localNode = localNodeId() ?: return emptyList()
        val buffer =
            try {
                dataClient.getDataItems(stateItems()).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Could not read the phone's state document; it is read again when it next changes", e)
                return emptyList()
            }
        return try {
            buffer.mapNotNull { item ->
                val node = item.uri.host
                if (node == null || node == localNode) return@mapNotNull null
                val text = DataMapItem.fromDataItem(item).dataMap.getString(StateDocumentCodec.KEY)
                val decoded =
                    if (text == null) {
                        Decoded.Malformed("The data item has no state document")
                    } else {
                        StateDocumentCodec.decode(text)
                    }
                PeerDocument(node, decoded)
            }
        } finally {
            buffer.release()
        }
    }

    override suspend fun add(capability: String): Boolean =
        changeCapability(capability, adding = true) { capabilityClient.addLocalCapability(capability).await() }

    override suspend fun remove(capability: String): Boolean =
        changeCapability(capability, adding = false) { capabilityClient.removeLocalCapability(capability).await() }

    override fun observe(capability: String): Flow<Boolean> =
        callbackFlow {
            val listener = CapabilityClient.OnCapabilityChangedListener { launch { send(isAdvertised(capability)) } }
            val listening =
                try {
                    capabilityClient.addListener(listener, capability).await()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Napier.e("Could not follow $capability; it is reported as of now only", e)
                    false
                }
            send(isAdvertised(capability))
            awaitClose {
                if (listening) capabilityClient.removeListener(listener, capability)
            }
        }.distinctUntilChanged()

    private suspend fun isAdvertised(capability: String): Boolean =
        try {
            capabilityClient
                .getCapability(capability, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not read whether the phone advertises $capability; reporting that it does not", e)
            false
        }

    private suspend fun changeCapability(
        capability: String,
        adding: Boolean,
        change: suspend () -> Unit,
    ): Boolean =
        try {
            change()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            val alreadyDone =
                e.statusCode == if (adding) WearableStatusCodes.DUPLICATE_CAPABILITY else WearableStatusCodes.UNKNOWN_CAPABILITY
            if (!alreadyDone) Napier.e("Could not ${if (adding) "add" else "remove"} the capability $capability", e)
            alreadyDone
        } catch (e: Exception) {
            Napier.e("Could not ${if (adding) "add" else "remove"} the capability $capability", e)
            false
        }

    private suspend fun localNodeId(): String? =
        try {
            nodeClient.localNode.await().id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not read this watch's Data Layer node; the phone's state is read when it next changes", e)
            null
        }

    private fun stateItems(): Uri =
        Uri
            .Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .authority("*")
            .path(WearPaths.STATE)
            .build()
}
