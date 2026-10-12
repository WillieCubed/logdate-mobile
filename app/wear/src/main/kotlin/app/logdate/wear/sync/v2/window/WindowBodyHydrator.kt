package app.logdate.wear.sync.v2.window

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WindowBodyPaths
import app.logdate.client.sync.datalayer.v2.WindowBodyRequest
import app.logdate.client.sync.datalayer.v2.WindowTombstoneDataMap
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.sync.v2.media.MediaRangeSource
import app.logdate.wear.sync.v2.media.MediaStreamClient
import app.logdate.wear.sync.v2.media.WatchMediaIdentity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import java.io.File

/** Hydrates local reconciliation input; the Data Layer's bounded envelope stays unchanged. */
class WindowBodyHydrator(
    private val source: WindowItemSource,
    private val peers: WatchDeliveryPeer,
    private val directory: File,
    private val ranges: (WindowBodyRequest) -> MediaRangeSource,
) : WindowItemSource {
    override suspend fun items(peer: PeerDeliveryContext): List<WindowItem> = source.items(peer).map { hydrate(peer, it) }

    private suspend fun hydrate(
        peer: PeerDeliveryContext,
        item: WindowItem,
    ): WindowItem {
        val path = WearPaths.parseWindowItem(item.path) ?: return item
        val envelope = (EnvelopeDataMap.fromDataMap(item.data) as? Decoded.Supported)?.value ?: return item
        val ref = envelope.bodyRef ?: return item
        val revision = item.data[WindowTombstoneDataMap.KEY_REVISION]?.toLongOrNull() ?: return item
        if (item.sourceNodeId != peer.phoneNode ||
            path.linkId != peer.linkId ||
            path.kind != RecordKind.NOTE ||
            envelope.kind != RecordKind.NOTE ||
            envelope.origin != RecordOrigin.PHONE ||
            envelope.context != peer ||
            envelope.id != path.id.toString() ||
            envelope.removed ||
            envelope.body != null ||
            envelope.contentVersion < 0 ||
            revision <= 0 ||
            ref.sizeBytes !in 1..WindowBodyPaths.MAX_BODY_BYTES ||
            !ref.sha256.matches(Regex("[0-9a-f]{64}")) ||
            !peers.isCurrent(peer)
        ) {
            return item
        }
        var file: File? = null
        return try {
            if (!directory.isDirectory && !directory.mkdirs()) return item
            val partial = File.createTempFile("window-body-", ".json", directory).also { file = it }
            val request =
                WindowBodyRequest(
                    context = peer,
                    noteId = envelope.id,
                    contentVersion = envelope.contentVersion,
                    revision = revision,
                    body = ref,
                )
            val identity = WatchMediaIdentity(peer, path.id, envelope.contentVersion, ref.sizeBytes, ref.sha256)
            if (!MediaStreamClient(ranges(request), { peers.isCurrent(it.peer) }).copyTo(identity, partial) {}) return item
            val body = WearJson.json.parseToJsonElement(partial.readBytes().decodeToString(throwOnInvalidSequence = true))
            if (!peers.isCurrent(peer)) return item
            // This map is local input, never a wire write; encoding it as a DataItem would exceed the inline limit.
            item.copy(
                data =
                    item.data + (
                        EnvelopeDataMap.KEY_ENVELOPE to
                            WearJson.encode(
                                RecordEnvelope.serializer(),
                                envelope.copy(body = body, bodyRef = null),
                            )
                    ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("A large phone note could not be verified; its library snapshot remains incomplete", e)
            item
        } finally {
            file?.delete()
        }
    }
}
