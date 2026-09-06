package app.logdate.client.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.media.MediaManager
import app.logdate.client.networking.DataRestriction
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.shouldSyncMedia
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableDraftRepository
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudAssociationDataSource
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.CloudDraftDataSource
import app.logdate.client.sync.cloud.CloudJournalDataSource
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.JournalContentAssociation
import app.logdate.client.sync.cloud.SyncedDraft
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncBackoff
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import app.logdate.client.util.platformIODispatcher
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.CloudQuotaManager
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.sync.DeviceId
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

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
    private val cloudQuotaManager: CloudQuotaManager? = null,
    private val backoff: SyncBackoff = SyncBackoff(),
    private val syncScope: CoroutineScope = CoroutineScope(platformIODispatcher),
) : SyncManager {
    // Thread-safe state management using StateFlow and Mutex
    private val syncStateFlow = MutableStateFlow<SyncState>(SyncState.Idle)
    private val lastErrorFlow = MutableStateFlow<SyncError?>(null)
    private val syncMutex = Mutex()

    private var isEnabled = true

    /**
     * Whether the last upload pass held a photo or video back for want of Wi-Fi.
     *
     * Reset at the start of each upload pass so the status reflects the current run rather than
     * a deferral the device has long since worked through.
     */
    private var mediaDeferredForNetwork = false

    /** What the current upload run set out to do, and how far through it is. See [SyncStatus]. */
    private var runTotal: Int? = null
    private var runCompleted = 0

    private val _syncStatusFlow =
        MutableStateFlow(
            SyncStatus(
                isEnabled = true,
                lastSyncTime = null,
                pendingUploads = 0,
                isSyncing = false,
                hasErrors = false,
            ),
        )
    override val syncStatusFlow: StateFlow<SyncStatus> = _syncStatusFlow.asStateFlow()

    init {
        // Republish status on each internal state/error transition so observers don't have to poll.
        syncScope.launch {
            combine(syncStateFlow, lastErrorFlow) { state, error -> state to error }
                .collect { publishStatus() }
        }
        // Republish whenever the session changes — sign-in flips isEnabled true, sign-out flips
        // it false, and the UI needs to react without waiting for the next sync transition.
        syncScope.launch {
            sessionStorage.getSessionFlow().collect { publishStatus() }
        }
    }

    /**
     * Snapshot the combined state into [syncStatusFlow]. Reads pending-uploads from metadata
     * on every publish; falls back to zero if metadata is unavailable (shouldn't happen in
     * practice, but we'd rather show an over-optimistic banner than crash the collector).
     *
     * `isEnabled` here is the *effective* state the UI cares about: a queue can only matter
     * when there's a session to drain it to. Without a session, we report disabled regardless
     * of the local preference.
     */
    private suspend fun publishStatus() {
        val authenticated = sessionStorage.getSession() != null
        val pendingCount =
            runCatching { syncMetadataService.getPendingCount() }.getOrDefault(0)
        _syncStatusFlow.value =
            SyncStatus(
                isEnabled = authenticated && isEnabled,
                lastSyncTime = latestSyncTime(),
                pendingUploads = pendingCount,
                isSyncing = syncStateFlow.value is SyncState.Syncing,
                hasErrors = lastErrorFlow.value != null,
                lastError = lastErrorFlow.value,
                pausedReason = currentPausedReason(authenticated),
                totalForRun = runTotal,
                completedInRun = runCompleted,
            )
    }

    /**
     * Starts a new top-level upload run: forgets whatever the previous run's counters said and
     * records what *this* run is setting out to do, then republishes so observers see the reset
     * immediately rather than a stale carryover.
     *
     * Must be called at the top of every public entry point that can reach [recordProgress] --
     * not just [uploadPendingChanges], but also the opportunistic, single-entity-type entry
     * points ([syncContent], [syncJournals], [syncAssociations], [syncDrafts]) that repositories
     * call right after a local write. Without this, an opportunistic sync of one item run right
     * after an earlier five-item run left [runTotal] at the stale value of 5 while [runCompleted]
     * climbed past it (e.g. reporting "6 of 5" instead of "1 of 1").
     */
    private suspend fun beginUploadRun(total: Int?) {
        runTotal = total
        runCompleted = 0
        publishStatus()
    }

    /** [beginUploadRun] scoped to how many of [entityType] are currently pending. */
    private suspend fun beginUploadRunForPending(entityType: EntityType) {
        beginUploadRun(runCatching { syncMetadataService.getPendingUploads(entityType).size }.getOrNull()?.takeIf { it > 0 })
    }

    /**
     * Advances the upload run's progress by [count] items and republishes status immediately.
     *
     * Called from inside each upload loop as items actually finish, rather than only once at the
     * start and end of a run -- without this, [runCompleted] sat at zero for the whole run and
     * jumped straight to the total, so a determinate progress indicator never actually looked
     * like it was moving.
     *
     * Deliberately does *not* go through [publishStatus]: that re-runs
     * [SyncMetadataService.getPendingCount], which takes a mutex and loops every [EntityType]
     * doing a promotion check before its final COUNT query. Calling that once per uploaded item
     * turned a 500-note backup into roughly 500x that overhead for no benefit -- the pending
     * badge doesn't need to be exactly current between every single item, only the run counters
     * ([runTotal]/[runCompleted]) do. This reuses the rest of the last-published snapshot and
     * only touches the two fields that actually changed; a fresh, fully-recomputed status is
     * still published on every state transition (run start/end) via [publishStatus].
     */
    private fun recordProgress(count: Int = 1) {
        runCompleted += count
        _syncStatusFlow.value =
            _syncStatusFlow.value.copy(
                totalForRun = runTotal,
                completedInRun = runCompleted,
            )
    }

    /**
     * Why the backup cannot progress right now, or null if nothing is holding it back.
     *
     * Reported whether or not anything is queued: background data being off means entries
     * written from here on will not back up either, and the user should hear that before
     * they lose a week of writing to a setting they do not know is on.
     */
    private suspend fun currentPausedReason(authenticated: Boolean): SyncPausedReason? {
        if (!authenticated) return SyncPausedReason.NOT_SIGNED_IN
        val restriction = runCatching { dataUsagePolicy.currentRestriction() }.getOrNull()
        return when (restriction) {
            DataRestriction.OFFLINE -> SyncPausedReason.OFFLINE
            DataRestriction.BACKGROUND_DATA_BLOCKED -> SyncPausedReason.BACKGROUND_DATA_OFF
            DataRestriction.NONE, null ->
                // Only after something was actually held back - being on cellular with nothing
                // waiting is not a pause, and saying so would train the user to ignore the line.
                SyncPausedReason.MEDIA_WAITING_FOR_WIFI.takeIf {
                    mediaDeferredForNetwork && !dataUsagePolicy.currentMode().shouldSyncMedia()
                }
        }
    }

    /**
     * Represents the current state of the sync operation.
     */
    private sealed class SyncState {
        object Idle : SyncState()

        object Syncing : SyncState()
    }

    private val downloadEngine =
        SyncDownloadEngine(
            transactionManager = transactionManager,
            syncMetadataService = syncMetadataService,
            conflictStore = conflictStore,
            mapCloudApiError = ::handleCloudApiError,
            mapException = ::handleSyncException,
        )

    override fun sync(startNow: Boolean) {
        if (startNow) {
            syncScope.launch {
                fullSync()
            }
        } else {
            // Schedule background sync
            syncScope.launch {
                fullSync()
            }
        }
    }

    override suspend fun uploadPendingChanges(): SyncResult =
        syncMutex.withLock {
            if (!isEnabled) {
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled"),
                        ),
                )
            }

            if (!isAuthenticated()) {
                Napier.w("Upload attempted without authentication")
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated. Please sign in to sync."),
                        ),
                )
            }

            syncStateFlow.value = SyncState.Syncing
            // Captured here because this is the only moment the denominator exists: once the run
            // starts draining the queue, the count that is left is all anyone can see.
            beginUploadRun(runCatching { syncMetadataService.getPendingCount() }.getOrNull()?.takeIf { it > 0 })

            try {
                val accessToken =
                    getAccessToken()
                        ?: return authError()

                // Deliberately sequential. Every one of these writes into the same AT Protocol
                // repo, and a repo write is read-whole-tree, rebuild, write-head. Two of them in
                // flight at once each build a tree missing the other's record, and the later head
                // write wins -- the earlier record survives as an orphaned block but is no longer
                // reachable, so it silently disappears. Running them in parallel bought nothing
                // (they contend on one repo) and cost entries.
                val journalResult = uploadJournals(accessToken)
                val contentResult = uploadContent(accessToken)
                val associationResult = uploadAssociations(accessToken)
                val draftResult = uploadDrafts(accessToken)
                val totalUploaded =
                    journalResult.uploadedItems +
                        contentResult.uploadedItems +
                        associationResult.uploadedItems +
                        draftResult.uploadedItems
                runCompleted = totalUploaded
                val errors =
                    journalResult.errors +
                        contentResult.errors +
                        associationResult.errors +
                        draftResult.errors

                val success = errors.isEmpty()
                if (success) {
                    lastErrorFlow.value = null
                    refreshObservedQuotaFromServer("pending upload")
                } else {
                    lastErrorFlow.value = errors.firstOrNull()
                }

                return SyncResult(
                    success = success,
                    uploadedItems = totalUploaded,
                    errors = errors,
                    lastSyncTime = latestSyncTime(),
                )
            } catch (e: Exception) {
                return handleSyncException(e, "Upload failed")
            } finally {
                syncStateFlow.value = SyncState.Idle
            }
        }

    override suspend fun downloadRemoteChanges(): SyncResult =
        syncMutex.withLock {
            if (!isEnabled) {
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.UNKNOWN_ERROR, "Sync is disabled"),
                        ),
                )
            }

            if (!isAuthenticated()) {
                Napier.w("Download attempted without authentication")
                return SyncResult(
                    success = false,
                    errors =
                        listOf(
                            SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated. Please sign in to sync."),
                        ),
                )
            }

            syncStateFlow.value = SyncState.Syncing

            try {
                val accessToken =
                    getAccessToken()
                        ?: return authError()

                val journalSince = cursorFor(EntityType.JOURNAL)
                val contentSince = cursorFor(EntityType.NOTE)
                val associationSince = cursorFor(EntityType.ASSOCIATION)

                // Sequential for the same reason as the upload side: these share one server
                // instance whose per-request cost is proportional to repo size, and fanning out
                // was enough on its own to starve every request thread it had.
                val journalResult = downloadJournals(accessToken, journalSince)
                val contentResult = downloadContent(accessToken, contentSince)
                val associationResult = downloadAssociations(accessToken, associationSince)
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
                    refreshObservedQuotaFromServer("remote download")
                } else {
                    lastErrorFlow.value = errors.firstOrNull()
                }

                return SyncResult(
                    success = success,
                    downloadedItems = totalDownloaded,
                    conflictsResolved = conflictsResolved,
                    errors = errors,
                    lastSyncTime = latestSyncTime(),
                )
            } catch (e: Exception) {
                return handleSyncException(e, "Download failed")
            } finally {
                syncStateFlow.value = SyncState.Idle
            }
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
                val accessToken = getAccessToken() ?: return@withLock authError()

                val since = cursorFor(entityType)
                val downloadResult = download(accessToken, since)

                beginUploadRunForPending(entityType)
                val uploadResult = upload(accessToken)

                val success = uploadResult.success && downloadResult.success
                if (updatesGlobalSyncState) {
                    if (success) {
                        lastErrorFlow.value = null
                        refreshObservedQuotaFromServer(quotaContext)
                    } else {
                        lastErrorFlow.value = (downloadResult.errors + uploadResult.errors).firstOrNull()
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
        } else if (!isAuthenticated()) {
            SyncResult(success = false, errors = listOf(SyncError(SyncErrorType.AUTHENTICATION_ERROR, "Not authenticated")))
        } else {
            null
        }

    override suspend fun syncContent(): SyncResult =
        syncEntityType(
            entityType = EntityType.NOTE,
            quotaContext = "content sync",
            disabledOrUnauthenticated = ::disabledOrUnauthenticatedResult,
            download = ::downloadContent,
            upload = ::uploadContent,
        )

    override suspend fun syncJournals(): SyncResult =
        syncEntityType(
            entityType = EntityType.JOURNAL,
            quotaContext = "journal sync",
            disabledOrUnauthenticated = ::disabledOrUnauthenticatedResult,
            download = ::downloadJournals,
            upload = ::uploadJournals,
        )

    override suspend fun syncAssociations(): SyncResult =
        syncEntityType(
            entityType = EntityType.ASSOCIATION,
            quotaContext = "association sync",
            disabledOrUnauthenticated = ::disabledOrUnauthenticatedResult,
            download = ::downloadAssociations,
            upload = ::uploadAssociations,
        )

    override suspend fun syncDrafts(): SyncResult =
        syncEntityType(
            entityType = EntityType.DRAFT,
            quotaContext = "draft sync",
            disabledOrUnauthenticated = {
                if (!isEnabled || !isAuthenticated()) SyncResult(success = false) else null
            },
            download = ::downloadDrafts,
            upload = ::uploadDrafts,
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
        enqueueEverythingOnFirstSync()
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

    /**
     * Queues everything already on this device the first time it syncs with a server.
     *
     * Entries can exist before the device has ever talked to a server: written offline, or restored
     * from a backup. Nothing enqueues those retrospectively, so without this they sit on the device
     * for ever while sync reports success and uploads nothing. Signing in on a second device
     * promises exactly this, and it has to be true.
     *
     * Only runs while the server has never been synced with, and enqueueing coalesces, so an entry
     * already waiting is not queued twice.
     */
    private suspend fun enqueueEverythingOnFirstSync() {
        val entityTypes = listOf(EntityType.JOURNAL, EntityType.NOTE)
        val neverSynced = entityTypes.filter { syncMetadataService.getLastSyncTime(it) == null }
        if (neverSynced.isEmpty()) {
            return
        }

        runCatching {
            if (EntityType.JOURNAL in neverSynced) {
                val journals = journalRepository.allJournalsObserved.first()
                journals.forEach { journal ->
                    syncMetadataService.enqueuePending(
                        entityId = journal.id.toString(),
                        entityType = EntityType.JOURNAL,
                        operation = PendingOperation.CREATE,
                    )
                }
                Napier.i("First sync: queued ${'$'}{journals.size} journals already on this device")
            }
            if (EntityType.NOTE in neverSynced) {
                val notes = journalNotesRepository.allNotesObserved.first()
                notes.forEach { note ->
                    syncMetadataService.enqueuePending(
                        entityId = note.uid.toString(),
                        entityType = EntityType.NOTE,
                        operation = PendingOperation.CREATE,
                    )
                }
                Napier.i("First sync: queued ${'$'}{notes.size} entries already on this device")
            }
        }.onFailure { error ->
            Napier.w("Could not queue existing entries for the first sync", error)
        }
    }

    private suspend fun refreshObservedQuotaFromServer(reason: String) {
        val manager = cloudQuotaManager ?: return
        runCatching { manager.syncWithServer() }
            .onFailure { Napier.w("Failed to refresh quota after $reason", it) }
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
            pausedReason = currentPausedReason(authenticated),
            totalForRun = runTotal,
            completedInRun = runCompleted,
        )
    }

    override fun observeDeadLetters(): Flow<List<SyncDeadLetterRecord>> = deadLetterStore.observe()

    override suspend fun retryDeadLetter(id: String) {
        val record = deadLetterStore.list().firstOrNull { it.id == id } ?: return
        val entityType = runCatching { EntityType.valueOf(record.entityType) }.getOrNull()
        val operation = runCatching { PendingOperation.valueOf(record.operation) }.getOrNull()
        if (entityType == null || operation == null) {
            Napier.w("Cannot retry dead-letter $id with type=${record.entityType} op=${record.operation}")
            deadLetterStore.remove(id)
            return
        }
        syncMetadataService.enqueuePending(record.entityId, entityType, operation)
        deadLetterStore.remove(id)
    }

    override suspend fun discardDeadLetter(id: String) {
        deadLetterStore.remove(id)
    }

    /**
     * Gets the last sync error, used for network recovery decisions.
     * Returns null if the last sync succeeded.
     */
    fun getLastSyncError(): SyncError? = lastErrorFlow.value

    private suspend fun getAccessToken(): String? =
        try {
            val session = sessionStorage.getSession()
            if (session != null) {
                session.accessToken
            } else {
                Napier.w("No active session found, cannot retrieve access token")
                null
            }
        } catch (e: Exception) {
            Napier.e("Failed to get access token", e)
            null
        }

    private suspend fun isAuthenticated(): Boolean = sessionStorage.getSession() != null

    /**
     * Helper to create authentication error result.
     */
    private fun authError() =
        SyncResult(
            success = false,
            errors = listOf(SyncError(SyncErrorType.AUTHENTICATION_ERROR, "No access token")),
        )

    /**
     * Helper to handle sync exceptions consistently.
     */
    private fun handleSyncException(
        e: Exception,
        operation: String,
    ): SyncResult {
        val error =
            SyncError(
                type = SyncErrorType.UNKNOWN_ERROR,
                message = "$operation: ${e.message}",
                cause = e,
            )
        lastErrorFlow.value = error
        Napier.e("$operation failed", e)
        return SyncResult(success = false, errors = listOf(error))
    }

    /**
     * Helper to handle CloudApiException consistently.
     * Distinguishes 401 Unauthorized errors from other server errors.
     */
    private fun handleCloudApiError(e: CloudApiException): SyncResult {
        val errorType =
            if (e.statusCode == 401) {
                SyncErrorType.AUTHENTICATION_ERROR
            } else {
                SyncErrorType.SERVER_ERROR
            }
        return SyncResult(
            success = false,
            errors =
                listOf(
                    SyncError(errorType, e.message, e),
                ),
        )
    }

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

    private fun currentDeviceId(): DeviceId =
        deviceIdProvider
            ?.getDeviceId()
            ?.value
            ?.toString()
            ?.let(::DeviceId)
            ?: DeviceId.UNKNOWN

    private fun SyncedDraft.toEditorDraft(): EditorDraft =
        EditorDraft(
            id = id,
            blocks =
                content
                    .takeIf { it.isNotBlank() }
                    ?.let {
                        listOf(
                            SerializableTextBlock(
                                id = id,
                                timestamp = createdAt,
                                content = it,
                            ),
                        )
                    }.orEmpty(),
            selectedJournalIds = journalIds,
            createdAt = createdAt,
            lastModifiedAt = lastUpdated,
        )

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

    private companion object {
        /**
         * Deliberately small. The server reads each record out of the account's repo, so the cost
         * of a page grows with the size of the page *and* the journal behind it; asking for 200 at
         * once stopped completing inside the request timeout once an account held a few hundred
         * entries, and a download that times out fails the whole sync. Raise this once a page is
         * cheap to serve again.
         */
        const val SYNC_PAGE_SIZE = 25
    }

    private suspend fun uploadJournals(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()
            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.JOURNAL)
            if (pendingUploads.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0)
            }

            val syncableRepository = journalRepository as? SyncableJournalRepository
            val journalsById =
                journalRepository.allJournalsObserved
                    .first()
                    .associateBy { it.id.toString() }

            for (pending in pendingUploads) {
                val journalId = runCatching { Uuid.parse(pending.entityId) }.getOrNull()
                if (journalId == null) {
                    errors.add(retryCoordinator.recordUnparsableOutboxEntry(EntityType.JOURNAL, pending.entityId, "journal ID"))
                    continue
                }
                if (!retryCoordinator.shouldAttempt(EntityType.JOURNAL, pending.entityId)) {
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        val result =
                            tokenRefresher.withFreshToken(
                                { token -> cloudJournalDataSource.deleteJournal(token, journalId) },
                                "deleteJournal($journalId)",
                            )
                        if (result.isSuccess) {
                            uploadedCount++
                            recordProgress()
                            retryCoordinator.markUploadSettled(EntityType.JOURNAL, pending.entityId, Clock.System.now(), 0L)
                            Napier.d("Successfully deleted journal: $journalId")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                            val movedToDeadLetter =
                                retryCoordinator.handleRetryFailure(
                                    entityType = EntityType.JOURNAL,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to delete journal $journalId: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to delete journal $journalId", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE,
                    -> {
                        val journal = journalsById[pending.entityId]
                        if (journal == null) {
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.JOURNAL,
                                Clock.System.now(),
                                0L,
                            )
                            continue
                        }

                        val result =
                            if (pending.operation == PendingOperation.CREATE) {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudJournalDataSource.uploadJournal(token, journal) },
                                    "uploadJournal(${journal.id})",
                                )
                            } else {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudJournalDataSource.updateJournal(token, journal) },
                                    "updateJournal(${journal.id})",
                                )
                            }

                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            recordProgress()
                            syncableRepository?.updateSyncMetadata(journalId, upload.serverVersion, upload.syncedAt)
                            retryCoordinator.markUploadSettled(EntityType.JOURNAL, pending.entityId, upload.syncedAt, upload.serverVersion)
                            Napier.d("Successfully uploaded journal: ${journal.id}")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            if ((error as? CloudApiException)?.statusCode == 409) {
                                errors.add(
                                    retryCoordinator.handleUploadConflict(
                                        entityType = EntityType.JOURNAL,
                                        entityId = journal.id.toString(),
                                        itemLabel = "journal ${journal.id}",
                                        conflictLabel = "Journal",
                                        error = error,
                                        localVersion = journal.syncVersion,
                                        localUpdatedAt = journal.lastUpdated,
                                    ),
                                )
                                continue
                            }
                            val movedToDeadLetter =
                                retryCoordinator.handleRetryFailure(
                                    entityType = EntityType.JOURNAL,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    if ((error as? CloudApiException)?.statusCode == 409) {
                                        SyncErrorType.CONFLICT_ERROR
                                    } else {
                                        SyncErrorType.SERVER_ERROR
                                    },
                                    "Failed to upload journal ${journal.id}: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to upload journal ${journal.id}", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Upload journals")
        }
    }

    private suspend fun uploadContent(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()
            mediaDeferredForNetwork = false

            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.NOTE)
            if (pendingUploads.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0)
            }

            val syncableRepository = journalNotesRepository as? SyncableJournalNotesRepository
            val notesById =
                journalNotesRepository.allNotesObserved
                    .first()
                    .associateBy { it.uid.toString() }

            for (pending in pendingUploads) {
                val noteId = runCatching { Uuid.parse(pending.entityId) }.getOrNull()
                if (noteId == null) {
                    errors.add(retryCoordinator.recordUnparsableOutboxEntry(EntityType.NOTE, pending.entityId, "note ID"))
                    continue
                }
                if (!retryCoordinator.shouldAttempt(EntityType.NOTE, pending.entityId)) {
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        val result =
                            tokenRefresher.withFreshToken(
                                { token -> cloudContentDataSource.deleteNote(token, noteId) },
                                "deleteNote($noteId)",
                            )
                        if (result.isSuccess) {
                            uploadedCount++
                            recordProgress()
                            // Deliberately before markUploadSettled: if this throws, the item must
                            // stay pending so the next attempt retries the ref-store cleanup too,
                            // rather than being marked settled with a stale mediaSyncRefStore entry
                            // that nothing will ever clean up again.
                            mediaSyncRefStore.delete(noteId)
                            retryCoordinator.markUploadSettled(EntityType.NOTE, pending.entityId, Clock.System.now(), 0L)
                            Napier.d("Successfully deleted content: $noteId")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                            val movedToDeadLetter =
                                retryCoordinator.handleRetryFailure(
                                    entityType = EntityType.NOTE,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to delete content $noteId: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to delete content $noteId", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE,
                    -> {
                        val note = notesById[pending.entityId]
                        if (note == null) {
                            syncMetadataService.markAsSynced(
                                pending.entityId,
                                EntityType.NOTE,
                                Clock.System.now(),
                                0L,
                            )
                            continue
                        }
                        val mediaRef = note.mediaRefOrNull()
                        val uploadReadyNote =
                            if (mediaRef != null && !mediaTransfer.isRemoteRef(mediaRef)) {
                                if (!dataUsagePolicy.currentMode().shouldSyncMedia()) {
                                    mediaDeferredForNetwork = true
                                    Napier.d("Deferring media upload for note ${note.uid} — data usage policy restricts media sync")
                                    continue
                                } else {
                                    val mediaUpload = mediaTransfer.uploadIfNeeded(accessToken, note)
                                    if (mediaUpload.isFailure) {
                                        val error =
                                            mediaUpload.exceptionOrNull()
                                                ?: Exception("Unknown media upload error")
                                        // This used to `continue` without recording an attempt, so a
                                        // note whose media file no longer exists on disk retried for
                                        // ever and held the whole queue behind it - the upload can
                                        // never succeed, because the bytes are gone. Counting the
                                        // attempt lets it dead-letter like any other stuck upload,
                                        // where it dead-letters silently -- there is currently no UI
                                        // surfacing dead-lettered items for review.
                                        //
                                        // A single existence check isn't proof of that, though --
                                        // MediaManager.exists() answers false for a provider that
                                        // merely failed to answer, not only for bytes truly gone
                                        // (see its doc comment). Only treat this as permanent once
                                        // the *immediately preceding* attempt for this same note was
                                        // also a missing-media miss -- not merely once its generic,
                                        // shared retryCount happens to be >= 1, which an unrelated
                                        // failure (network, server, ...) could have put there -- so
                                        // one bad read doesn't wrongly bury a file that's still there.
                                        val movedToDeadLetter =
                                            retryCoordinator.handleRetryFailure(
                                                entityType = EntityType.NOTE,
                                                pending = pending,
                                                error = error,
                                                permanent =
                                                    error is MissingMediaException &&
                                                        retryCoordinator.previousFailureWasMissingMedia(EntityType.NOTE, pending.entityId),
                                            )
                                        errors.add(
                                            SyncError(
                                                SyncErrorType.STORAGE_ERROR,
                                                "Failed to upload media for note ${note.uid}: ${error.message}",
                                                error,
                                                retryable = !movedToDeadLetter,
                                            ),
                                        )
                                        Napier.w("Skipping note ${note.uid} sync; media upload failed", error)
                                        continue
                                    }
                                    mediaUpload.getOrThrow()
                                }
                            } else {
                                note
                            }

                        val result =
                            if (pending.operation == PendingOperation.CREATE) {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudContentDataSource.uploadNote(token, uploadReadyNote) },
                                    "uploadNote(${note.uid})",
                                )
                            } else {
                                tokenRefresher.withFreshToken(
                                    { token -> cloudContentDataSource.updateNote(token, uploadReadyNote) },
                                    "updateNote(${note.uid})",
                                )
                            }

                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            recordProgress()
                            syncableRepository?.updateSyncMetadata(note, upload.serverVersion, upload.syncedAt)
                            retryCoordinator.markUploadSettled(EntityType.NOTE, pending.entityId, upload.syncedAt, upload.serverVersion)
                            Napier.d("Successfully uploaded content: ${note.uid}")
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                            if ((error as? CloudApiException)?.statusCode == 409) {
                                errors.add(
                                    retryCoordinator.handleUploadConflict(
                                        entityType = EntityType.NOTE,
                                        entityId = note.uid.toString(),
                                        itemLabel = "content ${note.uid}",
                                        conflictLabel = "Content",
                                        error = error,
                                        localVersion = note.syncVersion,
                                        localUpdatedAt = note.lastUpdated,
                                    ),
                                )
                                continue
                            }
                            val movedToDeadLetter =
                                retryCoordinator.handleRetryFailure(
                                    entityType = EntityType.NOTE,
                                    pending = pending,
                                    error = error,
                                )
                            errors.add(
                                SyncError(
                                    if ((error as? CloudApiException)?.statusCode == 409) {
                                        SyncErrorType.CONFLICT_ERROR
                                    } else {
                                        SyncErrorType.SERVER_ERROR
                                    },
                                    "Failed to upload content ${note.uid}: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                            Napier.w("Failed to upload content ${note.uid}", error)
                            if (movedToDeadLetter) {
                                continue
                            }
                        }
                    }
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Upload content")
        }
    }

    private suspend fun uploadAssociations(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()

            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.ASSOCIATION)
            if (pendingUploads.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0)
            }

            val pendingById = pendingUploads.associateBy { it.entityId }
            val createAssociations = mutableListOf<JournalContentAssociation>()
            val createIds = mutableListOf<String>()
            val deleteAssociations = mutableListOf<JournalContentAssociation>()
            val deleteIds = mutableListOf<String>()

            pendingUploads.forEach { pending ->
                if (!retryCoordinator.shouldAttempt(EntityType.ASSOCIATION, pending.entityId)) {
                    return@forEach
                }
                val key = AssociationPendingKey.fromPendingId(pending.entityId)
                if (key == null) {
                    errors.add(retryCoordinator.recordUnparsableOutboxEntry(EntityType.ASSOCIATION, pending.entityId, "association key"))
                    return@forEach
                }

                val association =
                    JournalContentAssociation(
                        journalId = key.journalId,
                        contentId = key.contentId,
                        createdAt = Clock.System.now(),
                    )

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        deleteAssociations.add(association)
                        deleteIds.add(pending.entityId)
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE,
                    -> {
                        createAssociations.add(association)
                        createIds.add(pending.entityId)
                    }
                }
            }

            if (createAssociations.isNotEmpty()) {
                val result =
                    tokenRefresher.withFreshToken(
                        { token -> cloudAssociationDataSource.uploadAssociations(token, createAssociations) },
                        "uploadAssociations(${createAssociations.size} items)",
                    )
                if (result.isSuccess) {
                    val uploadedAt = result.getOrThrow()
                    createIds.forEach { id -> retryCoordinator.markUploadSettled(EntityType.ASSOCIATION, id, uploadedAt, 0L) }
                    uploadedCount += createAssociations.size
                    recordProgress(createAssociations.size)
                    Napier.d("Successfully uploaded associations: ${createAssociations.size}")
                } else {
                    val error = result.exceptionOrNull() ?: Exception("Unknown upload error")
                    var movedToDeadLetter = false
                    createIds.forEach { id ->
                        val pending = pendingById[id] ?: return@forEach
                        if (retryCoordinator.handleRetryFailure(EntityType.ASSOCIATION, pending, error)) {
                            movedToDeadLetter = true
                        }
                    }
                    errors.add(
                        SyncError(
                            SyncErrorType.SERVER_ERROR,
                            "Failed to upload associations: ${error.message}",
                            error,
                            retryable = !movedToDeadLetter,
                        ),
                    )
                    Napier.w("Failed to upload associations", error)
                }
            }

            if (deleteAssociations.isNotEmpty()) {
                val result =
                    tokenRefresher.withFreshToken(
                        { token -> cloudAssociationDataSource.deleteAssociations(token, deleteAssociations) },
                        "deleteAssociations(${deleteAssociations.size} items)",
                    )
                if (result.isSuccess) {
                    val deletedAt = Clock.System.now()
                    deleteIds.forEach { id -> retryCoordinator.markUploadSettled(EntityType.ASSOCIATION, id, deletedAt, 0L) }
                    uploadedCount += deleteAssociations.size
                    recordProgress(deleteAssociations.size)
                    Napier.d("Successfully deleted associations: ${deleteAssociations.size}")
                } else {
                    val error = result.exceptionOrNull() ?: Exception("Unknown delete error")
                    var movedToDeadLetter = false
                    deleteIds.forEach { id ->
                        val pending = pendingById[id] ?: return@forEach
                        if (retryCoordinator.handleRetryFailure(EntityType.ASSOCIATION, pending, error)) {
                            movedToDeadLetter = true
                        }
                    }
                    errors.add(
                        SyncError(
                            SyncErrorType.SERVER_ERROR,
                            "Failed to delete associations: ${error.message}",
                            error,
                            retryable = !movedToDeadLetter,
                        ),
                    )
                    Napier.w("Failed to delete associations", error)
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Upload associations")
        }
    }

    private suspend fun uploadDrafts(accessToken: String): SyncResult {
        return try {
            var uploadedCount = 0
            val errors = mutableListOf<SyncError>()
            val pendingUploads = syncMetadataService.getPendingUploads(EntityType.DRAFT)
            if (pendingUploads.isEmpty()) {
                return SyncResult(success = true, uploadedItems = 0)
            }

            val draftsById = journalRepository.getAllDrafts().associateBy { it.id.toString() }
            val deviceId = currentDeviceId()

            for (pending in pendingUploads) {
                val draftId = runCatching { Uuid.parse(pending.entityId) }.getOrNull()
                if (draftId == null) {
                    errors.add(retryCoordinator.recordUnparsableOutboxEntry(EntityType.DRAFT, pending.entityId, "draft ID"))
                    continue
                }
                if (!retryCoordinator.shouldAttempt(EntityType.DRAFT, pending.entityId)) {
                    continue
                }

                when (pending.operation) {
                    PendingOperation.DELETE -> {
                        val result =
                            tokenRefresher.withFreshToken(
                                { token -> cloudDraftDataSource.deleteDraft(token, draftId) },
                                "deleteDraft($draftId)",
                            )
                        if (result.isSuccess) {
                            uploadedCount++
                            recordProgress()
                            retryCoordinator.markUploadSettled(EntityType.DRAFT, pending.entityId, Clock.System.now(), 0L)
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown draft delete error")
                            val movedToDeadLetter = retryCoordinator.handleRetryFailure(EntityType.DRAFT, pending, error)
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to delete draft $draftId: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                        }
                    }
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE,
                    -> {
                        val draft = draftsById[pending.entityId]
                        if (draft == null) {
                            syncMetadataService.markAsSynced(pending.entityId, EntityType.DRAFT, Clock.System.now(), 0L)
                            continue
                        }

                        val result =
                            tokenRefresher.withFreshToken(
                                { token -> cloudDraftDataSource.uploadDraft(token, draft, deviceId) },
                                "uploadDraft(${draft.id})",
                            )
                        if (result.isSuccess) {
                            val upload = result.getOrThrow()
                            uploadedCount++
                            recordProgress()
                            retryCoordinator.markUploadSettled(EntityType.DRAFT, pending.entityId, upload.syncedAt, upload.serverVersion)
                        } else {
                            val error = result.exceptionOrNull() ?: Exception("Unknown draft upload error")
                            val movedToDeadLetter = retryCoordinator.handleRetryFailure(EntityType.DRAFT, pending, error)
                            errors.add(
                                SyncError(
                                    SyncErrorType.SERVER_ERROR,
                                    "Failed to upload draft ${draft.id}: ${error.message}",
                                    error,
                                    retryable = !movedToDeadLetter,
                                ),
                            )
                        }
                    }
                }
            }

            SyncResult(success = errors.isEmpty(), uploadedItems = uploadedCount, errors = errors)
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Upload drafts")
        }
    }

    private suspend fun downloadJournals(
        accessToken: String,
        since: Instant,
    ): SyncResult {
        val syncableRepository = journalRepository as? SyncableJournalRepository
        return downloadEngine.download(
            strategy =
                DownloadStrategy(
                    entityType = EntityType.JOURNAL,
                    logLabel = "journal",
                    fetchChanges = { token, cursor ->
                        tokenRefresher
                            .withFreshToken(
                                { t -> cloudJournalDataSource.getJournalChanges(t, cursor, SYNC_PAGE_SIZE) },
                                "getJournalChanges",
                            ).map { ChangesPage(it.changes, it.deletions, it.lastSyncTimestamp, it.hasMore) }
                    },
                    localItems = { journalRepository.allJournalsObserved.first().associateBy { it.id } },
                    idOf = { it.id },
                    syncVersionOf = { it.syncVersion },
                    lastUpdatedOf = { it.lastUpdated },
                    conflictResolver = journalConflictResolver,
                    applyCreate = { journal ->
                        if (syncableRepository != null) syncableRepository.createFromSync(journal) else journalRepository.create(journal)
                    },
                    applyReplace = { _, replacement ->
                        if (syncableRepository != null) {
                            syncableRepository.updateFromSync(replacement)
                        } else {
                            journalRepository.update(replacement)
                        }
                    },
                    applyDelete = { id ->
                        if (syncableRepository != null) syncableRepository.deleteFromSync(id) else journalRepository.delete(id)
                    },
                ),
            accessToken = accessToken,
            since = since,
        )
    }

    private suspend fun downloadContent(
        accessToken: String,
        since: Instant,
    ): SyncResult {
        val syncableRepository = journalNotesRepository as? SyncableJournalNotesRepository
        return downloadEngine.download(
            strategy =
                DownloadStrategy(
                    entityType = EntityType.NOTE,
                    logLabel = "note",
                    fetchChanges = { token, cursor ->
                        tokenRefresher
                            .withFreshToken(
                                { t -> cloudContentDataSource.getContentChanges(t, cursor, SYNC_PAGE_SIZE) },
                                "getContentChanges",
                            ).map { ChangesPage(it.changes, it.deletions, it.lastSyncTimestamp, it.hasMore) }
                    },
                    localItems = { journalNotesRepository.allNotesObserved.first().associateBy { it.uid } },
                    idOf = { it.uid },
                    syncVersionOf = { it.syncVersion },
                    lastUpdatedOf = { it.lastUpdated },
                    conflictResolver = noteConflictResolver,
                    hydrate = { token, note -> mediaTransfer.downloadIfNeeded(token, note) },
                    applyCreate = { note ->
                        if (syncableRepository != null) syncableRepository.createFromSync(note) else journalNotesRepository.create(note)
                    },
                    // Notes have no update-in-place sync path -- a replacement is always applied as
                    // remove-then-recreate, matching how a fresh download from the server is applied.
                    applyReplace = { existing, replacement ->
                        if (syncableRepository != null) {
                            syncableRepository.deleteFromSync(existing.uid)
                            syncableRepository.createFromSync(replacement)
                        } else {
                            journalNotesRepository.remove(existing)
                            journalNotesRepository.create(replacement)
                        }
                    },
                    applyDelete = { id ->
                        if (syncableRepository != null) syncableRepository.deleteFromSync(id) else journalNotesRepository.removeById(id)
                    },
                    afterDelete = { id -> mediaSyncRefStore.delete(id) },
                ),
            accessToken = accessToken,
            since = since,
        )
    }

    private suspend fun downloadDrafts(
        accessToken: String,
        since: Instant,
    ): SyncResult =
        try {
            val pendingDrafts =
                syncMetadataService
                    .getPendingUploads(EntityType.DRAFT)
                    .map { it.entityId }
                    .toSet()
            val localDrafts =
                journalRepository
                    .getAllDrafts()
                    .associateBy { it.id }
                    .toMutableMap()
            val syncableDraftRepository = journalRepository as? SyncableDraftRepository

            var cursor = since
            var totalDownloaded = 0
            var totalConflicts = 0
            val errors = mutableListOf<SyncError>()

            while (true) {
                val result = cloudDraftDataSource.getDraftChanges(accessToken, cursor, SYNC_PAGE_SIZE).getOrThrow()

                val batchResult =
                    transactionManager.withTransaction {
                        var downloadedCount = 0
                        var conflictsResolved = 0
                        val batchErrors = mutableListOf<SyncError>()

                        for (remoteDraft in result.changes) {
                            try {
                                val existingDraft = localDrafts[remoteDraft.id]
                                val hasPendingLocal = pendingDrafts.contains(remoteDraft.id.toString())
                                if (hasPendingLocal) {
                                    conflictsResolved++
                                    Napier.w("Skipping draft update for ${remoteDraft.id} due to local pending changes")
                                    downloadEngine.recordConflict(
                                        entityType = EntityType.DRAFT,
                                        entityId = remoteDraft.id.toString(),
                                        reason = "Local pending draft changes vs remote update",
                                        localVersion = null,
                                        remoteVersion = remoteDraft.serverVersion,
                                        localUpdatedAt = existingDraft?.lastModifiedAt,
                                        remoteUpdatedAt = remoteDraft.lastUpdated,
                                    )
                                    continue
                                }

                                if (existingDraft != null && existingDraft.lastModifiedAt > remoteDraft.lastUpdated) {
                                    syncMetadataService.enqueuePending(
                                        entityId = existingDraft.id.toString(),
                                        entityType = EntityType.DRAFT,
                                        operation = PendingOperation.UPDATE,
                                    )
                                    conflictsResolved++
                                    Napier.w("Preserving newer local draft ${existingDraft.id} over older remote draft")
                                    continue
                                }

                                val draft = remoteDraft.toEditorDraft()
                                if (syncableDraftRepository != null) {
                                    syncableDraftRepository.saveDraftFromSync(draft)
                                } else {
                                    journalRepository.saveDraft(draft)
                                }
                                localDrafts[draft.id] = draft
                                downloadedCount++
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to apply draft change for ${remoteDraft.id}: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to apply draft change for ${remoteDraft.id}", e)
                            }
                        }

                        for (draftId in result.deletions) {
                            try {
                                val existingDraft = localDrafts[draftId]
                                val hasPendingLocal =
                                    existingDraft != null &&
                                        pendingDrafts.contains(draftId.toString())
                                if (hasPendingLocal) {
                                    conflictsResolved++
                                    Napier.w("Skipping draft deletion for $draftId due to local pending changes")
                                    downloadEngine.recordConflict(
                                        entityType = EntityType.DRAFT,
                                        entityId = draftId.toString(),
                                        reason = "Local pending draft changes vs remote deletion",
                                        localVersion = null,
                                        remoteVersion = null,
                                        localUpdatedAt = existingDraft.lastModifiedAt,
                                        remoteUpdatedAt = null,
                                    )
                                    continue
                                }

                                if (syncableDraftRepository != null) {
                                    syncableDraftRepository.deleteDraftFromSync(draftId)
                                } else {
                                    journalRepository.deleteDraft(draftId)
                                }
                                localDrafts.remove(draftId)
                                downloadedCount++
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to delete draft $draftId: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to delete draft $draftId", e)
                            }
                        }

                        BatchResult(downloadedCount, conflictsResolved, batchErrors)
                    }

                totalDownloaded += batchResult.downloadedCount
                totalConflicts += batchResult.conflictsResolved
                errors.addAll(batchResult.errors)

                if (batchResult.errors.isNotEmpty()) {
                    break
                }

                if (result.changes.isEmpty() && result.deletions.isEmpty()) {
                    break
                }

                syncMetadataService.updateLastSyncTime(EntityType.DRAFT, result.lastSyncTimestamp)

                if (!result.hasMore) {
                    break
                }

                if (result.lastSyncTimestamp <= cursor) {
                    Napier.w("Draft sync pagination cursor did not advance (since=$cursor, last=${result.lastSyncTimestamp})")
                    break
                }

                cursor = result.lastSyncTimestamp
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = totalDownloaded,
                conflictsResolved = totalConflicts,
                errors = errors,
            )
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Download drafts")
        }

    private suspend fun downloadAssociations(
        accessToken: String,
        since: Instant,
    ): SyncResult =
        try {
            val syncableRepository = journalContentRepository as? SyncableJournalContentRepository
            val pendingAssociations =
                syncMetadataService
                    .getPendingUploads(EntityType.ASSOCIATION)
                    .map { it.entityId }
                    .toSet()

            var cursor = since
            var hasMore = true
            var totalDownloaded = 0
            var totalConflicts = 0
            val errors = mutableListOf<SyncError>()

            while (hasMore) {
                val result =
                    tokenRefresher
                        .withFreshToken(
                            { token -> cloudAssociationDataSource.getAssociationChanges(token, cursor, SYNC_PAGE_SIZE) },
                            "getAssociationChanges",
                        ).getOrThrow()

                val batchResult =
                    transactionManager.withTransaction {
                        var downloadedCount = 0
                        var conflictsResolved = 0
                        val batchErrors = mutableListOf<SyncError>()

                        for (association in result.additions) {
                            try {
                                val pendingKey = AssociationPendingKey(association.journalId, association.contentId).toPendingId()
                                if (pendingAssociations.contains(pendingKey)) {
                                    conflictsResolved++
                                    Napier.w("Skipping association add for $pendingKey due to local pending changes")
                                    downloadEngine.recordConflict(
                                        entityType = EntityType.ASSOCIATION,
                                        entityId = pendingKey,
                                        reason = "Local pending changes vs remote association add",
                                        localVersion = null,
                                        remoteVersion = association.syncVersion,
                                        localUpdatedAt = null,
                                        remoteUpdatedAt = null,
                                    )
                                    continue
                                }

                                if (syncableRepository != null) {
                                    syncableRepository.addContentToJournalFromSync(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                } else {
                                    journalContentRepository.addContentToJournal(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                }
                                downloadedCount++
                                Napier.d("Added association: journal ${association.journalId} -> content ${association.contentId}")
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to add association ${association.journalId}->${association.contentId}: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to add association ${association.journalId}->${association.contentId}", e)
                            }
                        }

                        for (association in result.deletions) {
                            try {
                                val pendingKey = AssociationPendingKey(association.journalId, association.contentId).toPendingId()
                                if (pendingAssociations.contains(pendingKey)) {
                                    conflictsResolved++
                                    Napier.w("Skipping association delete for $pendingKey due to local pending changes")
                                    downloadEngine.recordConflict(
                                        entityType = EntityType.ASSOCIATION,
                                        entityId = pendingKey,
                                        reason = "Local pending changes vs remote association delete",
                                        localVersion = null,
                                        remoteVersion = null,
                                        localUpdatedAt = null,
                                        remoteUpdatedAt = null,
                                    )
                                    continue
                                }

                                if (syncableRepository != null) {
                                    syncableRepository.removeContentFromJournalFromSync(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                } else {
                                    journalContentRepository.removeContentFromJournal(
                                        contentId = association.contentId,
                                        journalId = association.journalId,
                                    )
                                }
                                downloadedCount++
                                Napier.d("Removed association: journal ${association.journalId} -> content ${association.contentId}")
                            } catch (e: Exception) {
                                batchErrors.add(
                                    SyncError(
                                        SyncErrorType.UNKNOWN_ERROR,
                                        "Failed to remove association ${association.journalId}->${association.contentId}: ${e.message}",
                                        e,
                                    ),
                                )
                                Napier.e("Failed to remove association ${association.journalId}->${association.contentId}", e)
                            }
                        }

                        BatchResult(downloadedCount, conflictsResolved, batchErrors)
                    }

                totalDownloaded += batchResult.downloadedCount
                totalConflicts += batchResult.conflictsResolved
                errors.addAll(batchResult.errors)

                if (batchResult.errors.isNotEmpty()) {
                    break
                }

                syncMetadataService.updateLastSyncTime(EntityType.ASSOCIATION, result.lastSyncTimestamp)

                if (!result.hasMore) {
                    break
                }

                if (result.lastSyncTimestamp <= cursor) {
                    Napier.w("Association sync pagination cursor did not advance (since=$cursor, last=${result.lastSyncTimestamp})")
                    break
                }

                cursor = result.lastSyncTimestamp
            }

            SyncResult(
                success = errors.isEmpty(),
                downloadedItems = totalDownloaded,
                conflictsResolved = totalConflicts,
                errors = errors,
            )
        } catch (e: CloudApiException) {
            handleCloudApiError(e)
        } catch (e: Exception) {
            handleSyncException(e, "Download associations")
        }
}

/**
 * The media a note points at is no longer on disk, so no number of retries can upload it.
 */
class MissingMediaException(
    mediaRef: String,
    cause: Throwable,
) : Exception("Media no longer exists at $mediaRef: ${cause.message}", cause)
