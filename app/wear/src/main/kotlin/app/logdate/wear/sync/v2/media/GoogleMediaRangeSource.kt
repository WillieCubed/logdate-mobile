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
import java.io.IOException
import kotlin.uuid.Uuid

/** Requests a media ticket and delegates native channel ownership to the shared range source. */
class GoogleMediaRangeSource(
    peers: WatchDeliveryPeer,
    private val transport: DataLayerTransportV2,
    channels: ChannelClient,
) : MediaRangeSource {
    private val source = GoogleChannelRangeSource(peers, channels) { requestTicket(it).channelPath }

    override suspend fun open(request: MediaRangeRequest): MediaRangeConnection = source.open(request)

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
}

internal fun WatchMediaIdentity.asDescriptor() =
    PhoneMediaDescriptor(
        context = peer,
        noteId = noteId.toString(),
        contentVersion = contentVersion,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        mediaRef = mediaRef,
    )
