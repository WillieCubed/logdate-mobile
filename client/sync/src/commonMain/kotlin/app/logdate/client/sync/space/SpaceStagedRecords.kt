package app.logdate.client.sync.space

import app.logdate.client.database.entities.sync.DownloadInboxEntity
import app.logdate.client.sync.RemoteOutcome
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.space.SealedRecordCodec
import io.github.aakira.napier.Napier
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One kind's personal-space records staged in the download inbox, joined into that kind's own items
 * so its existing download path applies them, resolving conflicts as it always has.
 *
 * Each record has its own row. [page] groups the due rows by the item they belong to and builds
 * each item from them, on top of the local copy for records that didn't change. The download path
 * reports what became of each item through [settle], inside the transaction that applied it, and
 * only then is each record's version recorded: a write sends that version back as its swap
 * condition, so an edit made here before the record was applied conflicts instead of overwriting a
 * change this device hasn't shown yet.
 */
class SpaceStagedRecords<T : Any> internal constructor(
    private val kind: SpaceKind<T>,
    private val inbox: DownloadInbox,
    private val swapStore: SpaceSwapStore,
    private val access: SpaceAccess,
    private val selected: DownloadScope,
) {
    private val type = spacePartsType(kind.entityType)
    private val offered = mutableMapOf<Uuid, List<Staged>>()
    private val seen = mutableSetOf<String>()
    private var lastReadAt: Instant? = null

    /** The items to apply, the items deleted, when they were read, and whether more rows are due. */
    class Page<T>(
        val changes: List<T>,
        val deletions: List<Uuid>,
        val readAt: Instant,
        val hasMore: Boolean,
    )

    internal suspend fun hasPending(): Boolean = inbox.pending(type, selected).isNotEmpty()

    /** Reads the due rows not offered yet in this download and joins them into items. */
    suspend fun page(): Page<T> {
        val due = inbox.pending(type, selected)
        val rows = due.filter { seen.add(it.entityId) }
        val changes = mutableListOf<T>()
        val deletions = mutableListOf<Uuid>()
        for ((owner, staged) in group(rows)) {
            val id = Uuid.parseOrNull(owner)
            if (id == null) {
                staged.forEach { fail(it.row, DiagnosticReason.CORRUPT_PAYLOAD) }
                continue
            }
            val mainChange = staged.lastOrNull { it.part === kind.main }
            if (mainChange?.row?.deleted == true) {
                deletions += id
                offered[id] = staged
                continue
            }
            val item = assemble(owner, staged) ?: continue
            changes += item.first
            offered[id] = item.second
        }
        val now = Clock.System.now()
        val readAt = lastReadAt?.let { maxOf(now, it + 1.milliseconds) } ?: now
        lastReadAt = readAt
        return Page(changes, deletions, readAt, hasMore = due.size >= INBOX_PAGE_SIZE && rows.isNotEmpty())
    }

    /**
     * Records what became of item [id]. Runs inside the transaction that applied it, so the item and
     * the versions recorded for its records are stored together or not at all.
     */
    internal suspend fun settle(
        id: Uuid,
        outcome: RemoteOutcome,
    ) {
        val staged = offered.remove(id) ?: return
        when (outcome) {
            RemoteOutcome.STORED, RemoteOutcome.MERGED, RemoteOutcome.KEPT_LOCAL -> staged.forEach { record(it) }
            RemoteOutcome.DELETED -> forgetItem(id.toString())
            RemoteOutcome.DECLINED -> Unit
        }
        staged.forEach { inbox.applied(type, it.row.entityId, it.row.version, selected, enqueueMedia = false) }
    }

    /** Leaves item [id]'s rows to be offered again after the inbox's backoff. */
    internal suspend fun settleFailed(id: Uuid) {
        offered.remove(id)?.forEach { fail(it.row, DiagnosticReason.LOCAL_STORAGE) }
    }

    private suspend fun group(rows: List<DownloadInboxEntity>): Map<String, List<Staged>> {
        val byOwner = linkedMapOf<String, MutableList<Staged>>()
        for (row in rows.sortedBy { it.version }) {
            val payload = SpaceInboxPayload.decode(row.payload)
            val part = payload?.let { staged -> kind.parts.firstOrNull { it.collection == staged.collection } }
            if (payload == null || part == null) {
                fail(row, DiagnosticReason.CORRUPT_PAYLOAD)
                continue
            }
            val owner = canonicalRecordKey(part.ownerOf(payload.rkey))
            byOwner.getOrPut(owner) { mutableListOf() } += Staged(row, payload, part)
        }
        return byOwner
    }

    /** The item [staged] builds and the rows it used, or null while it can't be built yet. */
    private suspend fun assemble(
        owner: String,
        staged: List<Staged>,
    ): Pair<T, List<Staged>>? {
        val used = mutableListOf<Staged>()
        val changes = mutableMapOf<String, MutableList<SpacePartChange<*>>>()
        for (record in staged) {
            val change =
                if (record.row.deleted) {
                    SpacePartChange.Deleted(record.payload.rkey, record.row.version)
                } else {
                    decode(record) ?: continue
                }
            changes.getOrPut(record.part.collection) { mutableListOf() } += change
            used += record
        }
        if (used.isEmpty()) return null
        val local = kind.local.load(owner)
        val item =
            try {
                kind.assemble(SpaceItemChanges(owner, changes), local)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SpaceRecordMappingException) {
                Napier.w("${kind.entityType} records couldn't be joined into one item: ${error.message}")
                used.forEach { fail(it.row, DiagnosticReason.UNSUPPORTED_FORMAT) }
                return null
            }
        val nothingWritten = used.all { it.row.deleted }
        if (item == null && local == null && changes[kind.main.collection] == null && (nothingWritten || mainDeleted(owner))) {
            // The item is gone or was never here, so these records have nothing to join.
            used.forEach { inbox.skipped(type, it.row.entityId, it.row.version, selected) }
        }
        return item?.let { it to used }
    }

    /** The value [record] holds, or null after keeping it for a later version that can read it. */
    private suspend fun decode(record: Staged): SpacePartChange<*>? {
        val content = record.payload.record
        if (content == null) {
            fail(record.row, DiagnosticReason.CORRUPT_PAYLOAD)
            return null
        }
        return try {
            SpacePartChange.Written(record.payload.rkey, record.part.mapper.fromRecord(record.payload.rkey, content), record.row.version)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // The record matched its schema, so only a mapper that knows more can read it.
            Napier.w("A ${record.payload.collection} record couldn't be read as LogDate data: ${error::class.simpleName}")
            fail(record.row, DiagnosticReason.UNSUPPORTED_FORMAT)
            null
        }
    }

    private suspend fun mainDeleted(owner: String): Boolean {
        val sealed = SealedRecordCodec.sealedRkey(access.keys, kind.main.collection, owner)
        val row = inbox.row(type, sealed, selected) ?: return false
        return row.deleted && row.state == "APPLIED"
    }

    private suspend fun record(staged: Staged) {
        val payload = staged.payload
        if (staged.row.deleted) {
            swapStore.delete(access.scope, payload.collection, payload.rkey)
            return
        }
        val cid = payload.cid ?: return
        val known = swapStore.get(access.scope, payload.collection, payload.rkey)
        if (known != null && known.seq > staged.row.version) return
        swapStore.put(access.scope, SpaceRecordState(payload.collection, payload.rkey, payload.sealedRkey, cid, staged.row.version))
    }

    private suspend fun forgetItem(owner: String) {
        for (part in kind.parts) {
            part.rkeysOf(owner).forEach { swapStore.delete(access.scope, part.collection, canonicalRecordKey(it)) }
        }
    }

    private suspend fun fail(
        row: DownloadInboxEntity,
        reason: DiagnosticReason,
    ) = inbox.failed(type, row.entityId, reason.name, row.version, selected)

    private class Staged(
        val row: DownloadInboxEntity,
        val payload: SpaceInboxPayload,
        val part: SpacePart<*, *>,
    )

    private companion object {
        /** How many due rows the inbox returns at once. */
        const val INBOX_PAGE_SIZE = 100
    }
}
