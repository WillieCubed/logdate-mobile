package app.logdate.client.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.networking.DataRestriction
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.shouldSyncMedia
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.CloudQuotaManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * Owns [DefaultSyncManager]'s [SyncStatus] snapshot: republishing it on every state/error/session
 * transition, tracking the current upload run's progress, and deciding [SyncPausedReason].
 *
 * @param syncStateFlow Read-only view of state [DefaultSyncManager] itself still writes directly
 *   (it gates control flow there -- `syncMutex`-scoped state transitions), so this class only
 *   ever reads it.
 * @param lastErrorFlow Read *and* written here: every other write happens directly on
 *   [DefaultSyncManager] (clearing/setting it around an upload or download run), but mapping an
 *   exception into a [SyncResult] -- [handleSyncException]/[handleCloudApiError] -- is itself
 *   part of deciding what the published status's `lastError` should say, so it lives with the
 *   rest of that decision instead of splitting the two.
 * @param latestSyncTime,isEnabled Reused from [DefaultSyncManager] rather than duplicated: the
 *   former is independently useful for a [SyncResult]'s own `lastSyncTime`, and the latter is a
 *   plain field with no reason to move.
 */
internal class SyncStatusPublisher(
    private val sessionStorage: SessionStorage,
    private val syncMetadataService: SyncMetadataService,
    private val dataUsagePolicy: DataUsagePolicy,
    private val cloudQuotaManager: CloudQuotaManager?,
    private val syncStateFlow: StateFlow<SyncState>,
    private val lastErrorFlow: MutableStateFlow<SyncError?>,
    private val syncScope: CoroutineScope,
    private val latestSyncTime: suspend () -> Instant?,
    private val isEnabled: () -> Boolean,
) {
    /**
     * Whether the last upload pass held a photo or video back for want of Wi-Fi.
     *
     * Reset at the start of each upload pass so the status reflects the current run rather than
     * a deferral the device has long since worked through.
     */
    private var mediaDeferredForNetwork = false

    /** What the current upload run set out to do, and how far through it is. See [SyncStatus]. */
    var runTotal: Int? = null
        private set
    var runCompleted = 0
        private set

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
    val syncStatusFlow: StateFlow<SyncStatus> = _syncStatusFlow.asStateFlow()

    init {
        // Republish status on each internal state/error transition so observers don't have to poll.
        syncScope.launch {
            combine(syncStateFlow, lastErrorFlow) { state, error -> state to error }
                .collect { publish() }
        }
        // Republish whenever the session changes — sign-in flips isEnabled true, sign-out flips
        // it false, and the UI needs to react without waiting for the next sync transition.
        syncScope.launch {
            sessionStorage.getSessionFlow().collect { publish() }
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
    suspend fun publish() {
        val authenticated = sessionStorage.getSession() != null
        val pendingCount =
            runCatching { syncMetadataService.getPendingCount() }
                // Falling back to zero renders as "everything is backed up", which is exactly the
                // healthy state -- so a metadata failure would otherwise look like success.
                .onFailure { Napier.e("Could not read the pending upload count", it) }
                .getOrDefault(0)
        _syncStatusFlow.value =
            SyncStatus(
                isEnabled = authenticated && isEnabled(),
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
     * not just `uploadPendingChanges`, but also the opportunistic, single-entity-type entry
     * points (`syncContent`, `syncJournals`, `syncAssociations`, `syncDrafts`) that repositories
     * call right after a local write. Without this, an opportunistic sync of one item run right
     * after an earlier five-item run left [runTotal] at the stale value of 5 while [runCompleted]
     * climbed past it (e.g. reporting "6 of 5" instead of "1 of 1").
     */
    suspend fun beginRun(total: Int?) {
        runTotal = total
        runCompleted = 0
        publish()
    }

    /** [beginRun] scoped to how many of [entityType] are currently pending. */
    suspend fun beginRunForPending(entityType: EntityType) {
        beginRun(runCatching { syncMetadataService.getPendingUploads(entityType).size }.getOrNull()?.takeIf { it > 0 })
    }

    /**
     * Advances the upload run's progress by [count] items and republishes status immediately.
     *
     * Called from inside each upload loop as items actually finish, rather than only once at the
     * start and end of a run -- without this, [runCompleted] sat at zero for the whole run and
     * jumped straight to the total, so a determinate progress indicator never actually looked
     * like it was moving.
     *
     * Deliberately does *not* go through [publish]: that re-runs
     * [SyncMetadataService.getPendingCount], which takes a mutex and loops every [EntityType]
     * doing a promotion check before its final COUNT query. Calling that once per uploaded item
     * turned a 500-note backup into roughly 500x that overhead for no benefit -- the pending
     * badge doesn't need to be exactly current between every single item, only the run counters
     * ([runTotal]/[runCompleted]) do. This reuses the rest of the last-published snapshot and
     * only touches the two fields that actually changed; a fresh, fully-recomputed status is
     * still published on every state transition (run start/end) via [publish].
     */
    fun recordProgress(count: Int = 1) {
        runCompleted += count
        _syncStatusFlow.value =
            _syncStatusFlow.value.copy(
                totalForRun = runTotal,
                completedInRun = runCompleted,
            )
    }

    /** Overrides [runCompleted] directly once a run's true final count is known. */
    fun setRunCompleted(count: Int) {
        runCompleted = count
    }

    fun setMediaDeferredForNetwork(deferred: Boolean) {
        mediaDeferredForNetwork = deferred
    }

    /**
     * Why the backup cannot progress right now, or null if nothing is holding it back.
     *
     * Reported whether or not anything is queued: background data being off means entries
     * written from here on will not back up either, and the user should hear that before
     * they lose a week of writing to a setting they do not know is on.
     */
    suspend fun currentPausedReason(authenticated: Boolean): SyncPausedReason? {
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

    suspend fun refreshObservedQuotaFromServer(reason: String) {
        val manager = cloudQuotaManager ?: return
        runCatching { manager.syncWithServer() }
            .onFailure { Napier.w("Failed to refresh quota after $reason", it) }
    }

    /** Helper to handle sync exceptions consistently. */
    fun handleSyncException(
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
    fun handleCloudApiError(e: CloudApiException): SyncResult {
        Napier.e("Sync failed with ${e.statusCode ?: "no"} status (${e.errorCode}): ${e.message}", e)
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
}
