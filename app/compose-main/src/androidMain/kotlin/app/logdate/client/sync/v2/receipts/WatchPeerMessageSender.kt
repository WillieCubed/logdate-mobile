package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import com.google.android.gms.wearable.MessageClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

fun interface WatchPeerMessageSender {
    suspend fun send(
        context: PeerDeliveryContext,
        path: String,
        data: ByteArray,
    ): Boolean
}

class GoogleWatchPeerMessageSender(
    private val messages: MessageClient,
    private val peers: PhoneDeliveryPeers,
) : WatchPeerMessageSender {
    override suspend fun send(
        context: PeerDeliveryContext,
        path: String,
        data: ByteArray,
    ): Boolean {
        if (!peers.isCurrent(context)) return false
        return try {
            messages.sendMessage(context.watchNode, path, data).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not send a receipt to its linked watch", e)
            false
        }
    }
}
