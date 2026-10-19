package app.logdate.client.sync

import app.logdate.client.database.entities.sync.DownloadInboxEntity
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.RemoteRecordFailure
import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.diagnostics.DiagnosticSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.InMemoryUnreadableCloudRecordStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UnreadableCloudRecordStore
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.shared.model.diagnostics.DiagnosticAction
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Data class to hold batch operation results within transactions.
 * Allows transactional batch applies to return result data that is used
 * after the transaction commits.
 */
internal data class BatchResult(
    val downloadedCount: Int,
    val conflictsResolved: Int,
    val errors: List<SyncError>,
)

/** The shape [app.logdate.client.sync.cloud.CloudJournalDataSource.getJournalChanges]/[app.logdate.client.sync.cloud.CloudContentDataSource.getContentChanges] share. */
internal data class ChangesPage<T>(
    val changes: List<T>,
    val deletions: List<Uuid>,
    val lastSyncTimestamp: Instant,
    val hasMore: Boolean,
    val unreadable: List<Uuid> = emptyList(),
    val failures: List<RemoteRecordFailure> = emptyList(),
    val unreadableVersions: Map<Uuid, Long> = emptyMap(),
)

/**
 * Everything [SyncDownloadEngine] needs to know about one [EntityType] to run the shared
 * download-paginate-resolve-conflicts sequence: how to fetch a page, read an item's identity
 * and versioning, resolve a conflict, and apply the outcome locally.
 */
internal class DownloadStrategy<T : Any>(
    val entityType: EntityType,
    val logLabel: String,
    val fetchChanges: suspend (accessToken: String, cursor: Instant) -> Result<ChangesPage<T>>,
    val localItems: suspend () -> Map<Uuid, T>,
    val idOf: (T) -> Uuid,
    val syncVersionOf: (T) -> Long,
    val lastUpdatedOf: (T) -> Instant,
    val conflictResolver: ConflictResolver<T>,
    val applyCreate: suspend (T) -> Unit,
    val applyReplace: suspend (existing: T, replacement: T) -> Unit,
    val applyDelete: suspend (Uuid) -> Unit,
    val hydrate: suspend (accessToken: String, item: T) -> T = { _, item -> item },
    val afterDelete: suspend (Uuid) -> Unit = {},
    val localItem: suspend (Uuid) -> T? = { id -> localItems()[id] },
    val sameUploadedFields: suspend (local: T, remote: T) -> Boolean = { _, _ -> false },
    val sameCreateBaseFields: suspend (local: T, remote: T) -> Boolean = { _, _ -> false },
    val acknowledgeUpload: suspend (local: T, remote: T) -> Boolean = { _, _ -> false },
    /** Whether a record only the server has is stored here; a record already here is always updated. */
    val appliesRemoteCreate: (T) -> Boolean = { true },
    /** Whether an applied record's media is queued for download; off where media is fetched on demand. */
    val enqueuesMediaRecovery: Boolean = true,
    /** The download checkpoint whose fetch failure this download reports. */
    val checkpoint: String = entityType.name,
    /**
     * Settles the records behind each remote item inside the transaction that applied it, in place of
     * the inbox row under the item's id. A personal-space read records the versions it applied here.
     */
    val settle: (suspend (id: Uuid, outcome: RemoteOutcome) -> Unit)? = null,
    /** Reports an item that failed to apply, for a [settle] strategy. */
    val settleFailed: suspend (id: Uuid) -> Unit = {},
    /**
     * Whether a remote change to an item with no local change waiting is applied as it is. The
     * personal space only offers changes made since this device last applied the item, so with
     * nothing changed here there is nothing to resolve; a local change still waiting is resolved
     * when it uploads, against the version it then finds.
     */
    val appliesRemoteOverUnchangedLocal: Boolean = false,
)

/** What became of one remote item or deletion. */
internal enum class RemoteOutcome {
    /** The remote version is now the local one. */
    STORED,

    /** The resolver merged it into the local copy, which is queued to upload. */
    MERGED,

    /** The resolver kept the local copy, which is queued to upload over it. */
    KEPT_LOCAL,

    /** Not applied: a local change waits, a newer copy is here, the conflict awaits review, or this device doesn't keep it. */
    DECLINED,

    /** The deletion was applied. */
    DELETED,
}

/**
 * Runs the download-paginate-resolve-conflicts sequence shared by every [EntityType] whose
 * download-side sync fits a [DownloadStrategy]: an item that only exists remotely is created, one
 * that exists both places goes through [DownloadStrategy.conflictResolver] unless a local edit is
 * already queued for it (in which case the remote side loses and the conflict is just recorded for
 * review), and a remote deletion is applied unless a local edit is queued against it too.
 * Associations (additions/deletions, not changes/deletions) and drafts (a simpler newer-wins
 * heuristic instead of a pluggable [ConflictResolver]) don't fit this shape and are downloaded by
 * their own functions instead of being forced through this engine.
 *
 * @param mapCloudApiError,mapException Reuse [DefaultSyncManager]'s own error mapping (including
 *   its [lastErrorFlow][DefaultSyncManager] side effect) instead of duplicating it here, so a
 *   download failure is reported identically to an upload failure.
 */
internal class SyncDownloadEngine(
    private val transactionManager: SyncTransactionManager,
    private val syncMetadataService: SyncMetadataService,
    private val conflictStore: SyncConflictStore,
    private val mapCloudApiError: (CloudApiException) -> SyncResult,
    private val mapException: (Exception, String) -> SyncResult,
    private val unreadableCloudRecordStore: UnreadableCloudRecordStore = InMemoryUnreadableCloudRecordStore(),
    private val downloadInbox: DownloadInbox? = null,
    private val diagnosticSource: () -> DiagnosticSource? = { null },
    private val diagnostics: (SyncDiagnosticEvent, DiagnosticSource?) -> Unit = { _, _ -> },
) {
    private fun report(
        event: SyncDiagnosticEvent,
        source: DiagnosticSource?,
    ) {
        try {
            diagnostics(event, source)
        } catch (_: Exception) {
            // A diagnostic sink cannot change record application or cancellation.
        }
    }

    /** Clear an unreadable marker only after applying a recovered version successfully. */
    suspend fun markRecovered(
        entityType: EntityType,
        id: Uuid,
    ) = unreadableCloudRecordStore.resolved(entityType, id)

    suspend fun repairUnreadable(
        entityType: EntityType,
        logLabel: String,
        unreadable: List<Uuid>,
        heldLocally: Set<Uuid>,
        selected: app.logdate.client.sync.recovery.DownloadScope? = downloadInbox?.currentScope(),
        observedVersions: Map<Uuid, Long> = emptyMap(),
    ) {
        enqueueUnreadableRepairs(
            entityType,
            logLabel,
            unreadable,
            heldLocally,
            selected,
            observedVersions,
            syncMetadataService,
            unreadableCloudRecordStore,
            ::ensureScope,
        )
    }

    suspend fun <T : Any> download(
        strategy: DownloadStrategy<T>,
        accessToken: String,
        since: Instant,
    ): SyncResult =
        try {
            val downloadScope = downloadInbox?.currentScope()
            val source =
                try {
                    diagnosticSource().takeIf { captured ->
                        captured != null &&
                            (
                                downloadScope == null ||
                                    (captured.scope.ownerId == downloadScope.owner && captured.scope.serverOrigin == downloadScope.origin)
                            )
                    }
                } catch (_: Exception) {
                    null
                }
            // Promote legacy pending rows before entering any Room apply transaction.
            val pendingById = syncMetadataService.getPendingUploads(strategy.entityType).associateBy { it.entityId }
            val localById = strategy.localItems().toMutableMap()

            var cursor = downloadInbox?.cursor(strategy.entityType.name)?.let(Instant::fromEpochMilliseconds) ?: since
            var hasMore = true
            var totalDownloaded = 0
            var totalConflicts = 0
            var remoteInventoryComplete = false
            val errors = mutableListOf<SyncError>()

            while (hasMore) {
                val page =
                    strategy.fetchChanges(accessToken, cursor).getOrElse { fetchError ->
                        if (fetchError is CancellationException) throw fetchError
                        errors.add(SyncError(SyncErrorType.SERVER_ERROR, "Failed to fetch remote changes"))
                        Napier.e("Failed to fetch remote changes")
                        return SyncResult(
                            success = false,
                            downloadedItems = totalDownloaded,
                            conflictsResolved = totalConflicts,
                            errors = errors,
                        )
                    }
                val fetchFailure = downloadInbox?.fetchFailure(strategy.checkpoint)
                if (fetchFailure != null && fetchFailure != DiagnosticReason.NONE) {
                    errors.add(
                        SyncError(
                            when (fetchFailure) {
                                DiagnosticReason.SIGN_IN_REQUIRED -> SyncErrorType.AUTHENTICATION_ERROR
                                DiagnosticReason.OFFLINE -> SyncErrorType.NETWORK_ERROR
                                else -> SyncErrorType.SERVER_ERROR
                            },
                            "Remote fetch failed; saved records are still being recovered",
                        ),
                    )
                }
                val rows =
                    if (strategy.settle != null) {
                        emptyMap()
                    } else {
                        downloadInbox?.pending(strategy.entityType.name).orEmpty().associateBy { it.entityId }
                    }
                val hydrated =
                    page.changes.map { remote ->
                        val result =
                            try {
                                Result.success(strategy.hydrate(accessToken, remote))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Result.failure(error)
                            }
                        remote to result
                    }
                val appliedIds = mutableSetOf<String>()
                val skippedIds = mutableSetOf<String>()
                val outsideWindowIds = mutableSetOf<String>()
                val failedIds = mutableSetOf<String>()

                if (downloadScope != null) check(downloadScope == downloadInbox.currentScope()) { "Download scope changed" }
                val batchResult =
                    run {
                        var downloadedCount = 0
                        var conflictsResolved = 0
                        val batchErrors = mutableListOf<SyncError>()

                        for ((remoteItem, hydration) in hydrated) {
                            val recordId = strategy.idOf(remoteItem)
                            val diagnosticRow = rows[recordId.toString()]
                            val attemptId = if (diagnosticRow != null) Uuid.random().toString() else null
                            if (diagnosticRow != null) {
                                report(applyEvent(diagnosticRow, attemptId, DiagnosticOutcome.STARTED), source)
                            }
                            val previous = localById[recordId]
                            val downloadedBefore = downloadedCount
                            val conflictsBefore = conflictsResolved
                            try {
                                transactionManager.withTransaction {
                                    ensureScope(downloadScope)
                                    var outcome = RemoteOutcome.STORED
                                    run record@{
                                        val item = hydration.getOrThrow()
                                        val id = strategy.idOf(item)
                                        val existing = strategy.localItem(id)
                                        val pendingLocal = syncMetadataService.hasPending(strategy.entityType, id.toString())

                                        if (existing != null &&
                                            acknowledgeUploadedOperation(
                                                strategy,
                                                existing,
                                                remoteItem,
                                                pendingById[id.toString()],
                                                syncMetadataService,
                                            )
                                        ) {
                                            localById[id] = strategy.localItem(id) ?: existing
                                            return@record
                                        }

                                        if (existing == null && pendingLocal) {
                                            skippedIds += id.toString()
                                            outcome = RemoteOutcome.DECLINED
                                            return@record
                                        }
                                        if (existing == null && !strategy.appliesRemoteCreate(item)) {
                                            outsideWindowIds += id.toString()
                                            outcome = RemoteOutcome.DECLINED
                                            return@record
                                        }
                                        if (existing != null &&
                                            strategy.syncVersionOf(existing) > 0 &&
                                            strategy.syncVersionOf(existing) >= strategy.syncVersionOf(item)
                                        ) {
                                            if (existing != item || pendingLocal) skippedIds += id.toString()
                                            outcome = RemoteOutcome.DECLINED
                                            return@record
                                        }
                                        if (existing != null) {
                                            val hasPendingLocal = pendingLocal
                                            if (hasPendingLocal) {
                                                outcome = RemoteOutcome.DECLINED
                                                skippedIds += id.toString()
                                                conflictsResolved++
                                                Napier.w("Remote update conflicts with pending local work")
                                                recordConflict(
                                                    entityType = strategy.entityType,
                                                    entityId = id.toString(),
                                                    reason = "Local pending changes vs remote update",
                                                    localVersion = strategy.syncVersionOf(existing),
                                                    remoteVersion = strategy.syncVersionOf(item),
                                                    localUpdatedAt = strategy.lastUpdatedOf(existing),
                                                    remoteUpdatedAt = strategy.lastUpdatedOf(item),
                                                )
                                                return@record
                                            }

                                            if (strategy.appliesRemoteOverUnchangedLocal) {
                                                strategy.applyReplace(existing, item)
                                                localById[id] = item
                                                downloadedCount++
                                                return@record
                                            }
                                            val (localTimestamp, remoteTimestamp) =
                                                conflictTimestamps(
                                                    localSyncVersion = strategy.syncVersionOf(existing),
                                                    localUpdatedAt = strategy.lastUpdatedOf(existing),
                                                    remoteSyncVersion = strategy.syncVersionOf(item),
                                                    remoteUpdatedAt = strategy.lastUpdatedOf(item),
                                                )
                                            val resolution =
                                                strategy.conflictResolver.resolve(
                                                    local = existing,
                                                    remote = item,
                                                    localTimestamp = localTimestamp,
                                                    remoteTimestamp = remoteTimestamp,
                                                )

                                            when (resolution) {
                                                is ConflictResolution.KeepRemote -> {
                                                    strategy.applyReplace(existing, resolution.value)
                                                    localById[id] = resolution.value
                                                    conflictsResolved++
                                                    Napier.d("Conflict resolved using remote version")
                                                }
                                                is ConflictResolution.KeepLocal -> {
                                                    outcome = RemoteOutcome.KEPT_LOCAL
                                                    // Keeping the local copy is only half the resolution:
                                                    // without re-queueing it the server keeps its own
                                                    // version, the next download resolves the same conflict
                                                    // the same way, and the two never converge.
                                                    syncMetadataService.enqueuePending(
                                                        entityId = id.toString(),
                                                        entityType = strategy.entityType,
                                                        operation = PendingOperation.UPDATE,
                                                    )
                                                    conflictsResolved++
                                                    Napier.d("Conflict resolved using local version")
                                                }
                                                is ConflictResolution.Merge -> {
                                                    outcome = RemoteOutcome.MERGED
                                                    strategy.applyReplace(existing, resolution.merged)
                                                    syncMetadataService.enqueuePending(
                                                        entityId = id.toString(),
                                                        entityType = strategy.entityType,
                                                        operation = PendingOperation.UPDATE,
                                                    )
                                                    localById[id] = resolution.merged
                                                    conflictsResolved++
                                                    Napier.d("Conflict resolved by merging")
                                                }
                                                is ConflictResolution.RequiresManualResolution -> {
                                                    outcome = RemoteOutcome.DECLINED
                                                    Napier.w(
                                                        "Conflict requires review",
                                                    )
                                                    recordConflict(
                                                        entityType = strategy.entityType,
                                                        entityId = id.toString(),
                                                        reason = resolution.reason,
                                                        localVersion = strategy.syncVersionOf(existing),
                                                        remoteVersion = strategy.syncVersionOf(item),
                                                        localUpdatedAt = strategy.lastUpdatedOf(existing),
                                                        remoteUpdatedAt = strategy.lastUpdatedOf(item),
                                                    )
                                                }
                                            }
                                        } else {
                                            strategy.applyCreate(item)
                                            localById[id] = item
                                            downloadedCount++
                                            Napier.d("Remote record applied")
                                        }
                                    }
                                    ensureScope(downloadScope)
                                    strategy.settle?.invoke(recordId, outcome)
                                    rows[recordId.toString()]?.let { row ->
                                        downloadInbox?.applied(
                                            strategy.entityType.name,
                                            row.entityId,
                                            row.version,
                                            requireNotNull(downloadScope),
                                            enqueueMedia =
                                                strategy.enqueuesMediaRecovery &&
                                                    row.entityId !in skippedIds &&
                                                    row.entityId !in outsideWindowIds,
                                        )
                                    }
                                    ensureScope(downloadScope)
                                }
                                appliedIds += recordId.toString()
                                if (diagnosticRow != null) {
                                    report(appliedEvent(diagnosticRow, attemptId, conflicted = recordId.toString() in skippedIds), source)
                                }
                            } catch (e: Exception) {
                                if (previous == null) localById.remove(recordId) else localById[recordId] = previous
                                downloadedCount = downloadedBefore
                                conflictsResolved = conflictsBefore
                                if (e is CancellationException) {
                                    if (diagnosticRow != null) {
                                        report(applyEvent(diagnosticRow, attemptId, DiagnosticOutcome.INTERRUPTED), source)
                                    }
                                    throw e
                                }
                                if (diagnosticRow != null) {
                                    val failure =
                                        applyEvent(
                                            diagnosticRow,
                                            attemptId,
                                            DiagnosticOutcome.FAILED,
                                            DiagnosticReason.LOCAL_STORAGE,
                                            DiagnosticAction.RETRY,
                                            retryable = true,
                                        )
                                    report(failure, source)
                                }
                                failedIds += recordId.toString()
                                if (strategy.settle != null) strategy.settleFailed(recordId)
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to apply remote change",
                                    ),
                                )
                                Napier.e("Failed to apply remote change")
                            }
                        }

                        for (id in page.deletions) {
                            val diagnosticRow = rows[id.toString()]
                            val attemptId = if (diagnosticRow != null) Uuid.random().toString() else null
                            if (diagnosticRow != null) {
                                report(applyEvent(diagnosticRow, attemptId, DiagnosticOutcome.STARTED), source)
                            }
                            val previous = localById[id]
                            val downloadedBefore = downloadedCount
                            val conflictsBefore = conflictsResolved
                            try {
                                transactionManager.withTransaction {
                                    ensureScope(downloadScope)
                                    var outcome = RemoteOutcome.DELETED
                                    run deletion@{
                                        val existing = strategy.localItem(id)
                                        val deletionVersion = rows[id.toString()]?.version
                                        if (existing != null &&
                                            deletionVersion != null &&
                                            strategy.syncVersionOf(existing) > deletionVersion
                                        ) {
                                            skippedIds += id.toString()
                                            outcome = RemoteOutcome.DECLINED
                                            return@deletion
                                        }
                                        val hasPendingLocal =
                                            existing != null && syncMetadataService.hasPending(strategy.entityType, id.toString())

                                        if (hasPendingLocal) {
                                            outcome = RemoteOutcome.DECLINED
                                            val item = requireNotNull(existing)
                                            conflictsResolved++
                                            Napier.w("Remote deletion conflicts with pending local work")
                                            recordConflict(
                                                entityType = strategy.entityType,
                                                entityId = id.toString(),
                                                reason = "Local pending changes vs remote deletion",
                                                localVersion = strategy.syncVersionOf(item),
                                                remoteVersion = null,
                                                localUpdatedAt = strategy.lastUpdatedOf(item),
                                                remoteUpdatedAt = null,
                                            )
                                            return@deletion
                                        }

                                        strategy.applyDelete(id)
                                        strategy.afterDelete(id)
                                        localById.remove(id)
                                        downloadedCount++
                                        Napier.d("Remote deletion applied")
                                    }
                                    ensureScope(downloadScope)
                                    strategy.settle?.invoke(id, outcome)
                                    rows[id.toString()]?.let { row ->
                                        downloadInbox?.applied(
                                            strategy.entityType.name,
                                            row.entityId,
                                            row.version,
                                            requireNotNull(downloadScope),
                                            enqueueMedia = false,
                                        )
                                    }
                                    ensureScope(downloadScope)
                                }
                                appliedIds += id.toString()
                                if (diagnosticRow != null) {
                                    report(appliedEvent(diagnosticRow, attemptId, conflicted = id.toString() in skippedIds), source)
                                }
                            } catch (e: Exception) {
                                if (previous == null) localById.remove(id) else localById[id] = previous
                                downloadedCount = downloadedBefore
                                conflictsResolved = conflictsBefore
                                if (e is CancellationException) {
                                    if (diagnosticRow != null) {
                                        report(applyEvent(diagnosticRow, attemptId, DiagnosticOutcome.INTERRUPTED), source)
                                    }
                                    throw e
                                }
                                if (diagnosticRow != null) {
                                    val failure =
                                        applyEvent(
                                            diagnosticRow,
                                            attemptId,
                                            DiagnosticOutcome.FAILED,
                                            DiagnosticReason.LOCAL_STORAGE,
                                            DiagnosticAction.RETRY,
                                            retryable = true,
                                        )
                                    report(failure, source)
                                }
                                failedIds += id.toString()
                                if (strategy.settle != null) strategy.settleFailed(id)
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to apply remote deletion",
                                    ),
                                )
                                Napier.e("Failed to apply remote deletion")
                            }
                        }

                        BatchResult(downloadedCount, conflictsResolved, batchErrors)
                    }

                totalDownloaded += batchResult.downloadedCount
                totalConflicts += batchResult.conflictsResolved
                errors.addAll(batchResult.errors)
                repairUnreadable(
                    strategy.entityType,
                    strategy.logLabel,
                    page.unreadable,
                    localById.keys,
                    downloadScope,
                    page.unreadableVersions,
                )

                for (id in appliedIds) {
                    unreadableCloudRecordStore.resolved(strategy.entityType, Uuid.parse(id))
                }
                for (id in failedIds) {
                    rows[id]?.let {
                        downloadInbox?.failed(
                            strategy.entityType.name,
                            id,
                            DiagnosticReason.LOCAL_STORAGE.name,
                            it.version,
                            requireNotNull(downloadScope),
                            source = source,
                        )
                    }
                }
                for (id in page.unreadable) {
                    page.unreadableVersions[id]?.let { version ->
                        if (downloadScope != null) {
                            downloadInbox.failed(
                                strategy.entityType.name,
                                id.toString(),
                                DiagnosticReason.KEY_RECOVERY_REQUIRED.name,
                                version,
                                downloadScope,
                                source = source,
                            )
                        }
                    }
                }
                for (failure in page.failures) {
                    ensureScope(downloadScope)
                    if (downloadScope != null) {
                        downloadInbox.failed(
                            strategy.entityType.name,
                            failure.entityId,
                            failure.reason.name,
                            failure.serverVersion,
                            downloadScope,
                            source = source,
                        )
                    }
                    // Only an app update reads a record a newer version wrote. The inbox stores it
                    // and offers it again, so this sync has nothing left to finish. Without an inbox
                    // nothing stores it, and the page must stay unfinished.
                    if (failure.reason == DiagnosticReason.UNSUPPORTED_FORMAT && downloadScope != null) continue
                    errors += SyncError(SyncErrorType.UNKNOWN_ERROR, "Remote record remains queued for recovery")
                }
                // Without a durable inbox there is nowhere safe to retain failed records.
                if ((batchResult.errors.isNotEmpty() || page.unreadable.isNotEmpty() || page.failures.isNotEmpty()) &&
                    downloadInbox == null
                ) {
                    break
                }

                syncMetadataService.updateLastSyncTime(strategy.entityType, page.lastSyncTimestamp)

                if (!page.hasMore) {
                    ensureScope(downloadScope)
                    remoteInventoryComplete = fetchFailure == null || fetchFailure == DiagnosticReason.NONE
                    break
                }

                if (page.lastSyncTimestamp <= cursor) {
                    Napier.w(
                        "Remote pagination did not advance",
                    )
                    errors += SyncError(SyncErrorType.SERVER_ERROR, "Remote inventory remains incomplete", retryable = true)
                    break
                }

                cursor = page.lastSyncTimestamp
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = totalDownloaded,
                conflictsResolved = totalConflicts,
                errors = errors,
                remoteInventoryComplete = remoteInventoryComplete,
            )
        } catch (e: CloudApiException) {
            mapCloudApiError(e)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mapException(e, "Download ${strategy.logLabel}s")
        }

    private fun ensureScope(selected: app.logdate.client.sync.recovery.DownloadScope?) {
        if (selected != null && selected != downloadInbox?.currentScope()) throw CancellationException("Download scope changed")
    }

    /**
     * Also called directly by [DefaultSyncManager]'s `downloadDrafts`/`downloadAssociations` --
     * their conflict shape (additions/deletions, or a newer-wins heuristic) doesn't fit
     * [DownloadStrategy], but recording a conflict once it's detected is identical either way.
     */
    suspend fun recordConflict(
        entityType: EntityType,
        entityId: String,
        reason: String,
        localVersion: Long?,
        remoteVersion: Long?,
        localUpdatedAt: Instant?,
        remoteUpdatedAt: Instant?,
    ) {
        conflictStore.add(
            SyncConflictRecord(
                id = "${entityType.name}:$entityId",
                entityType = entityType.name,
                entityId = entityId,
                localVersion = localVersion,
                remoteVersion = remoteVersion,
                localUpdatedAt = localUpdatedAt?.toEpochMilliseconds(),
                remoteUpdatedAt = remoteUpdatedAt?.toEpochMilliseconds(),
                reason = reason,
                detectedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }
}

/** One step of applying a downloaded record, as the diagnostics see it; creates and deletions report the same steps. */
private fun applyEvent(
    row: DownloadInboxEntity,
    attemptId: String?,
    outcome: DiagnosticOutcome,
    reason: DiagnosticReason = DiagnosticReason.NONE,
    action: DiagnosticAction = DiagnosticAction.NONE,
    retryable: Boolean = false,
) = SyncDiagnosticEvent(
    DiagnosticPhase.APPLY,
    outcome,
    reason = reason,
    action = action,
    operationId = row.operationId,
    attemptId = attemptId,
    attemptCount = row.attempts + 1,
    retryable = retryable,
)

private fun appliedEvent(
    row: DownloadInboxEntity,
    attemptId: String?,
    conflicted: Boolean,
): SyncDiagnosticEvent =
    if (conflicted) {
        applyEvent(row, attemptId, DiagnosticOutcome.CONFLICT, DiagnosticReason.CONFLICT, DiagnosticAction.REVIEW_CONFLICT)
    } else {
        applyEvent(row, attemptId, DiagnosticOutcome.SUCCEEDED)
    }

private fun conflictTimestamps(
    localSyncVersion: Long,
    localUpdatedAt: Instant,
    remoteSyncVersion: Long,
    remoteUpdatedAt: Instant,
): Pair<Instant, Instant> =
    if (localSyncVersion > 0L && remoteSyncVersion > 0L) {
        Instant.fromEpochMilliseconds(localSyncVersion) to Instant.fromEpochMilliseconds(remoteSyncVersion)
    } else {
        localUpdatedAt to remoteUpdatedAt
    }
