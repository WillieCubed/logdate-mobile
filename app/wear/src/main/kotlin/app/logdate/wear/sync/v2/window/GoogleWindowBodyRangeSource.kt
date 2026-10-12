package app.logdate.wear.sync.v2.window

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WindowBodyPaths
import app.logdate.client.sync.datalayer.v2.WindowBodyRequest
import app.logdate.client.sync.datalayer.v2.WindowBodyTicket
import app.logdate.wear.sync.v2.delivery.DataLayerTransportV2
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.media.GoogleChannelRangeSource
import app.logdate.wear.sync.v2.media.MediaRangeConnection
import app.logdate.wear.sync.v2.media.MediaRangeRequest
import app.logdate.wear.sync.v2.media.MediaRangeSource
import com.google.android.gms.wearable.ChannelClient
import java.io.IOException
import kotlin.uuid.Uuid

class GoogleWindowBodyRangeSource(
    peers: WatchDeliveryPeer,
    private val transport: DataLayerTransportV2,
    channels: ChannelClient,
    private val body: WindowBodyRequest,
) : MediaRangeSource {
    private val source = GoogleChannelRangeSource(peers, channels, ::requestTicket)

    override suspend fun open(request: MediaRangeRequest): MediaRangeConnection = source.open(request)

    private suspend fun requestTicket(range: MediaRangeRequest): String {
        val identity = range.identity
        if (body.schema != 1 ||
            body.context != identity.peer ||
            body.noteId != identity.noteId.toString() ||
            body.contentVersion != identity.contentVersion ||
            body.body.sizeBytes != identity.sizeBytes ||
            body.body.sha256 != identity.sha256 ||
            body.revision <= 0 ||
            identity.mediaRef != null ||
            body.body.sizeBytes !in 1..WindowBodyPaths.MAX_BODY_BYTES
        ) {
            throw IOException("The window body identity changed")
        }
        val request = body.copy(offset = range.offset)
        val bytes = transport.request(WindowBodyPaths.RANGE, WearJson.encodeToBytes(WindowBodyRequest.serializer(), request)).getOrThrow()
        if (bytes.size > 8192) throw IOException("The window body ticket is too large")
        val ticket =
            (WearJson.decode(bytes, 1, WindowBodyTicket.serializer()) as? Decoded.Supported)?.value
                ?: throw IOException("The phone did not provide a supported window body ticket")
        val token = ticket.channelPath.removePrefix(WindowBodyPaths.CHANNEL_PREFIX)
        if (ticket.schema != 1 ||
            ticket.request != request ||
            !ticket.channelPath.startsWith(WindowBodyPaths.CHANNEL_PREFIX) ||
            Uuid.parseOrNull(token)?.toString() != token
        ) {
            throw IOException("The window body ticket does not match the requested range")
        }
        return ticket.channelPath
    }
}
