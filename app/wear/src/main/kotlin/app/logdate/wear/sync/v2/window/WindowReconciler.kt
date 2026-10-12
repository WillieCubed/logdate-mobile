package app.logdate.wear.sync.v2.window

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.client.sync.datalayer.v2.WindowResyncRequest
import app.logdate.client.sync.datalayer.v2.WindowResyncResponse
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface WindowItemSource {
    suspend fun items(peer: PeerDeliveryContext): List<WindowItem>
}

class WindowReconciler(
    private val peers: WatchDeliveryPeer,
    private val supported: suspend (PeerDeliveryContext) -> Boolean,
    private val request: suspend (PeerDeliveryContext, String, ByteArray) -> Result<ByteArray>,
    private val source: WindowItemSource,
    private val applier: WindowApplier,
    private val changes: Flow<Unit> = emptyFlow(),
) {
    private val reconciling = Mutex()

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) =
        scope.launch {
            merge(flowOf(Unit), changes).debounce(2_000).collect { resync() }
        }

    suspend fun resync(): WindowApplied? =
        reconciling.withLock {
            try {
                val peer = peers.current() ?: return@withLock null
                if (!supported(peer)) return@withLock null
                val payload = WearJson.encodeToBytes(WindowResyncRequest.serializer(), WindowResyncRequest(context = peer))
                val bytes = request(peer, WearPaths.WINDOW_RESYNC, payload).getOrNull() ?: return@withLock null
                if (!peers.isCurrent(peer)) return@withLock null
                val response =
                    (WearJson.decode(bytes, WearProtocol.WINDOW_SCHEMA, WindowResyncResponse.serializer()) as? Decoded.Supported)?.value
                        ?: return@withLock null
                if (response.context != peer) return@withLock null
                val items = source.items(peer)
                if (!peers.isCurrent(peer)) return@withLock null
                applier.reconcile(peer, response, items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("The phone library could not be reconciled; local notes are preserved and resync will retry", e)
                null
            }
        }
}
