package app.logdate.client.sync.space

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.WireDownload
import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.space.ChangePage
import app.logdate.space.DataHomeCursorTooOldException
import app.logdate.space.SealedRecordCodec
import app.logdate.space.SealedRecordException
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.lexicon.LexiconRecordValidator
import studio.hypertext.atproto.syntax.Nsid

/**
 * Reads the personal space's change feed into the download inbox.
 *
 * The feed carries every kind of data under one cursor, the `SPACE` checkpoint. Each change is
 * opened, checked against its schema, and staged under its kind's entity type, where that kind's
 * existing download path applies it. A change that can't be opened or checked is staged under
 * [SPACE_UNREADABLE] with the reason, and opened again on later pulls, so it is never dropped.
 */
internal class SpaceDownloader(
    private val registry: SpaceRecordRegistry,
    private val swapStore: SpaceSwapStore,
    private val inbox: DownloadInbox,
    private val validator: LexiconRecordValidator,
    private val pageSize: Int = PAGE_SIZE,
) {
    suspend fun pull(
        access: SpaceAccess,
        client: SpaceClient,
    ): Result<Unit> {
        val selected = inbox.currentScope()
        retryUnreadable(access, selected)
        var cursor = inbox.cursor(SPACE_CHECKPOINT, selected)
        while (true) {
            val page =
                client.call("listChanges") { listChanges(access.space, cursor, pageSize) }.getOrElse { error ->
                    if (error is DataHomeCursorTooOldException) return reconcile(access, client, selected)
                    return fetchFailed(error, selected)
                }
            stagePage(access, selected, page)
            if (!page.hasMore) return Result.success(Unit)
            if (page.cursor <= cursor) return fetchFailed(stalled(), selected)
            cursor = page.cursor
        }
    }

    /**
     * The space purged deletions this device hadn't seen, so the feed can't say what was removed.
     * List everything from zero, stage it, and stage a deletion for every record this device holds
     * that the listing no longer has.
     */
    private suspend fun reconcile(
        access: SpaceAccess,
        client: SpaceClient,
        selected: DownloadScope,
    ): Result<Unit> {
        Napier.w("The personal space purged deletions since the last download; listing everything again")
        inbox.resetCheckpoint(SPACE_CHECKPOINT, selected)
        val live = mutableSetOf<String>()
        var cursor = 0L
        while (true) {
            val page =
                client
                    .call(
                        "listChanges",
                    ) { listChanges(access.space, cursor, pageSize) }
                    .getOrElse { return fetchFailed(it, selected) }
            page.changes.forEach { change -> if (change.deleted) live -= change.rkey else live += change.rkey }
            stagePage(access, selected, page)
            if (!page.hasMore) {
                stageMissing(access, selected, live, page.cursor)
                return Result.success(Unit)
            }
            if (page.cursor <= cursor) return fetchFailed(stalled(), selected)
            cursor = page.cursor
        }
    }

    private suspend fun stageMissing(
        access: SpaceAccess,
        selected: DownloadScope,
        live: Set<String>,
        cursor: Long,
    ) {
        val rows = mutableMapOf<String, MutableList<WireDownload>>()
        for (state in swapStore.all(access.scope)) {
            if (state.sealedRkey in live) continue
            val kind = registry.forCollection(state.collection) ?: continue
            val deletion = SpaceInboxPayload(state.collection, state.rkey, state.sealedRkey).encode()
            // Newer than any version this device staged for the record, so the deletion isn't skipped.
            val version = maxOf(cursor, state.seq + 1)
            rows.getOrPut(kind.entityType.name) { mutableListOf() } +=
                WireDownload(kind.mapper.entityIdOf(state.rkey), version, true, deletion)
        }
        inbox.stageAll(SPACE_CHECKPOINT, cursor, rows, selected)
    }

    private suspend fun stagePage(
        access: SpaceAccess,
        selected: DownloadScope,
        page: ChangePage,
    ) {
        val rows = mutableMapOf<String, MutableList<WireDownload>>()
        val unreadable = mutableListOf<Pair<WireDownload, DiagnosticReason>>()
        val readable = mutableListOf<String>()
        for (change in page.changes.associateBy { it.rkey }.values) {
            val known = swapStore.bySealedRkey(access.scope, change.rkey)
            if (change.deleted) {
                val state = known ?: continue
                val kind = registry.forCollection(state.collection) ?: continue
                val deletion = SpaceInboxPayload(state.collection, state.rkey, state.sealedRkey).encode()
                rows.getOrPut(kind.entityType.name) { mutableListOf() } +=
                    WireDownload(kind.mapper.entityIdOf(state.rkey), change.seq, true, deletion)
                continue
            }
            // This device wrote or already applied exactly this version.
            if (known != null && known.cid == change.cid) continue
            when (val read = read(access, change.rkey, change.cid, change.record)) {
                is Read.Readable -> {
                    rows.getOrPut(read.kind.entityType.name) { mutableListOf() } +=
                        WireDownload(read.kind.mapper.entityIdOf(read.payload.rkey), change.seq, false, read.payload.encode())
                    readable += change.rkey
                }
                is Read.Unreadable -> unreadable += WireDownload(change.rkey, change.seq, false, read.retained) to read.reason
            }
        }
        if (unreadable.isNotEmpty()) rows[SPACE_UNREADABLE] = unreadable.map { it.first }.toMutableList()
        inbox.stageAll(SPACE_CHECKPOINT, page.cursor, rows, selected)
        for ((row, reason) in unreadable) {
            inbox.failed(SPACE_UNREADABLE, row.id, reason.name, row.version, selected)
        }
        for (sealedRkey in readable) settleUnreadable(sealedRkey, selected)
    }

    /** Opens again the changes earlier pulls couldn't read, now that a key or a newer app may read them. */
    private suspend fun retryUnreadable(
        access: SpaceAccess,
        selected: DownloadScope,
    ) {
        for (row in inbox.pending(SPACE_UNREADABLE, selected)) {
            val retained = SpaceUnreadablePayload.decode(row.payload)
            if (retained == null) {
                inbox.failed(SPACE_UNREADABLE, row.entityId, DiagnosticReason.CORRUPT_PAYLOAD.name, row.version, selected)
                continue
            }
            when (val read = read(access, retained.sealedRkey, retained.cid, retained.record)) {
                is Read.Readable -> {
                    val kind = read.kind
                    val staged = WireDownload(kind.mapper.entityIdOf(read.payload.rkey), row.version, false, read.payload.encode())
                    inbox.stageAll(SPACE_CHECKPOINT, 0, mapOf(kind.entityType.name to listOf(staged)), selected)
                    inbox.skipped(SPACE_UNREADABLE, row.entityId, row.version, selected)
                }
                is Read.Unreadable -> inbox.failed(SPACE_UNREADABLE, row.entityId, read.reason.name, row.version, selected)
            }
        }
    }

    /** A readable version of a record supersedes an earlier unreadable one. */
    private suspend fun settleUnreadable(
        sealedRkey: String,
        selected: DownloadScope,
    ) {
        val version = inbox.serverVersion(SPACE_UNREADABLE, sealedRkey, selected) ?: return
        inbox.skipped(SPACE_UNREADABLE, sealedRkey, version, selected)
    }

    private suspend fun read(
        access: SpaceAccess,
        sealedRkey: String,
        cid: String?,
        envelope: Sealed?,
    ): Read {
        if (envelope == null || cid == null) return Read.Unreadable(DiagnosticReason.CORRUPT_PAYLOAD, "")
        val retained = SpaceUnreadablePayload(sealedRkey, cid, envelope).encode()
        if (envelope.version != SEALED_ENVELOPE_VERSION) return Read.Unreadable(DiagnosticReason.UNSUPPORTED_FORMAT, retained)
        if (envelope.keyId != access.keys.keyId) return Read.Unreadable(DiagnosticReason.KEY_RECOVERY_REQUIRED, retained)
        val payload =
            try {
                SealedRecordCodec.open(access.keys, access.space, access.author, sealedRkey, envelope)
            } catch (_: SealedRecordException) {
                return Read.Unreadable(DiagnosticReason.CORRUPT_PAYLOAD, retained)
            }
        val kind = registry.forCollection(payload.collection) ?: return Read.Unreadable(DiagnosticReason.UNSUPPORTED_FORMAT, retained)
        val record = payload.record as? JsonObject ?: return Read.Unreadable(DiagnosticReason.CORRUPT_PAYLOAD, retained)
        val issues = validator.validateRecord(Nsid.require(payload.collection), record).issues
        if (issues.isNotEmpty()) {
            // Paths only: an issue's message can quote the record's contents.
            Napier.w("A ${payload.collection} record failed its schema at ${issues.joinToString { it.path.ifEmpty { "(record)" } }}")
            return Read.Unreadable(DiagnosticReason.CORRUPT_PAYLOAD, retained)
        }
        return Read.Readable(kind, SpaceInboxPayload(payload.collection, payload.rkey, sealedRkey, cid, record))
    }

    private suspend fun fetchFailed(
        error: Throwable,
        selected: DownloadScope,
    ): Result<Unit> {
        val reason =
            when ((error as? CloudApiException)?.statusCode) {
                401, 403 -> DiagnosticReason.SIGN_IN_REQUIRED
                429 -> DiagnosticReason.RATE_LIMITED
                else -> DiagnosticReason.SERVER_UNAVAILABLE
            }
        inbox.fetchFailed(SPACE_CHECKPOINT, reason, selected)
        return Result.failure(error)
    }

    private fun stalled() = CloudApiException("SPACE_CURSOR_STALLED", "The personal space's change feed didn't advance")

    private sealed interface Read {
        class Readable(
            val kind: SpaceKind<*>,
            val payload: SpaceInboxPayload,
        ) : Read

        class Unreadable(
            val reason: DiagnosticReason,
            val retained: String,
        ) : Read
    }

    private companion object {
        const val PAGE_SIZE = 100

        /** The only sealed envelope format this version reads. */
        const val SEALED_ENVELOPE_VERSION = 1L
    }
}
