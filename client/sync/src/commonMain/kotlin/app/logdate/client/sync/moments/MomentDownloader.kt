package app.logdate.client.sync.moments

import app.logdate.client.sync.SyncDownloader
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncTokenRefresher
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.MomentSyncResult
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UnreadableCloudRecordStore
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class MomentDownloader(
    private val config: MomentSyncConfig,
    private val metadata: SyncMetadataService,
    private val transactions: SyncTransactionManager,
    private val inbox: DownloadInbox,
    private val tokens: SyncTokenRefresher,
    private val unreadable: UnreadableCloudRecordStore,
    private val mapCloudError: (CloudApiException) -> SyncResult,
    private val mapException: (Exception, String) -> SyncResult,
) {
    private val applier = MomentRemoteApplier(config.repository, metadata, transactions, inbox)

    private class Tally {
        var downloaded = 0
        val errors = mutableListOf<SyncError>()
    }

    suspend fun download(): SyncResult =
        try {
            if (!config.enabled()) {
                SyncResult(success = true)
            } else {
                val selected = inbox.currentScope()
                val request = tokens.currentRequestScope() ?: throw scopeChanged()
                ensureScope(selected, request)
                downloadPages(selected, request)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: CloudApiException) {
            mapCloudError(error)
        } catch (error: Exception) {
            mapException(error, "Download marked moments")
        }

    private suspend fun downloadPages(
        selected: DownloadScope,
        request: UploadScope,
    ): SyncResult {
        var cursor = Instant.fromEpochMilliseconds(inbox.cursor(TYPE, selected))
        val tally = Tally()
        repeat(MAX_PAGES) {
            ensureScope(selected, request)
            if (!config.enabled()) return result(tally)
            val page =
                tokens
                    .withFreshToken(
                        { token -> config.cloud.changes(token, cursor, SyncDownloader.SYNC_PAGE_SIZE) },
                        "getMomentChanges",
                        request,
                    ).getOrThrow()
            ensureScope(selected, request)
            if (!config.enabled()) return result(tally)
            applyPage(page, selected, request, tally)
            ensureScope(selected, request)
            if (inbox.retained(TYPE, selected).isNotEmpty() && tally.errors.isEmpty()) {
                tally.errors += SyncError(SyncErrorType.UNKNOWN_ERROR, "Marked moments are saved for recovery")
            }
            val fetchFailed = inbox.fetchFailure(TYPE, selected) != DiagnosticReason.NONE
            if (fetchFailed) {
                tally.errors +=
                    SyncError(SyncErrorType.NETWORK_ERROR, "Saved marked moments recovered; the latest download failed")
            }
            if (page.hasMore && page.lastSyncTimestamp <= cursor) {
                tally.errors += SyncError(SyncErrorType.SERVER_ERROR, "Marked-moment download did not advance")
                return result(tally)
            }
            if (tally.errors.isEmpty()) {
                transactions.withTransaction {
                    ensureScope(selected, request)
                    metadata.updateLastSyncTime(EntityType.MOMENT, page.lastSyncTimestamp)
                    ensureScope(selected, request)
                }
            }
            if (!page.hasMore) return result(tally, inventoryComplete = !fetchFailed)
            cursor = page.lastSyncTimestamp
        }
        return result(tally, hasMore = true)
    }

    private suspend fun applyPage(
        page: MomentSyncResult,
        selected: DownloadScope,
        request: UploadScope,
        tally: Tally,
    ) {
        for (mark in page.changes) {
            applyRecord(mark.id, mark.syncVersion, selected, request, tally) { applier.apply(mark, selected) }
        }
        for (id in page.deletions) {
            val version = page.deletionVersions[id]
            val deletedAt = page.deletionTimestamps[id]
            if (version == null || deletedAt == null) {
                tally.errors += SyncError(SyncErrorType.SERVER_ERROR, "Incomplete marked-moment deletion")
                continue
            }
            applyRecord(id, version, selected, request, tally) { applier.delete(id, deletedAt, version, selected) }
        }
        for (id in page.unreadable) {
            ensureScope(selected, request)
            val version = page.unreadableVersions[id] ?: continue
            if (!inbox.isCurrentVersion(TYPE, id.toString(), version, selected)) continue
            inbox.failed(TYPE, id.toString(), DiagnosticReason.KEY_RECOVERY_REQUIRED.name, version, selected)
            unreadable.record(EntityType.MOMENT, listOf(id))
            ensureScope(selected, request)
            tally.errors += SyncError(SyncErrorType.KEY_RECOVERY_ERROR, "A marked moment needs its original identity key")
        }
        for (failure in page.failures) {
            ensureScope(selected, request)
            inbox.failed(TYPE, failure.entityId, failure.reason.name, failure.serverVersion, selected)
            ensureScope(selected, request)
            tally.errors += SyncError(SyncErrorType.SERVER_ERROR, "A marked moment could not be read")
        }
    }

    private suspend fun applyRecord(
        id: Uuid,
        version: Long,
        selected: DownloadScope,
        request: UploadScope,
        tally: Tally,
        apply: suspend () -> Boolean,
    ) {
        try {
            ensureScope(selected, request)
            if (apply()) {
                unreadable.resolved(EntityType.MOMENT, id)
                ensureScope(selected, request)
                tally.downloaded++
            } else if (inbox.isCurrentVersion(TYPE, id.toString(), version, selected)) {
                tally.errors += SyncError(SyncErrorType.CONFLICT_ERROR, "A marked moment could not be merged")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ensureScope(selected, request)
            inbox.failed(TYPE, id.toString(), DiagnosticReason.LOCAL_STORAGE.name, version, selected)
            tally.errors += SyncError(SyncErrorType.UNKNOWN_ERROR, "A marked moment could not be saved")
        }
    }

    private fun ensureScope(
        selected: DownloadScope,
        request: UploadScope,
    ) {
        if (selected != inbox.currentScope() ||
            request != tokens.currentRequestScope() ||
            selected.owner != request.ownerId ||
            selected.origin != request.serverOrigin
        ) {
            throw scopeChanged()
        }
    }

    private fun scopeChanged() = CloudApiException("CLOUD_SCOPE_CHANGED", "Marked-moment account or server changed")

    private suspend fun result(
        tally: Tally,
        hasMore: Boolean = false,
        inventoryComplete: Boolean = false,
    ) = SyncResult(
        success = tally.errors.isEmpty(),
        downloadedItems = tally.downloaded,
        errors = tally.errors,
        lastSyncTime = metadata.getLastSyncTime(EntityType.MOMENT),
        hasMorePending = hasMore,
        remoteInventoryComplete = inventoryComplete,
    )

    private companion object {
        const val TYPE = "MOMENT"
        const val MAX_PAGES = 20
    }
}
