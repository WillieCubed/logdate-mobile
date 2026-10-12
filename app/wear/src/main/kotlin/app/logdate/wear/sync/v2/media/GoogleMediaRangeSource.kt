package app.logdate.wear.sync.v2.media

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.PhoneMediaDescriptor
import app.logdate.client.sync.datalayer.v2.PhoneMediaRangeRequest
import app.logdate.client.sync.datalayer.v2.PhoneMediaRangeTicket
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearMediaPaths
import app.logdate.wear.sync.v2.delivery.DataLayerTransportV2
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import com.google.android.gms.wearable.ChannelClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid

/** Requests and opens a one-use range only on the captured verified phone. */
class GoogleMediaRangeSource(
    private val peers: WatchDeliveryPeer,
    private val transport: DataLayerTransportV2,
    private val channels: ChannelClient,
) : MediaRangeSource {
    override suspend fun open(request: MediaRangeRequest): MediaRangeConnection {
        checkCurrent(request)
        val ticket = requestTicket(request)
        checkCurrent(request)
        val opening = channels.openChannel(request.identity.peer.phoneNode, ticket.channelPath)
        val channel =
            try {
                opening.await()
            } catch (e: CancellationException) {
                opening.addOnSuccessListener(Executor { it.run() }) { channels.close(it) }
                throw e
            }
        try {
            checkCurrent(request)
            if (channel.nodeId != request.identity.peer.phoneNode || channel.path != ticket.channelPath) {
                throw IOException("The media channel is from another phone or ticket")
            }
            val openingInput = channels.getInputStream(channel)
            val input =
                try {
                    openingInput.await()
                } catch (e: CancellationException) {
                    openingInput.addOnSuccessListener(Executor { it.run() }) { input ->
                        try {
                            input.close()
                        } catch (failure: Exception) {
                            Napier.w("Could not close a late phone media input stream", failure)
                        }
                    }
                    throw e
                }
            val connection = Connection(request, channel, input)
            try {
                checkCurrent(request)
            } catch (e: Exception) {
                connection.close()
                throw e
            }
            return connection
        } catch (e: Exception) {
            closeChannel(channel)
            throw e
        }
    }

    private suspend fun requestTicket(request: MediaRangeRequest): PhoneMediaRangeTicket {
        val media = request.identity.asDescriptor()
        val bytes =
            transport
                .request(
                    WearMediaPaths.RANGE,
                    WearJson.encodeToBytes(
                        PhoneMediaRangeRequest.serializer(),
                        PhoneMediaRangeRequest(media = media, offset = request.offset),
                    ),
                ).getOrThrow()
        if (bytes.size > 8192) throw IOException("The phone media ticket is too large")
        val ticket =
            (WearJson.decode(bytes, 1, PhoneMediaRangeTicket.serializer()) as? Decoded.Supported)?.value
                ?: throw IOException("The phone did not provide a supported media ticket")
        val token = ticket.channelPath.removePrefix(WearMediaPaths.CHANNEL_PREFIX)
        if (ticket.schema != 1 ||
            ticket.media != media ||
            ticket.offset != request.offset ||
            !ticket.channelPath.startsWith(WearMediaPaths.CHANNEL_PREFIX) ||
            Uuid.parseOrNull(token)?.toString() != token
        ) {
            throw IOException("The phone media ticket does not match the requested range")
        }
        return ticket
    }

    private suspend fun checkCurrent(request: MediaRangeRequest) {
        if (!peers.isCurrent(request.identity.peer) || request.offset !in 0..request.identity.sizeBytes) {
            throw IOException("The phone media exchange is no longer current")
        }
    }

    private inner class Connection(
        request: MediaRangeRequest,
        private val channel: ChannelClient.Channel,
        private val input: InputStream,
    ) : MediaRangeConnection {
        override val identity = request.identity
        override val offset = request.offset
        override val sourceNodeId = channel.nodeId
        private val closed = AtomicBoolean()

        override suspend fun read(buffer: ByteArray): Int =
            coroutineScope {
                val reading = async(Dispatchers.IO) { input.read(buffer) }
                try {
                    reading.await()
                } catch (e: CancellationException) {
                    close()
                    throw e
                }
            }

        override suspend fun close() {
            if (!closed.compareAndSet(false, true)) return
            withContext(NonCancellable + Dispatchers.IO) {
                closeChannel(channel)
                input.close()
            }
        }
    }

    private suspend fun closeChannel(channel: ChannelClient.Channel) =
        withContext(NonCancellable) {
            try {
                withTimeoutOrNull(5_000) { channels.close(channel).await() }
            } catch (e: Exception) {
                Napier.w("Could not close a phone media input channel", e)
            }
        }
}

internal fun WatchMediaIdentity.asDescriptor() =
    PhoneMediaDescriptor(
        context = peer,
        noteId = noteId.toString(),
        contentVersion = contentVersion,
        sizeBytes = sizeBytes,
        sha256 = sha256,
    )
