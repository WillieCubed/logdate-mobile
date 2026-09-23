package app.logdate.client.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.media.MediaManager
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.FirstSyncEnqueueStore
import app.logdate.client.sync.metadata.IdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.InMemoryFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.InMemoryIdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.InMemoryLastSyncErrorStore
import app.logdate.client.sync.metadata.InMemoryUnreadableCloudRecordStore
import app.logdate.client.sync.metadata.LastSyncErrorStore
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.SyncBackoff
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.client.sync.metadata.UnreadableCloudRecordStore
import app.logdate.client.util.platformIODispatcher
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.CloudQuotaManager
import app.logdate.shared.model.Journal
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * Default implementation of SyncManager that coordinates synchronization
 * across all data sources using the LogDate Cloud API.
 *
 * This class acts as an orchestrator, delegating work to injected dependencies.
 */
class DefaultSyncManager(
    private val cloudContentDataSource: CloudContentDataSource,
    private val cloudJournalDataSource: CloudJournalDataSource,
    private val cloudAssociationDataSource: CloudAssociationDataSource,
    private val cloudMediaDataSource: CloudMediaDataSource,
    private val cloudDraftDataSource: CloudDraftDataSource,
    private val cloudAccountRepository: CloudAccountRepository,
    private val sessionStorage: SessionStorage,
    private val mediaManager: MediaManager,
    private val mediaSyncRefStore: MediaSyncRefStore,
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val journalContentRepository: JournalContentRepository,
    private val journalConflictResolver: ConflictResolver<Journal>,
    private val noteConflictResolver: ConflictResolver<JournalNote>,
    private val conflictStore: SyncConflictStore,
    private val deadLetterStore: SyncDeadLetterStore,
    private val retryScheduleStore: SyncRetryScheduleStore,
    private val syncMetadataService: SyncMetadataService,
    private val transactionManager: SyncTransactionManager,
    private val dataUsagePolicy: DataUsagePolicy,
    private val deviceIdProvider: DeviceIdProvider? = null,
    /** Wired on every platform; optional only so tests that never encrypt can omit it. */
    private val identityKeyManager: IdentityKeyManager? = null,
    /**
     * Wired on every platform; optional so tests that never exercise identity provisioning can
     * omit it. Used only to forget the cached media key when [provisionIdentityKey] silently
     * restores an identity from an [app.logdate.client.device.crypto.IdentityKeyBackupStore] --
     * the cache otherwise keeps serving a key derived from whatever identity minted it.
     */
    private val mediaPayloadKeyProvider: MediaPayloadKeyProvider? = null,
    private val cloudQuotaManager: CloudQuotaManager? = null,
    private val backoff: SyncBackoff = SyncBackoff(),
    private val syncScope: CoroutineScope = CoroutineScope(platformIODispatcher),
    private val lastErrorStore: LastSyncErrorStore = InMemoryLastSyncErrorStore(),
    private val firstSyncEnqueueStore: FirstSyncEnqueueStore = InMemoryFirstSyncEnqueueStore(),
    /**
     * Wired on every platform; optional so tests that never exercise identity provisioning can
     * omit it. Without it, [provisionIdentityKey] cannot check whether the account already has
     * cloud data and falls back to minting a key on absence, matching behavior before that check
     * existed.
     */
    private val cloudApiClient: CloudApiClient? = null,
    private val identityRecoveryNeededStore: IdentityRecoveryNeededStore = InMemoryIdentityRecoveryNeededStore(),
    private val unreadableCloudRecordStore: UnreadableCloudRecordStore = InMemoryUnreadableCloudRecordStore(),
) : SyncManager {
    // Thread-safe state management using StateFlow and Mutex
    private val syncStateFlow = MutableStateFlow<SyncState>(SyncState.Idle)
    private val lastErrorFlow = MutableStateFlow<SyncError?>(null)
    private val syncMutex = Mutex()

    private var isEnabled = true

    private val statusPublisher =
        SyncStatusPublisher(
            sessionStorage = sessionStorage,
            syncMetadataService = syncMetadataService,
            dataUsagePolicy = dataUsagePolicy,
            cloudQuotaManager = cloudQuotaManager,
            syncStateFlow = syncStateFlow,
            lastErrorFlow = lastErrorFlow,
            syncScope = syncScope,
            lastErrorStore = lastErrorStore,
            latestSyncTime = ::latestSyncTime,
            isEnabled = { isEnabled },
            conflictStore = conflictStore,
            identityRecoveryNeededStore = identityRecoveryNeededStore,
            unreadableCloudRecordStore = unreadableCloudRecordStore,
        )
    override val syncStatusFlow: StateFlow<SyncStatus> = statusPublisher.syncStatusFlow

    private val downloadEngine =
        SyncDownloadEngine(
            transactionManager = transactionManager,
            syncMetadataService = syncMetadataService,
            conflictStore = conflictStore,
            mapCloudApiError = statusPublisher::handleCloudApiError,
            mapException = statusPublisher::handleSyncException,
            unreadableCloudRecordStore = unreadableCloudRecordStore,
        )

    /**
     * Arming a periodic/background schedule is a platform concern this class has no access to --
     * the platform-specific [SyncManager]s (Android's WorkManager-backed one, [ForegroundSyncManager]
     * elsewhere) each do their own scheduling and never call this method at all. So the only
     * meaningful case here is `startNow`: fire a sync immediately; per [SyncManager.sync]'s own
     * contract, `false` does not start syncing immediately.
     */
    override fun sync(startNow: Boolean) {
        if (startNow) {
            syncScope.launch {
                releaseUploadBackoff()
                fullSync()
            }
        }
    }

    /**
     * Every note, journal, and draft is encrypted with a key derived from this device's identity
     * key, so a device without one fails every upload. Onboarding provisions it for new devices;
     * this covers the ones that finished onboarding back when nothing did.
     *
     * A device can also have no key because it *lost* one -- a reinstall, a restore onto a
     * different device -- while the account it signs back into already has data in the cloud
     * encrypted under the old key. Minting a fresh key in that case would look identical to
     * onboarding a new device, but it silently starts a second, unrelated identity: nothing
     * already in the cloud can ever be read again, and uploads from now on quietly diverge from
     * everything before them. So before minting anything, this first checks whether the identity
     * key manager can silently restore the key from its own device-transfer/cloud backup (see
     * [app.logdate.client.device.crypto.IdentityKeyBackupStore]) -- the common case for exactly
     * this scenario, needing no user interaction at all. Only when that backup is also empty does
     * this fall back to the mint-or-pause decision: mints when the account can be confirmed empty;
     * when it already has data, sync pauses with [SyncPausedReason.NEEDS_RECOVERY_PHRASE] instead
     * and waits for the phrase to be entered again, and when it cannot be determined (offline, a
     * server error), nothing happens this pass and the next sync attempt tries again.
     */
    private suspend fun provisionIdentityKey(accessToken: String) {
        val manager = identityKeyManager ?: return
        if (manager.hasIdentityKey()) {
            if (identityRecoveryNeededStore.isNeeded()) identityRecoveryNeededStore.setNeeded(false)
            return
        }

        if (manager.restoreFromBackupIfAvailable()) {
            Napier.i(
                "Identity key silently restored from its backup store -- resetting sync state so " +
                    "records the old, now-replaced identity could not read re-arrive and get " +
                    "another chance",
            )
            mediaPayloadKeyProvider?.clearCachedKey()
            syncMetadataService.resetAllCursors()
            identityRecoveryNeededStore.setNeeded(false)
            unreadableCloudRecordStore.clear()
            return
        }

        val client = cloudApiClient
        if (client == null) {
            runCatching { manager.setupNewIdentity() }
                .onFailure { Napier.e("Could not provision an identity key; uploads will fail", it) }
            return
        }

        when (accountAlreadyHasCloudData(client, accessToken)) {
            true -> {
                Napier.w(
                    "This device has no identity key but the account already has cloud data -- " +
                        "pausing sync instead of minting a key that could never decrypt it",
                )
                identityRecoveryNeededStore.setNeeded(true)
            }
            false -> {
                runCatching { manager.setupNewIdentity() }
                    .onFailure { Napier.e("Could not provision an identity key; uploads will fail", it) }
            }
            null -> {
                // Could not tell (offline, a server error). Leave things as they are; the next
                // sync attempt checks again rather than guessing now.
            }
        }
    }

    /** Null when it could not be determined -- offline, a server error -- rather than assumed either way. */
    private suspend fun accountAlreadyHasCloudData(
        client: CloudApiClient,
        accessToken: String,
    ): Boolean? =
        runCatching {
            val journals = client.getJournalChanges(accessToken, since = 0L, limit = 1).getOrThrow()
            if (journals.changes.isNotEmpty() || journals.deletions.isNotEmpty()) return@runCatching true
            val content = client.getContentChanges(accessToken, since = 0L, limit = 1).getOrThrow()
            content.changes.isNotEmpty() || content.deletions.isNotEmpty()
        }.getOrElse {
            Napier.w("Could not check whether the account already has cloud data", it)
            null
        }

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
    private suspend fun runFullSyncPhase(
        operationName: String,
        exceptionLabel: String,
        beforeAccessToken: suspend () -> Unit = {},
        body: suspend (accessToken: String) -> SyncResult,
    ): SyncResult =
        syncMutex.withLock {
            if (!isEnabled) {
                return SyncResult(
                    success = false,
                    errors = listOf(SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled")),
                )
            }

            if (!tokenRefresher.isAuthenticated()) {
                Napier.w("$operationName attempted without authentication")
                return SyncResult(
                    success = false,
                    errors = listOf(SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated. Please sign in to sync.")),
                )
            }

            syncStateFlow.value = SyncState.Syncing
            beforeAccessToken()

            try {
                val accessToken = tokenRefresher.getAccessToken() ?: return authError()
                provisionIdentityKey(accessToken)
                if (identityRecoveryNeededStore.isNeeded()) {
                    return SyncResult(success = false)
                }
                body(accessToken)
            } catch (e: Exception) {
                statusPublisher.handleSyncException(e, exceptionLabel)
            } finally {
                syncStateFlow.value = SyncState.Idle
            }
        }

    override suspend fun uploadPendingChanges(): SyncResult =
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
            val associationResult = uploader.uploadAssociations(accessToken)
            val draftResult = uploader.uploadDrafts(accessToken)
            val totalUploaded =
                journalResult.uploadedItems +
                    contentResult.uploadedItems +
                    associationResult.uploadedItems +
                    draftResult.uploadedItems
            statusPublisher.setRunCompleted(totalUploaded)
            val errors =
                journalResult.errors +
                    contentResult.errors +
                    associationResult.errors +
                    draftResult.errors

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
            )
        }

    override suspend fun downloadRemoteChanges(): SyncResult =
        runFullSyncPhase(
            operationName = "Download",
            exceptionLabel = "Download failed",
        ) { accessToken ->
            val journalSince = cursorFor(EntityType.JOURNAL)
            val contentSince = cursorFor(EntityType.NOTE)
            val associationSince = cursorFor(EntityType.ASSOCIATION)

            // Sequential for the same reason as the upload side: these share one server
            // instance whose per-request cost is proportional to repo size, and fanning out
            // was enough on its own to starve every request thread it had.
            val journalResult = downloader.downloadJournals(accessToken, journalSince)
            val contentResult = downloader.downloadContent(accessToken, contentSince)
            val associationResult = downloader.downloadAssociations(accessToken, associationSince)
            val totalDownloaded =
                journalResult.downloadedItems +
                    contentResult.downloadedItems +
                    associationResult.downloadedItems
            val conflictsResolved =
                journalResult.conflictsResolved +
                    contentResult.conflictsResolved +
                    associationResult.conflictsResolved
            val errors =
                journalResult.errors +
                    contentResult.errors +
                    associationResult.errors

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
    private suspend fun syncEntityType(
        entityType: EntityType,
        quotaContext: String,
        disabledOrUnauthenticated: suspend () -> SyncResult?,
        download: suspend (accessToken: String, since: Instant) -> SyncResult,
        upload: suspend (accessToken: String) -> SyncResult,
        updatesGlobalSyncState: Boolean = true,
        onException: (suspend (Exception) -> SyncResult)? = null,
    ): SyncResult =
        syncMutex.withLock {
            disabledOrUnauthenticated()?.let { return@withLock it }

            syncStateFlow.value = SyncState.Syncing
            try {
                val accessToken = tokenRefresher.getAccessToken() ?: return@withLock authError()
                provisionIdentityKey(accessToken)
                if (identityRecoveryNeededStore.isNeeded()) {
                    return@withLock SyncResult(success = false)
                }

                val since = cursorFor(entityType)
                val downloadResult = download(accessToken, since)

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
                if (onException != null) onException(e) else throw e
            } finally {
                syncStateFlow.value = SyncState.Idle
            }
        }

    /**
     * The "disabled" / "not authenticated" guard shared by [syncContent]/[syncJournals]/[syncAssociations]
     * -- each reports disabled vs. unauthenticated as distinct [SyncError]s, unlike [syncDrafts]'s
     * own single combined check.
     */
    private suspend fun disabledOrUnauthenticatedResult(): SyncResult? =
        if (!isEnabled) {
            SyncResult(success = false, errors = listOf(SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled")))
        } else if (!tokenRefresher.isAuthenticated()) {
            SyncResult(success = false, errors = listOf(SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated")))
        } else {
            null
        }

    override suspend fun syncContent(): SyncResult =
        syncEntityType(
            entityType = EntityType.NOTE,
            quotaContext = "content sync",
            disabledOrUnauthenticated = ::disabledOrUnauthenticatedResult,
            download = downloader::downloadContent,
            upload = uploader::uploadContent,
        )

    override suspend fun syncJournals(): SyncResult =
        syncEntityType(
            entityType = EntityType.JOURNAL,
            quotaContext = "journal sync",
            disabledOrUnauthenticated = ::disabledOrUnauthenticatedResult,
            download = downloader::downloadJournals,
            upload = uploader::uploadJournals,
        )

    override suspend fun syncAssociations(): SyncResult =
        syncEntityType(
            entityType = EntityType.ASSOCIATION,
            quotaContext = "association sync",
            disabledOrUnauthenticated = ::disabledOrUnauthenticatedResult,
            download = downloader::downloadAssociations,
            upload = uploader::uploadAssociations,
        )

    override suspend fun syncDrafts(): SyncResult =
        syncEntityType(
            entityType = EntityType.DRAFT,
            quotaContext = "draft sync",
            disabledOrUnauthenticated = {
                if (!isEnabled || !tokenRefresher.isAuthenticated()) SyncResult(success = false) else null
            },
            download = downloader::downloadDrafts,
            upload = uploader::uploadDrafts,
            updatesGlobalSyncState = false,
            onException = { e ->
                Napier.e("Draft sync failed", e)
                SyncResult(
                    success = false,
                    errors = listOf(SyncError(SyncErrorType.UNKNOWN_ERROR, "Draft sync failed: ${e.message}")),
                )
            },
        )

    override suspend fun fullSync(): SyncResult {
        uploader.enqueueEverythingOnFirstSync()
        val downloadResult = downloadRemoteChanges()
        val uploadResult = uploadPendingChanges()
        val draftResult = syncDrafts()

        return SyncResult(
            success = uploadResult.success && downloadResult.success && draftResult.success,
            uploadedItems = uploadResult.uploadedItems + draftResult.uploadedItems,
            downloadedItems = downloadResult.downloadedItems + draftResult.downloadedItems,
            conflictsResolved = downloadResult.conflictsResolved,
            errors = downloadResult.errors + uploadResult.errors + draftResult.errors,
            lastSyncTime = latestSyncTime(),
        )
    }

    override suspend fun getSyncStatus(): SyncStatus {
        val pendingCount = syncMetadataService.getPendingCount()
        val authenticated = sessionStorage.getSession() != null
        return SyncStatus(
            isEnabled = authenticated && isEnabled,
            lastSyncTime = latestSyncTime(),
            pendingUploads = pendingCount,
            isSyncing = syncStateFlow.value is SyncState.Syncing,
            hasErrors = lastErrorFlow.value != null,
            lastError = lastErrorFlow.value,
            pausedReason = statusPublisher.currentPausedReason(authenticated),
            totalForRun = statusPublisher.runTotal,
            completedInRun = statusPublisher.runCompleted,
            conflictCount = runCatching { conflictStore.list().size }.getOrDefault(0),
            unreadableCloudCount = runCatching { unreadableCloudRecordStore.count() }.getOrDefault(0),
        )
    }

    override fun observeDeadLetters(): Flow<List<SyncDeadLetterRecord>> = deadLetterStore.observe()

    /** See [SyncRetryCoordinator.releaseBackoff]. Called when the user asks to back up now. */
    suspend fun releaseUploadBackoff() = retryCoordinator.releaseBackoff()

    override suspend fun retryDeadLetter(id: String) = retryCoordinator.retryDeadLetter(id)

    override suspend fun discardDeadLetter(id: String) = retryCoordinator.discardDeadLetter(id)

    /**
     * Gets the last sync error, used for network recovery decisions.
     * Returns null if the last sync succeeded.
     */
    fun getLastSyncError(): SyncError? = lastErrorFlow.value

    /**
     * Helper to create authentication error result.
     */
    private fun authError() =
        SyncResult(
            success = false,
            errors = listOf(SyncError(SyncErrorType.AUTHENTICATION_ERROR, "No access token")),
        )

    private val tokenRefresher = SyncTokenRefresher(sessionStorage, cloudAccountRepository)

    private suspend fun cursorFor(entityType: EntityType): Instant =
        syncMetadataService.getLastSyncTime(entityType)
            ?: Instant.fromEpochMilliseconds(0)

    private suspend fun latestSyncTime(): Instant? {
        val times =
            listOf(
                syncMetadataService.getLastSyncTime(EntityType.JOURNAL),
                syncMetadataService.getLastSyncTime(EntityType.NOTE),
                syncMetadataService.getLastSyncTime(EntityType.ASSOCIATION),
                syncMetadataService.getLastSyncTime(EntityType.MEDIA),
                syncMetadataService.getLastSyncTime(EntityType.DRAFT),
            ).filterNotNull()

        return times.maxOrNull()
    }

    private val mediaTransfer =
        SyncMediaTransfer(
            mediaManager = mediaManager,
            mediaSyncRefStore = mediaSyncRefStore,
            cloudMediaDataSource = cloudMediaDataSource,
        )

    private val retryCoordinator =
        SyncRetryCoordinator(
            retryScheduleStore = retryScheduleStore,
            syncMetadataService = syncMetadataService,
            deadLetterStore = deadLetterStore,
            backoff = backoff,
            recordConflict = downloadEngine::recordConflict,
        )

    private val downloader =
        SyncDownloader(
            journalRepository = journalRepository,
            journalNotesRepository = journalNotesRepository,
            journalContentRepository = journalContentRepository,
            cloudJournalDataSource = cloudJournalDataSource,
            cloudContentDataSource = cloudContentDataSource,
            cloudDraftDataSource = cloudDraftDataSource,
            cloudAssociationDataSource = cloudAssociationDataSource,
            journalConflictResolver = journalConflictResolver,
            noteConflictResolver = noteConflictResolver,
            syncMetadataService = syncMetadataService,
            transactionManager = transactionManager,
            mediaSyncRefStore = mediaSyncRefStore,
            downloadEngine = downloadEngine,
            mediaTransfer = mediaTransfer,
            tokenRefresher = tokenRefresher,
            mapCloudApiError = statusPublisher::handleCloudApiError,
            mapException = statusPublisher::handleSyncException,
        )

    private val uploader =
        SyncUploader(
            journalRepository = journalRepository,
            journalNotesRepository = journalNotesRepository,
            cloudJournalDataSource = cloudJournalDataSource,
            cloudContentDataSource = cloudContentDataSource,
            cloudAssociationDataSource = cloudAssociationDataSource,
            cloudDraftDataSource = cloudDraftDataSource,
            mediaSyncRefStore = mediaSyncRefStore,
            syncMetadataService = syncMetadataService,
            dataUsagePolicy = dataUsagePolicy,
            deviceIdProvider = deviceIdProvider,
            tokenRefresher = tokenRefresher,
            mediaTransfer = mediaTransfer,
            retryCoordinator = retryCoordinator,
            firstSyncEnqueueStore = firstSyncEnqueueStore,
            mapCloudApiError = statusPublisher::handleCloudApiError,
            mapException = statusPublisher::handleSyncException,
            recordProgress = statusPublisher::recordProgress,
            setMediaDeferredForNetwork = statusPublisher::setMediaDeferredForNetwork,
        )
}

/**
 * The most actionable error in a run that may have failed several ways at once, rather than
 * whichever happened to run first in the fixed upload/download order (journal, then content, then
 * association, then draft) -- code order is not a severity order, and a network hiccup on the
 * first of those must not hide an auth lapse or a full quota discovered on the second. Every error
 * is still returned to the caller either way; this only decides which one is surfaced as *the*
 * status.
 */
private fun List<SyncError>.mostSevere(): SyncError? = SEVERITY_ORDER.firstNotNullOfOrNull { type -> firstOrNull { it.type == type } }

private val SEVERITY_ORDER =
    listOf(
        SyncErrorType.AUTHENTICATION_ERROR,
        SyncErrorType.STORAGE_ERROR,
        SyncErrorType.CONFLICT_ERROR,
        SyncErrorType.SERVER_ERROR,
        SyncErrorType.NETWORK_ERROR,
        SyncErrorType.UNKNOWN_ERROR,
    )

/**
 * The media a note points at is no longer on disk, so no number of retries can upload it.
 */
class MissingMediaException(
    mediaRef: String,
    cause: Throwable,
) : Exception("Media no longer exists at $mediaRef: ${cause.message}", cause)

/** The current state of a [DefaultSyncManager] sync operation. */
internal sealed class SyncState {
    object Idle : SyncState()

    object Syncing : SyncState()
}
