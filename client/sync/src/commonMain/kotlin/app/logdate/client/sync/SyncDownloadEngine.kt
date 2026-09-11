package app.logdate.client.sync

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier
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
) {
    /** Consecutive failed pages per entity type, so a poison page cannot pin the feed forever. */
    private val consecutiveBatchFailures = mutableMapOf<EntityType, Int>()

    suspend fun <T : Any> download(
        strategy: DownloadStrategy<T>,
        accessToken: String,
        since: Instant,
    ): SyncResult =
        try {
            val pendingIds =
                syncMetadataService
                    .getPendingUploads(strategy.entityType)
                    .map { it.entityId }
                    .toSet()
            val localById = strategy.localItems().toMutableMap()

            var cursor = since
            var hasMore = true
            var totalDownloaded = 0
            var totalConflicts = 0
            val errors = mutableListOf<SyncError>()

            while (hasMore) {
                val page = strategy.fetchChanges(accessToken, cursor).getOrThrow()
                val hydratedChanges = page.changes.map { strategy.hydrate(accessToken, it) }

                val batchResult =
                    transactionManager.withTransaction {
                        var downloadedCount = 0
                        var conflictsResolved = 0
                        val batchErrors = mutableListOf<SyncError>()

                        for (item in hydratedChanges) {
                            try {
                                val id = strategy.idOf(item)
                                val existing = localById[id]

                                if (existing != null) {
                                    val hasPendingLocal = pendingIds.contains(id.toString())
                                    if (hasPendingLocal) {
                                        conflictsResolved++
                                        Napier.w("Skipping ${strategy.logLabel} update for $id due to local pending changes")
                                        recordConflict(
                                            entityType = strategy.entityType,
                                            entityId = id.toString(),
                                            reason = "Local pending changes vs remote update",
                                            localVersion = strategy.syncVersionOf(existing),
                                            remoteVersion = strategy.syncVersionOf(item),
                                            localUpdatedAt = strategy.lastUpdatedOf(existing),
                                            remoteUpdatedAt = strategy.lastUpdatedOf(item),
                                        )
                                        continue
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
                                            Napier.d("Resolved conflict for ${strategy.logLabel} $id: keeping remote")
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
                                            Napier.d("Resolved conflict for ${strategy.logLabel} $id: keeping local")
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
                                            Napier.d("Resolved conflict for ${strategy.logLabel} $id: merged")
                                        }
                                        is ConflictResolution.RequiresManualResolution -> {
                                            Napier.w(
                                                "Conflict for ${strategy.logLabel} $id requires manual resolution: ${resolution.reason}",
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
                                    Napier.d("Downloaded new ${strategy.logLabel}: $id")
                                }
                            } catch (e: Exception) {
                                val id = runCatching { strategy.idOf(item) }.getOrNull() ?: "unknown"
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to apply ${strategy.logLabel} change for $id: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to apply ${strategy.logLabel} change for $id", e)
                            }
                        }

                        for (id in page.deletions) {
                            try {
                                val existing = localById[id]
                                val hasPendingLocal = existing != null && pendingIds.contains(id.toString())

                                if (hasPendingLocal) {
                                    val item = requireNotNull(existing)
                                    conflictsResolved++
                                    Napier.w("Skipping ${strategy.logLabel} deletion for $id due to local changes")
                                    recordConflict(
                                        entityType = strategy.entityType,
                                        entityId = id.toString(),
                                        reason = "Local pending changes vs remote deletion",
                                        localVersion = strategy.syncVersionOf(item),
                                        remoteVersion = null,
                                        localUpdatedAt = strategy.lastUpdatedOf(item),
                                        remoteUpdatedAt = null,
                                    )
                                    continue
                                }

                                strategy.applyDelete(id)
                                strategy.afterDelete(id)
                                localById.remove(id)
                                downloadedCount++
                                Napier.d("Deleted ${strategy.logLabel}: $id")
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to delete ${strategy.logLabel} $id: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to delete ${strategy.logLabel} $id", e)
                            }
                        }

                        BatchResult(downloadedCount, conflictsResolved, batchErrors)
                    }

                totalDownloaded += batchResult.downloadedCount
                totalConflicts += batchResult.conflictsResolved
                errors.addAll(batchResult.errors)

                if (batchResult.errors.isNotEmpty()) {
                    // Holding the cursor is right for a local write that failed and may succeed
                    // next time. It is wrong for a remote item this build can never apply -- an
                    // undecryptable payload, an unknown type -- because the same page is refetched
                    // and fails identically forever, blocking every later page behind it. Hold for
                    // a few attempts, then step over the page so the feed keeps moving. The errors
                    // are still reported either way.
                    val failures = (consecutiveBatchFailures[strategy.entityType] ?: 0) + 1
                    consecutiveBatchFailures[strategy.entityType] = failures
                    if (failures < MAX_CONSECUTIVE_BATCH_FAILURES) {
                        break
                    }
                    Napier.e(
                        "${strategy.logLabel} page at $cursor failed $failures times; advancing past it to unblock sync",
                    )
                    syncMetadataService.updateLastSyncTime(strategy.entityType, page.lastSyncTimestamp)
                    consecutiveBatchFailures.remove(strategy.entityType)
                    break
                }

                consecutiveBatchFailures.remove(strategy.entityType)
                syncMetadataService.updateLastSyncTime(strategy.entityType, page.lastSyncTimestamp)

                if (!page.hasMore) {
                    break
                }

                if (page.lastSyncTimestamp <= cursor) {
                    Napier.w(
                        "${strategy.logLabel} sync pagination cursor did not advance (since=$cursor, last=${page.lastSyncTimestamp})",
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
            mapException(e, "Download ${strategy.logLabel}s")
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

    private companion object {
        /** Attempts at the same page before stepping over it. */
        const val MAX_CONSECUTIVE_BATCH_FAILURES = 3
    }
}
