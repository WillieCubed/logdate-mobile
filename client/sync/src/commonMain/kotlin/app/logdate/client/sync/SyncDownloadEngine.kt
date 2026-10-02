package app.logdate.client.sync

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
)

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
        if (unreadable.isEmpty()) return
        val (repairable, cloudOnly) = unreadable.partition { it in heldLocally }
        for (id in repairable) {
            ensureScope(selected)
            val version = observedVersions[id]
            if (selected != null && version == null) continue
            syncMetadataService.enqueueRepairIfAbsent(id.toString(), entityType, version, selected?.origin)
        }
        if (cloudOnly.isNotEmpty()) {
            unreadableCloudRecordStore.record(entityType, cloudOnly)
        }
        Napier.w(
            "${repairable.size} unreadable $logLabel(s) queued to re-upload from this device; " +
                "${cloudOnly.size} exist only in the cloud and were left in place",
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
            syncMetadataService.getPendingUploads(strategy.entityType)
            val localById = strategy.localItems().toMutableMap()

            var cursor = downloadInbox?.cursor(strategy.entityType.name)?.let(Instant::fromEpochMilliseconds) ?: since
            var hasMore = true
            var totalDownloaded = 0
            var totalConflicts = 0
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
                val fetchFailure = downloadInbox?.fetchFailure(strategy.entityType.name)
                if (fetchFailure != null && fetchFailure != DiagnosticReason.NONE) {
                    errors.add(
                        SyncError(
                            if (fetchFailure ==
                                DiagnosticReason.SIGN_IN_REQUIRED
                            ) {
                                SyncErrorType.AUTHENTICATION_ERROR
                            } else {
                                SyncErrorType.SERVER_ERROR
                            },
                            "Remote fetch failed; saved records are still being recovered",
                        ),
                    )
                }
                val rows = downloadInbox?.pending(strategy.entityType.name).orEmpty().associateBy { it.entityId }
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
                val failedIds = mutableSetOf<String>()

                if (downloadScope != null) check(downloadScope == downloadInbox?.currentScope()) { "Download scope changed" }
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
                                report(
                                    SyncDiagnosticEvent(
                                        DiagnosticPhase.APPLY,
                                        DiagnosticOutcome.STARTED,
                                        operationId = diagnosticRow.operationId,
                                        attemptId = attemptId,
                                        attemptCount = diagnosticRow.attempts + 1,
                                    ),
                                    source,
                                )
                            }
                            val previous = localById[recordId]
                            val downloadedBefore = downloadedCount
                            val conflictsBefore = conflictsResolved
                            try {
                                transactionManager.withTransaction {
                                    ensureScope(downloadScope)
                                    run record@{
                                        val item = hydration.getOrThrow()
                                        val id = strategy.idOf(item)
                                        val existing = strategy.localItem(id)
                                        val pendingLocal = syncMetadataService.hasPending(strategy.entityType, id.toString())

                                        if (existing == null && pendingLocal) {
                                            skippedIds += id.toString()
                                            return@record
                                        }
                                        if (existing != null &&
                                            strategy.syncVersionOf(existing) > 0 &&
                                            strategy.syncVersionOf(existing) >= strategy.syncVersionOf(item)
                                        ) {
                                            if (existing != item || pendingLocal) skippedIds += id.toString()
                                            return@record
                                        }
                                        if (existing != null) {
                                            val hasPendingLocal = pendingLocal
                                            if (hasPendingLocal) {
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
                                    rows[recordId.toString()]?.let { row ->
                                        downloadInbox?.applied(
                                            strategy.entityType.name,
                                            row.entityId,
                                            row.version,
                                            requireNotNull(downloadScope),
                                            enqueueMedia =
                                                row.entityId !in skippedIds,
                                        )
                                    }
                                    ensureScope(downloadScope)
                                }
                                appliedIds += recordId.toString()
                                if (diagnosticRow != null) {
                                    report(
                                        SyncDiagnosticEvent(
                                            DiagnosticPhase.APPLY,
                                            if (recordId.toString() in
                                                skippedIds
                                            ) {
                                                DiagnosticOutcome.CONFLICT
                                            } else {
                                                DiagnosticOutcome.SUCCEEDED
                                            },
                                            reason =
                                                if (recordId.toString() in
                                                    skippedIds
                                                ) {
                                                    DiagnosticReason.CONFLICT
                                                } else {
                                                    DiagnosticReason.NONE
                                                },
                                            action =
                                                if (recordId.toString() in
                                                    skippedIds
                                                ) {
                                                    DiagnosticAction.REVIEW_CONFLICT
                                                } else {
                                                    DiagnosticAction.NONE
                                                },
                                            operationId = diagnosticRow.operationId,
                                            attemptId = attemptId,
                                            attemptCount = diagnosticRow.attempts + 1,
                                        ),
                                        source,
                                    )
                                }
                            } catch (e: Exception) {
                                if (previous == null) localById.remove(recordId) else localById[recordId] = previous
                                downloadedCount = downloadedBefore
                                conflictsResolved = conflictsBefore
                                if (e is CancellationException) {
                                    if (diagnosticRow != null) {
                                        report(
                                            SyncDiagnosticEvent(
                                                DiagnosticPhase.APPLY,
                                                DiagnosticOutcome.INTERRUPTED,
                                                operationId = diagnosticRow.operationId,
                                                attemptId = attemptId,
                                                attemptCount = diagnosticRow.attempts + 1,
                                            ),
                                            source,
                                        )
                                    }
                                    throw e
                                }
                                if (diagnosticRow != null) {
                                    report(
                                        SyncDiagnosticEvent(
                                            DiagnosticPhase.APPLY,
                                            DiagnosticOutcome.FAILED,
                                            reason = DiagnosticReason.LOCAL_STORAGE,
                                            action = DiagnosticAction.RETRY,
                                            operationId = diagnosticRow.operationId,
                                            attemptId = attemptId,
                                            attemptCount = diagnosticRow.attempts + 1,
                                            retryable = true,
                                        ),
                                        source,
                                    )
                                }
                                failedIds += recordId.toString()
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
                                report(
                                    SyncDiagnosticEvent(
                                        DiagnosticPhase.APPLY,
                                        DiagnosticOutcome.STARTED,
                                        operationId = diagnosticRow.operationId,
                                        attemptId = attemptId,
                                        attemptCount = diagnosticRow.attempts + 1,
                                    ),
                                    source,
                                )
                            }
                            val previous = localById[id]
                            val downloadedBefore = downloadedCount
                            val conflictsBefore = conflictsResolved
                            try {
                                transactionManager.withTransaction {
                                    ensureScope(downloadScope)
                                    run deletion@{
                                        val existing = strategy.localItem(id)
                                        val deletionVersion = rows[id.toString()]?.version
                                        if (existing != null &&
                                            deletionVersion != null &&
                                            strategy.syncVersionOf(existing) > deletionVersion
                                        ) {
                                            skippedIds += id.toString()
                                            return@deletion
                                        }
                                        val hasPendingLocal =
                                            existing != null && syncMetadataService.hasPending(strategy.entityType, id.toString())

                                        if (hasPendingLocal) {
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
                                    report(
                                        SyncDiagnosticEvent(
                                            DiagnosticPhase.APPLY,
                                            if (id.toString() in skippedIds) DiagnosticOutcome.CONFLICT else DiagnosticOutcome.SUCCEEDED,
                                            reason = if (id.toString() in skippedIds) DiagnosticReason.CONFLICT else DiagnosticReason.NONE,
                                            action =
                                                if (id.toString() in
                                                    skippedIds
                                                ) {
                                                    DiagnosticAction.REVIEW_CONFLICT
                                                } else {
                                                    DiagnosticAction.NONE
                                                },
                                            operationId = diagnosticRow.operationId,
                                            attemptId = attemptId,
                                            attemptCount = diagnosticRow.attempts + 1,
                                        ),
                                        source,
                                    )
                                }
                            } catch (e: Exception) {
                                if (previous == null) localById.remove(id) else localById[id] = previous
                                downloadedCount = downloadedBefore
                                conflictsResolved = conflictsBefore
                                if (e is CancellationException) {
                                    if (diagnosticRow != null) {
                                        report(
                                            SyncDiagnosticEvent(
                                                DiagnosticPhase.APPLY,
                                                DiagnosticOutcome.INTERRUPTED,
                                                operationId = diagnosticRow.operationId,
                                                attemptId = attemptId,
                                                attemptCount = diagnosticRow.attempts + 1,
                                            ),
                                            source,
                                        )
                                    }
                                    throw e
                                }
                                if (diagnosticRow != null) {
                                    report(
                                        SyncDiagnosticEvent(
                                            DiagnosticPhase.APPLY,
                                            DiagnosticOutcome.FAILED,
                                            reason = DiagnosticReason.LOCAL_STORAGE,
                                            action = DiagnosticAction.RETRY,
                                            operationId = diagnosticRow.operationId,
                                            attemptId = attemptId,
                                            attemptCount = diagnosticRow.attempts + 1,
                                            retryable = true,
                                        ),
                                        source,
                                    )
                                }
                                failedIds += id.toString()
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
                            downloadInbox?.failed(
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
                        downloadInbox?.failed(
                            strategy.entityType.name,
                            failure.entityId,
                            failure.reason.name,
                            failure.serverVersion,
                            downloadScope,
                            source = source,
                        )
                    }
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
                    break
                }

                if (page.lastSyncTimestamp <= cursor) {
                    Napier.w(
                        "Remote pagination did not advance",
                    )
                    break
                }

                cursor = page.lastSyncTimestamp
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = totalDownloaded,
                conflictsResolved = totalConflicts,
                errors = errors,
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
}
