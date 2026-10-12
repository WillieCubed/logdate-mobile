package app.logdate.client.sync.v2.window

import app.logdate.client.domain.watch.WatchWindowPolicy
import app.logdate.client.domain.watch.WindowBudget
import app.logdate.client.domain.watch.WindowCandidate
import app.logdate.client.domain.watch.WindowExtraIds
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.datalayer.v2.BodyRef
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordEnvelopeCodec
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.datalayer.v2.WindowTombstoneDataMap
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.MomentEnvelopeBody
import kotlinx.datetime.TimeZone
import java.security.MessageDigest
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class MomentWindowPublished(
    val members: List<WindowMemberRef> = emptyList(),
    val written: Int = 0,
    val removed: Int = 0,
    val failed: Int = 0,
)

/** Separate moment ownership shares the library's total budget and revision sequence. */
class PhoneMomentWindowPublisher(
    private val manifest: WindowManifestStore,
    private val items: WindowDataItems,
) {
    private class Counts(
        var written: Int = 0,
        var removed: Int = 0,
        var failed: Int = 0,
    )

    suspend fun publish(
        peer: PeerDeliveryContext,
        marks: List<MarkedMoment>,
        now: Instant,
        zone: TimeZone,
        dayStartHour: Int,
        maxItems: Int,
        maxBytes: Long,
        interrupted: Boolean,
        current: suspend () -> Unit,
    ): MomentWindowPublished {
        current()
        val all = marks.associateBy { it.id }
        val candidates = all.values.map { WindowCandidate(it.id, it.capturedAt, bodyBytes(it).size.toLong()) }
        val chosen =
            WatchWindowPolicy(
                budget = WindowBudget(maxItems, maxBytes),
            ).choose(candidates, WindowExtraIds(), now, zone, dayStartHour)
        val previous = manifest.read(peer)
        val published = previous.moments.toMutableMap()
        val tombstones = previous.momentTombstones.toMutableMap()
        val counts = Counts()
        for (member in chosen.members) {
            current()
            val mark = all.getValue(member.id)
            val body = checkNotNull(MomentEnvelopeBody.envelope(mark, RecordOrigin.PHONE).body)
            val bytes = bodyBytes(mark)
            val hash = digest(bytes)
            val existing = published[mark.id.toString()]
            val wanted =
                PublishedItem(mark.lastUpdated.toEpochMilliseconds(), member.reasons, bodySha256 = hash, syncVersion = mark.syncVersion)
            if (!interrupted &&
                existing?.contentVersion == wanted.contentVersion &&
                existing.reasons == wanted.reasons &&
                existing.bodySha256 == hash &&
                existing.syncVersion == mark.syncVersion &&
                existing.revision > 0
            ) {
                continue
            }
            val reference = if (RecordEnvelopeCodec.needsChannel(body)) BodyRef(hash, bytes.size.toLong()) else null
            val envelope =
                MomentEnvelopeBody.envelope(mark, RecordOrigin.PHONE).copy(
                    context = peer,
                    reasons = member.reasons,
                    body = body.takeIf { reference == null },
                    bodyRef = reference,
                )
            val data = EnvelopeDataMap.toDataMap(envelope, wanted.contentVersion)
            val revision = manifest.reserveRevision(peer)
            current()
            if (mark.id.toString() in tombstones || interrupted) {
                if (!items.deleteOwn(WearPaths.windowTombstone(RecordKind.MOMENT, mark.id, peer))) {
                    counts.failed++
                    continue
                }
                current()
                tombstones.remove(mark.id.toString())
            }
            if (!items.put(
                    WearPaths.windowItem(RecordKind.MOMENT, mark.id, peer),
                    data + (WindowTombstoneDataMap.KEY_REVISION to revision.toString()),
                    false,
                )
            ) {
                counts.failed++
                continue
            }
            current()
            published[mark.id.toString()] = wanted.copy(revision = revision)
            counts.written++
        }
        val desired = chosen.members.mapTo(mutableSetOf()) { it.id.toString() }
        for (id in published.keys.toSet() - desired) {
            current()
            val uuid = Uuid.parse(id)
            val revision = manifest.reserveRevision(peer)
            current()
            if (!items.put(
                    WearPaths.windowTombstone(RecordKind.MOMENT, uuid, peer),
                    WindowTombstoneDataMap.toDataMap(now.toEpochMilliseconds(), peer, revision),
                    true,
                )
            ) {
                counts.failed++
                continue
            }
            current()
            published.remove(id)
            tombstones[id] = now.toEpochMilliseconds()
            counts.removed++
        }
        for ((id, at) in tombstones.toMap()) {
            if (id in desired) continue
            current()
            val uuid = Uuid.parse(id)
            if (!items.deleteOwn(WearPaths.windowItem(RecordKind.MOMENT, uuid, peer))) {
                counts.failed++
                continue
            }
            current()
            if (at < now.toEpochMilliseconds() - TOMBSTONE_KEEP_MS) {
                if (items.deleteOwn(WearPaths.windowTombstone(RecordKind.MOMENT, uuid, peer))) {
                    current()
                    tombstones.remove(id)
                } else {
                    counts.failed++
                }
            }
        }
        current()
        manifest.write(peer, previous.copy(moments = published, momentTombstones = tombstones))
        current()
        return MomentWindowPublished(
            chosen.members.mapNotNull { member ->
                published[member.id.toString()]?.let {
                    WindowMemberRef(RecordKind.MOMENT, member.id.toString(), it.contentVersion, it.revision)
                }
            },
            counts.written,
            counts.removed,
            counts.failed,
        )
    }

    private fun bodyBytes(mark: MarkedMoment) =
        checkNotNull(MomentEnvelopeBody.envelope(mark, RecordOrigin.PHONE).body).toString().encodeToByteArray()

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TOMBSTONE_KEEP_MS = 7L * 24 * 60 * 60 * 1000
    }
}
