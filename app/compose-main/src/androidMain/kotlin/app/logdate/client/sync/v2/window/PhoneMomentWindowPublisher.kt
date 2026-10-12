package app.logdate.client.sync.v2.window

import app.logdate.client.domain.watch.WatchWindowPolicy
import app.logdate.client.domain.watch.WatchWindowPreferences
import app.logdate.client.domain.watch.WindowBudget
import app.logdate.client.domain.watch.WindowCandidate
import app.logdate.client.domain.watch.WindowExtraIds
import app.logdate.client.domain.watch.WindowMember
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
import kotlinx.serialization.json.JsonElement
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
        deletions: Map<Uuid, Instant> = emptyMap(),
        preferences: WatchWindowPreferences = WatchWindowPreferences(),
        current: suspend () -> Unit,
    ): MomentWindowPublished {
        current()
        val previous = manifest.read(peer)
        val deletedIds = previous.momentDeletions.keys + deletions.keys.map { it.toString() }
        val all = marks.filter { it.id.toString() !in deletedIds }.associateBy { it.id }
        val candidates = all.values.map { WindowCandidate(it.id, it.capturedAt, bodyBytes(it).size.toLong()) }
        val chosen =
            WatchWindowPolicy(
                budget = WindowBudget(maxItems, maxBytes),
            ).choose(candidates, WindowExtraIds(), now, zone, dayStartHour, preferences)
        val published = previous.moments.toMutableMap()
        val tombstones = previous.momentTombstones.toMutableMap()
        val counts = Counts()
        val deletionProofs = previous.momentDeletions.toMutableMap()
        val requestedDeletions =
            previous.momentDeletions.mapValues { Instant.fromEpochMilliseconds(it.value.deletedAt) } +
                deletions.mapKeys { it.key.toString() }
        publishDeletions(peer, now, interrupted, requestedDeletions, deletionProofs, published, tombstones, counts, current)
        publishMembers(peer, chosen.members, all, interrupted, published, tombstones, counts, current)
        val desired = chosen.members.mapTo(mutableSetOf()) { it.id.toString() }
        evictMembers(peer, now, desired, deletedIds, published, tombstones, counts, current)
        clearOldTombstones(peer, now, desired, deletedIds, tombstones, counts, current)
        current()
        manifest.write(peer, previous.copy(moments = published, momentTombstones = tombstones, momentDeletions = deletionProofs))
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

    private suspend fun publishDeletions(
        peer: PeerDeliveryContext,
        now: Instant,
        interrupted: Boolean,
        requestedDeletions: Map<String, Instant>,
        deletionProofs: MutableMap<String, PublishedMomentDeletion>,
        published: MutableMap<String, PublishedItem>,
        tombstones: MutableMap<String, Long>,
        counts: Counts,
        current: suspend () -> Unit,
    ) {
        for ((id, at) in requestedDeletions) {
            current()
            val old = deletionProofs[id]
            val deletedAt = maxOf(old?.deletedAt ?: 0, at.toEpochMilliseconds())
            if (interrupted || old == null || old.deletedAt < deletedAt) {
                val revision = manifest.reserveRevision(peer)
                current()
                val data =
                    WindowTombstoneDataMap.toDataMap(now.toEpochMilliseconds(), peer, revision) +
                        (WindowTombstoneDataMap.KEY_DELETED_AT to deletedAt.toString())
                if (!items.put(WearPaths.windowTombstone(RecordKind.MOMENT, Uuid.parse(id), peer), data, true)) {
                    counts.failed++
                    continue
                }
                current()
                deletionProofs[id] = PublishedMomentDeletion(deletedAt, revision)
                counts.removed++
            }
            published.remove(id)
            tombstones.remove(id)
            current()
            if (!items.deleteOwn(WearPaths.windowItem(RecordKind.MOMENT, Uuid.parse(id), peer))) counts.failed++
        }
    }

    private suspend fun publishMembers(
        peer: PeerDeliveryContext,
        members: List<WindowMember>,
        all: Map<Uuid, MarkedMoment>,
        interrupted: Boolean,
        published: MutableMap<String, PublishedItem>,
        tombstones: MutableMap<String, Long>,
        counts: Counts,
        current: suspend () -> Unit,
    ) {
        for (member in members) {
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
            val data = encodeMoment(peer, mark, member, body, hash, bytes)
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
    }

    private fun encodeMoment(
        peer: PeerDeliveryContext,
        mark: MarkedMoment,
        member: WindowMember,
        body: JsonElement,
        hash: String,
        bytes: ByteArray,
    ): Map<String, String> {
        val reference = if (RecordEnvelopeCodec.needsChannel(body)) BodyRef(hash, bytes.size.toLong()) else null
        val envelope =
            MomentEnvelopeBody.envelope(mark, RecordOrigin.PHONE).copy(
                context = peer,
                reasons = member.reasons,
                body = body.takeIf { reference == null },
                bodyRef = reference,
            )
        return EnvelopeDataMap.toDataMap(envelope, mark.lastUpdated.toEpochMilliseconds())
    }

    private suspend fun evictMembers(
        peer: PeerDeliveryContext,
        now: Instant,
        desired: Set<String>,
        deletedIds: Set<String>,
        published: MutableMap<String, PublishedItem>,
        tombstones: MutableMap<String, Long>,
        counts: Counts,
        current: suspend () -> Unit,
    ) {
        for (id in published.keys.toSet() - desired - deletedIds) {
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
    }

    private suspend fun clearOldTombstones(
        peer: PeerDeliveryContext,
        now: Instant,
        desired: Set<String>,
        deletedIds: Set<String>,
        tombstones: MutableMap<String, Long>,
        counts: Counts,
        current: suspend () -> Unit,
    ) {
        for ((id, at) in tombstones.toMap()) {
            if (id in desired || id in deletedIds) continue
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
    }

    private fun bodyBytes(mark: MarkedMoment) =
        checkNotNull(MomentEnvelopeBody.envelope(mark, RecordOrigin.PHONE).body).toString().encodeToByteArray()

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TOMBSTONE_KEEP_MS = 7L * 24 * 60 * 60 * 1000
    }
}
