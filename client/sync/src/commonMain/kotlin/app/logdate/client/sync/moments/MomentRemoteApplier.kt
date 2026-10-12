package app.logdate.client.sync.moments

import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.repository.moments.atMillisecondPrecision
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Applies a retained cloud mark and its exact-version receipt in the same transaction. */
class MomentRemoteApplier(
    private val repository: SyncableMarkedMomentRepository,
    private val metadata: SyncMetadataService,
    private val transactions: SyncTransactionManager,
    private val inbox: DownloadInbox,
) {
    suspend fun apply(
        remote: MarkedMoment,
        scope: DownloadScope,
    ): Boolean =
        transactions.withTransaction {
            checkScope(scope)
            val id = remote.id.toString()
            if (!inbox.isCurrentVersion(EntityType.MOMENT.name, id, remote.syncVersion, scope)) return@withTransaction false
            val incoming = remote.atMillisecondPrecision()
            val merged = repository.applyFromSync(incoming)
            checkScope(scope)
            if (merged == null) {
                inbox.failed(EntityType.MOMENT.name, id, DiagnosticReason.CONFLICT.name, remote.syncVersion, scope)
                checkScope(scope)
                return@withTransaction false
            }
            // Version is transport metadata. Only surviving local content needs a return upload.
            if (merged.copy(syncVersion = incoming.syncVersion) != incoming && !metadata.hasPending(EntityType.MOMENT, id)) {
                metadata.enqueuePending(id, EntityType.MOMENT, PendingOperation.UPDATE)
            }
            checkScope(scope)
            inbox.applied(EntityType.MOMENT.name, id, remote.syncVersion, scope, enqueueMedia = false)
            checkScope(scope)
            true
        }

    suspend fun delete(
        id: Uuid,
        deletedAt: Instant,
        version: Long,
        scope: DownloadScope,
    ): Boolean =
        transactions.withTransaction {
            checkScope(scope)
            if (!inbox.isCurrentDeletionVersion(EntityType.MOMENT.name, id.toString(), version, scope)) return@withTransaction false
            repository.applyDeletionFromSync(id, deletedAt, version)
            checkScope(scope)
            inbox.applied(EntityType.MOMENT.name, id.toString(), version, scope, enqueueMedia = false)
            checkScope(scope)
            true
        }

    private fun checkScope(scope: DownloadScope) {
        check(scope == inbox.currentScope()) { "Moment download scope changed" }
    }
}
