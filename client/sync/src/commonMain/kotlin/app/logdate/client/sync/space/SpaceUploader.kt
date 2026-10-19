package app.logdate.client.sync.space

import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncRetryCoordinator
import app.logdate.client.sync.beginAttempt
import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.dueNow
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import app.logdate.space.DataHomeConflictException
import app.logdate.space.RecordSwap
import app.logdate.space.SealedRecordCodec
import app.logdate.space.StoredRecord
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.lexicon.LexiconRecordValidator
import studio.hypertext.atproto.syntax.Nsid
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * Uploads one kind's outbox entries to the personal space as sealed records.
 *
 * A create is written only if the record is absent, and an update only if the record still has the
 * content identifier this device last saw. When the record changed elsewhere, the uploader reads
 * it, runs the kind's existing conflict resolver, and writes again with the version it just read.
 * Every outcome settles or retries the entry through [SyncRetryCoordinator], as the per-kind
 * uploads do.
 */
internal class SpaceUploader(
    private val swapStore: SpaceSwapStore,
    private val syncMetadataService: SyncMetadataService,
    private val retryCoordinator: SyncRetryCoordinator,
    private val validator: LexiconRecordValidator,
    private val recordProgress: (Int) -> Unit,
) {
    suspend fun <T : Any> upload(
        kind: SpaceKind<T>,
        access: SpaceAccess,
        client: SpaceClient,
        uploadsUpdates: Boolean,
        begin: (suspend (PendingUpload, T, MutableList<SyncError>) -> T?)?,
        afterDelete: suspend (PendingUpload) -> Unit,
    ): SyncResult {
        val type = kind.entityType
        val errors = mutableListOf<SyncError>()
        var uploaded = 0
        for (pending in syncMetadataService.getPendingUploads(type).dueNow(type, retryCoordinator)) {
            // An entry queued for another account or server waits until that one is signed in again.
            if (pending.scope != null && pending.scope != access.scope) continue
            if (pending.operation == PendingOperation.DELETE) {
                if (!retryCoordinator.beginAttempt(type, pending, errors)) continue
                uploaded += Upload(kind, access, client, pending, errors).delete(afterDelete)
                continue
            }
            if (pending.operation == PendingOperation.UPDATE && !uploadsUpdates) continue
            val item = kind.local.load(pending.entityId)
            if (item == null) {
                errors += retryCoordinator.recordUnavailableOutboxEntry(type, pending)
                continue
            }
            val ready =
                if (begin != null) {
                    begin(pending, item, errors) ?: continue
                } else {
                    if (!retryCoordinator.beginAttempt(type, pending, errors)) continue
                    item
                }
            uploaded += Upload(kind, access, client, pending, errors).write(ready)
        }
        return SyncResult(success = errors.isEmpty(), uploadedItems = uploaded, errors = errors)
    }

    /** One outbox entry's attempt. */
    private inner class Upload<T : Any>(
        private val kind: SpaceKind<T>,
        private val access: SpaceAccess,
        private val client: SpaceClient,
        private val pending: PendingUpload,
        private val errors: MutableList<SyncError>,
    ) {
        private val type = kind.entityType
        private val collection = kind.collection

        suspend fun write(item: T): Int {
            val rkey = kind.mapper.rkeyOf(item)
            val sealedRkey = SealedRecordCodec.sealedRkey(access.keys, collection, rkey)
            var current = item
            var base = swapStore.get(access.scope, collection, rkey)
            repeat(MAX_WRITE_ATTEMPTS) {
                val record = recordOf(current) ?: return 0
                val swap = base?.let { RecordSwap.Matching(it.cid) } ?: RecordSwap.Absent
                val sealed = SealedRecordCodec.seal(access.keys, access.space, access.author, SealedPayload(collection, record, rkey))
                val written =
                    client.call("putRecord($collection)") {
                        putRecord(access.space, sealed.rkey, sealed.record, kind.mapper.blobCids(current), swap)
                    }
                val stored = written.getOrNull()
                if (stored != null) {
                    swapStore.put(access.scope, SpaceRecordState(collection, rkey, sealedRkey, stored.cid, stored.seq))
                    kind.local.acknowledgeUpload(current, stored.seq, Clock.System.now())
                    retryCoordinator.markUploadSettled(type, pending, Clock.System.now(), stored.seq)
                    recordProgress(1)
                    return 1
                }
                val failure = written.exceptionOrNull() ?: IllegalStateException("The write returned nothing")
                if (failure !is DataHomeConflictException) return fail(failure)
                when (val next = resolve(current, rkey, sealedRkey, failure)) {
                    is Next.Write -> {
                        current = next.item
                        base = next.base
                    }
                    is Next.Done -> return 0
                }
            }
            return fail(DataHomeConflictException("The record kept changing on other devices"))
        }

        suspend fun delete(afterDelete: suspend (PendingUpload) -> Unit): Int {
            val rkey = kind.mapper.rkeyForEntity(pending.entityId)
            val known = swapStore.get(access.scope, collection, rkey)
            val sealedRkey = known?.sealedRkey ?: SealedRecordCodec.sealedRkey(access.keys, collection, rkey)
            // A deletion is the latest thing this device asked for, so it removes whatever version is there.
            var swap: RecordSwap = known?.let { RecordSwap.Matching(it.cid) } ?: RecordSwap.Unconditional
            repeat(MAX_WRITE_ATTEMPTS) {
                val deleted = client.call("deleteRecord($collection)") { deleteRecord(access.space, sealedRkey, swap) }
                val seq = deleted.getOrNull()
                if (seq != null) return deleted(rkey, seq, afterDelete)
                val failure = deleted.exceptionOrNull() ?: IllegalStateException("The deletion returned nothing")
                if (failure !is DataHomeConflictException) return fail(failure)
                val remote = client.call("getRecord($collection)") { getRecord(access.space, sealedRkey) }.getOrElse { return fail(it) }
                swap = remote?.let { RecordSwap.Matching(it.cid) } ?: RecordSwap.Unconditional
            }
            return fail(DataHomeConflictException("The record kept changing on other devices"))
        }

        private suspend fun deleted(
            rkey: String,
            seq: Long,
            afterDelete: suspend (PendingUpload) -> Unit,
        ): Int {
            swapStore.delete(access.scope, collection, rkey)
            afterDelete(pending)
            retryCoordinator.markUploadSettled(type, pending, Clock.System.now(), seq)
            recordProgress(1)
            return 1
        }

        /** Reads the version another device wrote and lets the kind's resolver decide what to write. */
        private suspend fun resolve(
            local: T,
            rkey: String,
            sealedRkey: String,
            conflict: DataHomeConflictException,
        ): Next<T> {
            val remote =
                client
                    .call(
                        "getRecord($collection)",
                    ) { getRecord(access.space, sealedRkey) }
                    .getOrElse { return Next.Done(fail(it)) }
            if (remote == null) {
                // Deleted elsewhere while this device edited it: kept here, and listed for review.
                swapStore.delete(access.scope, collection, rkey)
                errors += retryCoordinator.handleUploadConflict(type, pending, conflict, 0L, kind.local.lastUpdatedOf(local))
                return Next.Done(0)
            }
            val remoteItem = open(remote, rkey) ?: return Next.Done(0)
            val remoteBase = SpaceRecordState(collection, rkey, sealedRkey, remote.cid, remote.seq)
            val resolution =
                kind.local.conflictResolver.resolve(
                    local = local,
                    remote = remoteItem,
                    localTimestamp = kind.local.lastUpdatedOf(local),
                    remoteTimestamp = kind.local.lastUpdatedOf(remoteItem),
                )
            return when (resolution) {
                is ConflictResolution.KeepLocal -> Next.Write(resolution.value, remoteBase)
                is ConflictResolution.Merge -> {
                    kind.local.replaceLocal(resolution.merged)
                    Next.Write(resolution.merged, remoteBase)
                }
                is ConflictResolution.KeepRemote -> {
                    kind.local.replaceLocal(resolution.value)
                    swapStore.put(access.scope, remoteBase)
                    kind.local.acknowledgeUpload(resolution.value, remote.seq, Clock.System.now())
                    retryCoordinator.markUploadSettled(type, pending, Clock.System.now(), remote.seq)
                    Next.Done(0)
                }
                is ConflictResolution.RequiresManualResolution -> {
                    errors += retryCoordinator.handleUploadConflict(type, pending, conflict, remote.seq, kind.local.lastUpdatedOf(local))
                    Next.Done(0)
                }
            }
        }

        /** The item [remote] holds, or null after reporting why it can't be read. */
        private suspend fun open(
            remote: StoredRecord,
            rkey: String,
        ): T? {
            val item =
                try {
                    read(remote, rkey)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    fail(error)
                    return null
                }
            if (item == null) fail(SpaceRecordInvalidException("The stored $collection record doesn't match its schema"))
            return item
        }

        private suspend fun read(
            remote: StoredRecord,
            rkey: String,
        ): T? {
            val payload = SealedRecordCodec.open(access.keys, access.space, access.author, remote.rkey, remote.record)
            val record = payload.record as? JsonObject ?: return null
            if (payload.collection != collection || payload.rkey != rkey || issues(record).isNotEmpty()) return null
            return kind.mapper.fromRecord(rkey, record, remote.seq)
        }

        private suspend fun recordOf(item: T): JsonObject? {
            val record = kind.mapper.toRecord(item)
            val problems = issues(record)
            if (problems.isEmpty()) return record
            // Paths only: an issue's message can quote the record's contents.
            val paths = problems.joinToString { it.path.ifEmpty { "(record)" } }
            failPermanently(SpaceRecordInvalidException("The $collection record this device built fails its schema at $paths"))
            return null
        }

        private fun issues(record: JsonObject) = validator.validateRecord(Nsid.require(collection), record).issues

        private suspend fun fail(error: Throwable): Int {
            val movedToDeadLetter = retryCoordinator.handleRetryFailure(type, pending, error, permanent = error.isPermanentSpaceFailure())
            errors +=
                SyncError(
                    error.spaceErrorType(),
                    "Failed to upload $type to the personal space: ${error.message}",
                    error,
                    !movedToDeadLetter,
                )
            Napier.w("Personal space upload failed")
            return 0
        }

        private suspend fun failPermanently(error: SpaceRecordInvalidException) {
            retryCoordinator.handleRetryFailure(type, pending, error, permanent = true)
            errors += SyncError(SyncErrorType.UNKNOWN_ERROR, error.message ?: "Invalid record", error, retryable = false)
        }
    }

    private sealed interface Next<out T> {
        class Write<T>(
            val item: T,
            val base: SpaceRecordState,
        ) : Next<T>

        class Done(
            val uploaded: Int,
        ) : Next<Nothing>
    }

    private companion object {
        /** Writes before giving up on a record other devices keep changing; the entry is retried later. */
        const val MAX_WRITE_ATTEMPTS = 3
    }
}
