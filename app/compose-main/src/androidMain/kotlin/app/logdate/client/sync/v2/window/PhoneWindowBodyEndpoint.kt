package app.logdate.client.sync.v2.window

import android.net.Uri
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WindowBodyPaths
import app.logdate.client.sync.datalayer.v2.WindowBodyRequest
import app.logdate.client.sync.datalayer.v2.WindowBodyTicket
import app.logdate.client.sync.v2.media.GooglePhoneMediaSender
import com.google.android.gms.wearable.ChannelClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Thin listener boundary; every request is authorized again by the server. */
class PhoneWindowBodyEndpoint(
    private val server: PhoneWindowBodyServer,
    private val channels: ChannelClient,
) {
    suspend fun answer(
        node: String,
        path: String,
        payload: ByteArray,
    ): ByteArray? {
        if (path != WindowBodyPaths.RANGE || payload.size > 8192) return null
        val request = (WearJson.decode(payload, 1, WindowBodyRequest.serializer()) as? Decoded.Supported)?.value ?: return null
        if (request.schema != 1) return null
        return server.prepare(node, request)?.let { WearJson.encodeToBytes(WindowBodyTicket.serializer(), it) }
    }

    suspend fun serve(channel: ChannelClient.Channel) {
        try {
            server.serve(channel.nodeId, channel.path) { file, offset, length, current ->
                GooglePhoneMediaSender(channels).send(channel, Uri.fromFile(file), offset, length, current)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not send phone window body to the linked watch", e)
        } finally {
            withContext(NonCancellable) {
                try {
                    withTimeoutOrNull(5_000) { channels.close(channel).await() }
                } catch (e: Exception) {
                    Napier.w("Could not close a phone window body output channel", e)
                }
            }
        }
    }
}
