package app.logdate.client.sync.space

import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.shared.model.diagnostics.DiagnosticReason
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One kind's personal-space records staged in the download inbox, read as that kind's own items so
 * its existing download path applies them, resolving conflicts as it always has.
 *
 * Call [page] to read them and, once they have been applied, [settle] to record the version of
 * each applied record. A write sends that version back as its swap condition, so it is recorded
 * only after the record is applied: an edit made before then conflicts instead of overwriting a
 * change this device hasn't shown yet.
 */
class SpaceStagedRecords<T : Any> internal constructor(
    private val kind: SpaceKind<T>,
    private val inbox: DownloadInbox,
    private val swapStore: SpaceSwapStore,
    private val scope: UploadScope,
    private val selected: DownloadScope,
) {
    private val offered = mutableMapOf<String, Offered>()

    /** The staged records as items, the staged deletions, and when they were read. */
    class Page<T>(
        val changes: List<T>,
        val deletions: List<Uuid>,
        val readAt: Instant,
    )

    internal suspend fun hasPending(): Boolean =
        inbox.pending(kind.entityType.name, selected).any {
            SpaceInboxPayload.decode(it.payload) !=
                null
        }

    /**
     * Reads the staged records that are due. A record its mapper can't read is reported back to the
     * inbox as corrupt and retried later; it never reaches the apply path.
     */
    suspend fun page(): Page<T> {
        val type = kind.entityType.name
        val changes = mutableListOf<T>()
        val deletions = mutableListOf<Uuid>()
        for (row in inbox.pending(type, selected)) {
            // A row the per-kind endpoints staged before this account moved stays for them.
            val payload = SpaceInboxPayload.decode(row.payload) ?: continue
            val id = Uuid.parseOrNull(row.entityId)
            if (id == null) {
                inbox.failed(type, row.entityId, DiagnosticReason.CORRUPT_PAYLOAD.name, row.version, selected)
                continue
            }
            if (row.deleted) {
                deletions += id
                offered[row.entityId] = Offered(row.version, payload)
                continue
            }
            val record = payload.record
            val item =
                try {
                    requireNotNull(record) { "A staged record has no content" }
                    kind.mapper.fromRecord(payload.rkey, record, row.version)
                } catch (error: Exception) {
                    Napier.w("A staged ${payload.collection} record couldn't be read: ${error::class.simpleName}")
                    inbox.failed(type, row.entityId, DiagnosticReason.CORRUPT_PAYLOAD.name, row.version, selected)
                    continue
                }
            changes += item
            offered[row.entityId] = Offered(row.version, payload)
        }
        return Page(changes, deletions, Clock.System.now())
    }

    /** Records the version of every offered record the download path applied. */
    suspend fun settle() {
        val type = kind.entityType.name
        for ((id, offer) in offered) {
            val payload = offer.payload
            val cid = payload.cid
            if (cid == null) {
                if (inbox.appliedDeletionVersion(type, id, selected) == offer.version) {
                    swapStore.delete(scope, payload.collection, payload.rkey)
                }
                continue
            }
            if (inbox.isAppliedVersion(type, id, offer.version, selected)) {
                swapStore.put(scope, SpaceRecordState(payload.collection, payload.rkey, payload.sealedRkey, cid, offer.version))
            }
        }
        offered.clear()
    }

    private class Offered(
        val version: Long,
        val payload: SpaceInboxPayload,
    )
}
