package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordEnvelopeCodec
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import java.io.InputStream

/** Binds each event to its current source and keeps overlapping work on the same per-link ingestor. */
class ScopedCaptureIngestion(
    private val peers: PhoneDeliveryPeers,
    private val create: (PeerDeliveryContext) -> WatchRecordIngestorV2,
) {
    private class Lease(
        val ingestor: WatchRecordIngestorV2,
        var users: Int = 0,
    )

    private val leases = mutableMapOf<PeerDeliveryContext, Lease>()

    suspend fun onRecord(
        sourceNodeId: String?,
        path: String,
        data: Map<String, String>,
    ) {
        val source = sourceNodeId ?: return
        val peer = peers.current(source) ?: return
        val envelope = (EnvelopeDataMap.fromDataMap(data) as? Decoded.Supported)?.value ?: return
        if (envelope.context != peer) return
        using(peer) { ingestor ->
            val unbound = data + (EnvelopeDataMap.KEY_ENVELOPE to RecordEnvelopeCodec.encode(envelope.copy(context = null)))
            ingestor.onRecord(path, unbound)
        }
    }

    suspend fun onMedia(
        sourceNodeId: String?,
        path: String,
        input: InputStream,
    ) {
        val source = sourceNodeId ?: return
        val peer = peers.current(source) ?: return
        val record = WearPaths.parseRecordMedia(path) ?: return
        if (record.linkId != peer.linkId) return
        using(peer) { it.onMedia(WearPaths.recordMedia(record.kind, record.id), input) }
    }

    private suspend fun using(
        peer: PeerDeliveryContext,
        block: suspend (WatchRecordIngestorV2) -> Unit,
    ) {
        val lease = synchronized(leases) { leases.getOrPut(peer) { Lease(create(peer)) }.also { it.users++ } }
        try {
            if (peers.isCurrent(peer)) block(lease.ingestor)
        } finally {
            synchronized(leases) { if (--lease.users == 0) leases.remove(peer) }
        }
    }
}
