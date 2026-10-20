package app.logdate.client.sync

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.diagnostics.SyncRunDiagnostics
import app.logdate.client.sync.metadata.EntityType
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * Shared shape behind [uploadPendingChanges] and [downloadRemoteChanges]: the same
 * disabled/unauthenticated guard, a [syncStateFlow] transition around the body, unexpected
 * exceptions mapped through [SyncStatusPublisher.handleSyncException], and [SyncState.Idle]
 * always restored.
 *
 * @param beforeAccessToken Runs once the guard has passed and [syncStateFlow] is
 *   [SyncState.Syncing], but before the access token is fetched -- [uploadPendingChanges]
 *   needs to capture the pending count as the run's total *before* a possible auth failure,
 *   since once the queue starts draining, the original count is gone.
 */
internal suspend fun DefaultSyncManager.runFullSyncPhase(
    operationName: String,
    exceptionLabel: String,
    beforeAccessToken: suspend () -> Unit = {},
    body: suspend (accessToken: String) -> SyncResult,
): SyncResult =
    syncMutex.withLock {
        if (!tokenRefresher.isFullSyncScopeCurrent()) return SyncResult(success = false, hasMorePending = true)
        if (!isEnabled) {
            return SyncResult(
                success = false,
                errors = listOf(SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled")),
            )
        }

        if (!tokenRefresher.isAuthenticated()) {
            Napier.w("attempted without authentication")
            return SyncResult(
                success = false,
                errors = listOf(SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated. Please sign in to sync.")),
            )
        }

        accountStorageBlocked()?.let { return it }

        syncStateFlow.value = SyncState.Syncing
        beforeAccessToken()

        try {
            val accessToken = tokenRefresher.getAccessToken() ?: return authError()
            identityProvisioner.provision(accessToken)
            if (identityRecoveryNeededStore.isNeeded() || identityKeyManager?.hasIdentityKey() == false) {
                return SyncResult(success = false)
            }
            body(accessToken)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            statusPublisher.handleSyncException(e, exceptionLabel)
        } finally {
            syncStateFlow.value = SyncState.Idle
        }
    }

/**
 * Refuses to sync anything when the account's storage mode rules out both the personal space and
 * the per-kind endpoints: syncing either way would split the account's data between two stores.
 */
private suspend fun DefaultSyncManager.accountStorageBlocked(): SyncResult? {
    val blocked = space?.blocked() ?: return null
    val error = SyncError(SyncErrorType.ACCOUNT_STORAGE_ERROR, blocked.reason, retryable = false)
    lastErrorFlow.value = error
    return SyncResult(success = false, errors = listOf(error))
}

internal suspend fun DefaultSyncManager.runPendingUploads(): SyncResult =
    runFullSyncPhase(
        operationName = "Upload",
        exceptionLabel = "Upload failed",
        beforeAccessToken = {
            // Captured here because this is the only moment the denominator exists: once the
            // run starts draining the queue, the count that is left is all anyone can see.
            statusPublisher.beginRun(runCatching { syncMetadataService.getPendingCount() }.getOrNull()?.takeIf { it > 0 })
        },
    ) { accessToken ->
        // Deliberately sequential. Every one of these writes into the same AT Protocol
        // repo, and a repo write is read-whole-tree, rebuild, write-head. Two of them in
        // flight at once each build a tree missing the other's record, and the later head
        // write wins -- the earlier record survives as an orphaned block but is no longer
        // reachable, so it silently disappears. Running them in parallel bought nothing
        // (they contend on one repo) and cost entries.
        val journalResult = uploader.uploadJournals(accessToken)
        val contentResult = uploader.uploadContent(accessToken)
        val mergeResult =
            if (journalResult.success && contentResult.success) {
                mergeUploader.uploadPending()
            } else {
                SyncResult(success = false)
            }
        val associationResult = uploader.uploadAssociations(accessToken)
        val draftResult = if (profile.syncsDrafts) uploader.uploadDrafts(accessToken) else SyncResult(success = true)
        val historyResult = uploadLocationHistory(accessToken)
        val momentResult = momentUploader?.upload() ?: SyncResult(success = true)
        val totalUploaded =
            journalResult.uploadedItems +
                contentResult.uploadedItems +
                mergeResult.uploadedItems +
                associationResult.uploadedItems +
                draftResult.uploadedItems +
                historyResult.uploadedItems + momentResult.uploadedItems
        statusPublisher.setRunCompleted(totalUploaded)
        val errors =
            journalResult.errors +
                contentResult.errors +
                mergeResult.errors +
                associationResult.errors +
                draftResult.errors +
                historyResult.errors + momentResult.errors

        val success = errors.isEmpty()
        if (success) {
            lastErrorFlow.value = null
            statusPublisher.refreshObservedQuotaFromServer("pending upload")
        } else {
            lastErrorFlow.value = errors.mostSevere()
        }

        SyncResult(
            success = success,
            uploadedItems = totalUploaded,
            errors = errors,
            lastSyncTime = latestSyncTime(),
            hasMorePending = historyResult.hasMorePending,
        )
    }

/** What one download pass brought, kind by kind. */
private data class RemoteDownloads(
    val journals: SyncResult,
    val content: SyncResult,
    val associations: SyncResult,
    val locationHistory: SyncResult,
    val moments: SyncResult,
)

/** Location history goes through the personal space when the account's data lives there, and through its own endpoints otherwise. */
private suspend fun DefaultSyncManager.uploadLocationHistory(accessToken: String): SyncResult {
    val engine = historyEngine ?: return SyncResult(success = true)
    val viaSpace = locationHistorySpace?.takeIf { it.isActive() } ?: return engine.upload(accessToken)
    return engine.uploadToSpace(accessToken, viaSpace)
}

private suspend fun DefaultSyncManager.downloadLocationHistory(accessToken: String): SyncResult {
    val engine = historyEngine ?: return SyncResult(success = true)
    val viaSpace = locationHistorySpace?.takeIf { it.isActive() } ?: return engine.download(accessToken)
    return engine.downloadFromSpace(accessToken, viaSpace)
}

internal suspend fun DefaultSyncManager.runRemoteDownload(): SyncResult =
    runFullSyncPhase(
        operationName = "Download",
        exceptionLabel = "Download failed",
    ) { accessToken ->
        auditLegacyRecordsIfNeeded()
        val journalSince = cursorFor(EntityType.JOURNAL)
        val contentSince = cursorFor(EntityType.NOTE)
        val associationSince = cursorFor(EntityType.ASSOCIATION)

        // Sequential for the same reason as the upload side: these share one server
        // instance whose per-request cost is proportional to repo size, and fanning out
        // was enough on its own to starve every request thread it had.
        val downloads =
            downloader.downloadPass {
                RemoteDownloads(
                    downloader.downloadJournals(accessToken, journalSince),
                    downloader.downloadContent(accessToken, contentSince),
                    downloader.downloadAssociations(accessToken, associationSince),
                    downloadLocationHistory(accessToken),
                    momentDownloader?.download() ?: SyncResult(success = true),
                )
            }
        val (journalResult, contentResult, associationResult, historyResult, momentResult) = downloads
        if (journalResult.remoteInventoryComplete) legacyBackfill?.enqueueRecordsIfNeeded(EntityType.JOURNAL)
        if (contentResult.remoteInventoryComplete) legacyBackfill?.enqueueRecordsIfNeeded(EntityType.NOTE)
        if (associationResult.success) legacyBackfill?.enqueueMembershipsIfNeeded()
        recoverMedia(accessToken, EntityType.NOTE)
        recoverMedia(accessToken, EntityType.JOURNAL)
        val totalDownloaded =
            journalResult.downloadedItems +
                contentResult.downloadedItems +
                associationResult.downloadedItems +
                historyResult.downloadedItems + momentResult.downloadedItems
        val conflictsResolved =
            journalResult.conflictsResolved +
                contentResult.conflictsResolved +
                associationResult.conflictsResolved
        val errors =
            journalResult.errors +
                contentResult.errors +
                associationResult.errors +
                historyResult.errors + momentResult.errors

        val success = errors.isEmpty()
        if (success) {
            lastErrorFlow.value = null
            statusPublisher.refreshObservedQuotaFromServer("remote download")
        } else {
            lastErrorFlow.value = errors.mostSevere()
        }

        SyncResult(
            success = success,
            downloadedItems = totalDownloaded,
            conflictsResolved = conflictsResolved,
            errors = errors,
            lastSyncTime = latestSyncTime(),
            hasMorePending = historyResult.hasMorePending || momentResult.hasMorePending,
        )
    }

/**
 * Shared shape behind every opportunistic, single-entity-type sync entry point
 * ([syncContent], [syncJournals], [syncAssociations], [syncDrafts]): check the caller-supplied
 * guard, download first (so a push that 409s has a fresh conflict marker to compare against),
 * start a run scoped to just this entity type's own pending count, then upload, and merge the
 * two results the same way every time.
 *
 * @param disabledOrUnauthenticated Runs before anything else; a non-null result short-circuits
 *   with it. Kept as a caller-supplied check rather than a fixed one because the four entry
 *   points don't all report "can't sync right now" the same way (some distinguish disabled vs.
 *   unauthenticated with their own [SyncError], one collapses both into a bare failure).
 * @param updatesGlobalSyncState Whether a run through this template should update
 *   [lastErrorFlow]/trigger [refreshObservedQuotaFromServer] the way [uploadPendingChanges]-driven
 *   runs do. `false` for [syncDrafts], which never reported into that shared state even before
 *   this template existed -- an unrelated, already-surfaced sync error must not be silently
 *   cleared by a successful draft-only sync, and a draft sync has no reason to trigger a quota
 *   refresh.
 * @param onException When non-null, wraps the body (guard excluded) in a catch that reports
 *   through this instead of letting the exception propagate -- only [syncDrafts] used to do
 *   this; the other three entry points still let an unexpected exception propagate uncaught.
 */
internal suspend fun DefaultSyncManager.syncEntityType(
    entityType: EntityType,
    quotaContext: String,
    disabledOrUnauthenticated: suspend () -> SyncResult?,
    download: suspend (accessToken: String, since: Instant) -> SyncResult,
    upload: suspend (accessToken: String) -> SyncResult,
    updatesGlobalSyncState: Boolean = true,
    onException: (suspend (Exception) -> SyncResult)? = null,
): SyncResult =
    syncMutex.withLock {
        if (!tokenRefresher.isFullSyncScopeCurrent()) return@withLock SyncResult(success = false, hasMorePending = true)
        disabledOrUnauthenticated()?.let { return@withLock it }
        accountStorageBlocked()?.let { return@withLock it }

        syncStateFlow.value = SyncState.Syncing
        try {
            val accessToken = tokenRefresher.getAccessToken() ?: return@withLock authError()
            identityProvisioner.provision(accessToken)
            if (identityRecoveryNeededStore.isNeeded() || identityKeyManager?.hasIdentityKey() == false) {
                return@withLock SyncResult(success = false)
            }

            if (entityType == EntityType.DRAFT) uploader.enqueueDraftsForRichSync()
            val since = cursorFor(entityType)
            val downloadResult = download(accessToken, since)
            recoverMedia(accessToken, entityType)

            statusPublisher.beginRunForPending(entityType)
            val uploadResult = upload(accessToken)

            val success = uploadResult.success && downloadResult.success
            if (updatesGlobalSyncState) {
                if (success) {
                    lastErrorFlow.value = null
                    statusPublisher.refreshObservedQuotaFromServer(quotaContext)
                } else {
                    lastErrorFlow.value = (downloadResult.errors + uploadResult.errors).mostSevere()
                }
            }

            SyncResult(
                success = success,
                uploadedItems = uploadResult.uploadedItems,
                downloadedItems = downloadResult.downloadedItems,
                conflictsResolved = downloadResult.conflictsResolved,
                errors = downloadResult.errors + uploadResult.errors,
                lastSyncTime = latestSyncTime(),
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (onException != null) onException(e) else throw e
        } finally {
            syncStateFlow.value = SyncState.Idle
        }
    }

internal suspend fun DefaultSyncManager.runCompleteSync(): SyncResult {
    val run = SyncRunDiagnostics(diagnostics, diagnosticSource())
    val selected = tokenRefresher.currentUploadScope()
    return withContext(FullSyncScope(selected)) {
        run.phase(DiagnosticPhase.RECOVERY) recovery@{
            if (!tokenRefresher.isFullSyncScopeCurrent()) return@recovery SyncResult(success = false, hasMorePending = true)
            val accountErrors = refreshAccountForSync()
            if (!tokenRefresher.isFullSyncScopeCurrent()) return@recovery SyncResult(success = false, hasMorePending = true)
            if (profile.sweepsOnFirstSync) uploader.enqueueEverythingOnFirstSync()
            val downloadResult = run.phase(DiagnosticPhase.FETCH) { downloadRemoteChanges() }
            if (!tokenRefresher.isFullSyncScopeCurrent()) return@recovery SyncResult(success = false, hasMorePending = true)
            val uploadResult = run.phase(DiagnosticPhase.UPLOAD) { uploadPendingChanges() }
            if (!tokenRefresher.isFullSyncScopeCurrent()) return@recovery SyncResult(success = false, hasMorePending = true)
            val draftResult = run.phase(DiagnosticPhase.RECOVERY) { syncDrafts() }
            if (!tokenRefresher.isFullSyncScopeCurrent()) return@recovery SyncResult(success = false, hasMorePending = true)

            try {
                ensureDeviceEnrolled()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Napier.w("Couldn't enroll this account device", failure)
            }

            val errors = accountErrors + downloadResult.errors + uploadResult.errors + draftResult.errors
            // Each phase updates the shared last error. A successful upload must not erase a failed
            // download from the same full run, or the worker retries with no visible reason.
            lastErrorFlow.value = errors.mostSevere()

            SyncResult(
                success =
                    accountErrors.isEmpty() &&
                        uploadResult.success &&
                        downloadResult.success &&
                        draftResult.success &&
                        (downloadInbox?.count() ?: 0) == 0,
                uploadedItems = uploadResult.uploadedItems + draftResult.uploadedItems,
                downloadedItems = downloadResult.downloadedItems + draftResult.downloadedItems,
                conflictsResolved = downloadResult.conflictsResolved,
                errors = errors,
                lastSyncTime = latestSyncTime(),
                hasMorePending =
                    downloadResult.hasMorePending ||
                        uploadResult.hasMorePending ||
                        draftResult.hasMorePending ||
                        (downloadInbox?.count() ?: 0) > 0,
            ).let { result ->
                result.copy(
                    recoveredMediaScope =
                        selected.takeIf {
                            result.success &&
                                !result.hasMorePending &&
                                tokenRefresher.isFullSyncScopeCurrent()
                        },
                )
            }
        }
    }
}

/**
 * Reads the account from the server before anything else syncs, so its storage mode is the server's
 * current answer rather than a saved one. A failure is reported with the run's other errors; it never
 * stops the legacy phases, which the server refuses itself when the account isn't in legacy storage.
 */
internal suspend fun DefaultSyncManager.refreshAccountForSync(): List<SyncError> {
    if (!isEnabled || !tokenRefresher.isAuthenticated()) return emptyList()
    val failure = refreshServerAccount()?.exceptionOrNull() ?: return emptyList()
    Napier.e("The account could not be read from the server at the start of sync", failure)
    val unauthorized = (failure.cause as? CloudApiException)?.statusCode == 401
    return listOf(
        SyncError(
            type = if (unauthorized) SyncErrorType.AUTHENTICATION_ERROR else SyncErrorType.SERVER_ERROR,
            message = "The account could not be read from the server: ${failure.message}",
            cause = failure,
        ),
    )
}
