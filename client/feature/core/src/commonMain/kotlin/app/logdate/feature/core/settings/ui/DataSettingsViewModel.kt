package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.data.maintenance.DataIntegrityService
import app.logdate.client.data.maintenance.IntegrityRepairResult
import app.logdate.client.data.maintenance.IntegrityReport
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.domain.quota.ObserveCloudQuotaUseCase
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.CloudStorageQuota
import app.logdate.shared.model.ServerCapability
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

data class DataSettingsState(
    val quotaState: CloudStorageQuota?,
    val hasAuthoritativeQuota: Boolean,
    val isQuotaAvailable: Boolean,
    val integrityState: IntegrityState,
    val conflictsState: ConflictsState,
    val syncStatus: app.logdate.client.sync.SyncStatus?,
    val isAuthenticated: Boolean,
    val isBackgroundSyncEnabled: Boolean,
)

data class IntegrityState(
    val isChecking: Boolean = false,
    val isRepairing: Boolean = false,
    val lastReport: IntegrityReport? = null,
    val lastRepair: IntegrityRepairResult? = null,
    val errorMessage: String? = null,
)

/**
 * The outcome of a manual sync, for the UI to report once.
 *
 * Sync Now previously logged its result and nothing else, so a refused sync, a failed sync and a
 * successful one were indistinguishable from the outside - the button appeared to do nothing.
 */
sealed interface SyncFeedback {
    /** Sync was refused because there is no account yet; the user needs to sign in, not retry. */
    data object NeedsAccount : SyncFeedback

    data class Succeeded(
        val uploadedItems: Int,
        val downloadedItems: Int,
    ) : SyncFeedback

    /** A sync has been handed to background sync; progress shows in the sync status. */
    data object Started : SyncFeedback

    data class Failed(
        val message: String,
    ) : SyncFeedback
}

data class ConflictsState(
    val conflicts: List<SyncConflictRecord> = emptyList(),
    val isLoading: Boolean = false,
    val lastUpdated: Instant? = null,
    val errorMessage: String? = null,
)

class DataSettingsViewModel(
    observeCloudQuotaUseCase: ObserveCloudQuotaUseCase,
    private val syncManager: SyncManager,
    private val sessionStorage: SessionStorage,
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val configRepository: LogDateConfigRepository,
    private val dataIntegrityService: DataIntegrityService,
    private val conflictStore: SyncConflictStore,
) : ViewModel() {
    private val _integrityState = MutableStateFlow(IntegrityState())
    val integrityState: StateFlow<IntegrityState> = _integrityState.asStateFlow()

    private val _conflictsState = MutableStateFlow(ConflictsState())
    val conflictsState: StateFlow<ConflictsState> = _conflictsState.asStateFlow()

    private val _syncFeedback = MutableStateFlow<SyncFeedback?>(null)
    val syncFeedback: StateFlow<SyncFeedback?> = _syncFeedback.asStateFlow()

    // combine() waits for every source before it emits anything, so a slow or failing quota
    // fetch used to hold back the entire screen -- including whether the user is signed in, which
    // the initial value hard-codes to false. That showed "Create Account" to someone who was
    // signed in and syncing. Neither quota flow gates the rest of the screen any more.
    private val quotaFlow: Flow<CloudStorageQuota?> =
        observeCloudQuotaUseCase()
            .map<CloudStorageQuota, CloudStorageQuota?> { it }
            .onStart { emit(null) }
    private val sessionFlow = sessionStorage.getSessionFlow()
    private val backgroundSyncEnabledFlow = preferencesDataSource.backgroundSyncEnabled
    private val quotaAvailabilityFlow =
        configRepository.serverDescriptor
            .combine(configRepository.backendUrl) { descriptor, backendUrl ->
                when {
                    descriptor != null -> descriptor.hasCapability(ServerCapability.MANAGED_QUOTA)
                    backendUrl == DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL -> true
                    else -> false
                }
            }.onStart { emit(true) }

    private val syncStatusFlow =
        flow {
            while (true) {
                val status = syncManager.getSyncStatus()
                emit(status)
                delay(5000)
            }
        }

    private val sourceStateFlow =
        combine(
            quotaFlow,
            quotaAvailabilityFlow,
            _integrityState,
            _conflictsState,
        ) { quotaState, isQuotaAvailable, integrityState, conflictsState ->
            DataSettingsState(
                quotaState = quotaState,
                hasAuthoritativeQuota = true,
                isQuotaAvailable = isQuotaAvailable,
                integrityState = integrityState,
                conflictsState = conflictsState,
                syncStatus = null,
                isAuthenticated = false,
                isBackgroundSyncEnabled = true,
            )
        }

    val uiState: StateFlow<DataSettingsState> =
        combine(
            sourceStateFlow,
            syncStatusFlow,
            sessionFlow,
            backgroundSyncEnabledFlow,
        ) { sourceState, syncStatus, session, backgroundSyncEnabled ->
            sourceState.copy(
                syncStatus = syncStatus,
                isAuthenticated = session != null,
                isBackgroundSyncEnabled = backgroundSyncEnabled,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DataSettingsState(
                quotaState = null,
                hasAuthoritativeQuota = false,
                isQuotaAvailable = true,
                integrityState = IntegrityState(),
                conflictsState = ConflictsState(),
                syncStatus = null,
                isAuthenticated = false,
                isBackgroundSyncEnabled = true,
            ),
        )

    init {
        startConflictPolling()
    }

    fun runIntegrityCheck() {
        viewModelScope.launch {
            _integrityState.update { it.copy(isChecking = true, errorMessage = null) }
            runCatching { dataIntegrityService.audit() }
                .onSuccess { report ->
                    _integrityState.update {
                        it.copy(isChecking = false, lastReport = report, errorMessage = null)
                    }
                }.onFailure { error ->
                    Napier.e("Integrity audit failed", error)
                    _integrityState.update {
                        it.copy(isChecking = false, errorMessage = error.message ?: "Integrity audit failed")
                    }
                }
        }
    }

    fun repairIntegrity() {
        viewModelScope.launch {
            _integrityState.update { it.copy(isRepairing = true, errorMessage = null) }
            runCatching { dataIntegrityService.repair() }
                .onSuccess { result ->
                    val refreshed = runCatching { dataIntegrityService.audit() }.getOrNull()
                    _integrityState.update {
                        it.copy(
                            isRepairing = false,
                            lastRepair = result,
                            lastReport = refreshed ?: it.lastReport,
                            errorMessage = null,
                        )
                    }
                }.onFailure { error ->
                    Napier.e("Integrity repair failed", error)
                    _integrityState.update {
                        it.copy(isRepairing = false, errorMessage = error.message ?: "Integrity repair failed")
                    }
                }
        }
    }

    fun refreshConflicts(force: Boolean = false) {
        viewModelScope.launch {
            val shouldShowLoading = force || _conflictsState.value.conflicts.isEmpty()
            if (shouldShowLoading) {
                _conflictsState.update { it.copy(isLoading = true, errorMessage = null) }
            } else {
                _conflictsState.update { it.copy(errorMessage = null) }
            }
            runCatching { conflictStore.list() }
                .onSuccess { conflicts ->
                    _conflictsState.update {
                        it.copy(
                            conflicts = conflicts.sortedByDescending { record -> record.detectedAt },
                            isLoading = false,
                            lastUpdated = Clock.System.now(),
                            errorMessage = null,
                        )
                    }
                }.onFailure { error ->
                    Napier.e("Failed to load sync conflicts", error)
                    _conflictsState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load conflicts",
                        )
                    }
                }
        }
    }

    fun clearConflicts() {
        viewModelScope.launch {
            runCatching { conflictStore.clear() }
                .onFailure { error -> Napier.e("Failed to clear sync conflicts", error) }
            refreshConflicts()
        }
    }

    private fun startConflictPolling() {
        viewModelScope.launch {
            while (isActive) {
                refreshConflicts()
                delay(10_000)
            }
        }
    }

    /**
     * Hands the whole sync to the platform's background worker rather than running it here.
     *
     * Running it in this ViewModel tied it to the screen that started it: switching apps cancelled
     * the coroutine mid-run ("Job was cancelled") and hundreds of entries stopped uploading. The
     * worker survives the app going away, and progress is visible from the sync status either way,
     * so there is nothing to report back here beyond needing an account.
     */
    fun syncNow() {
        viewModelScope.launch {
            try {
                if (sessionStorage.getSession() == null) {
                    _syncFeedback.value = SyncFeedback.NeedsAccount
                    return@launch
                }
                Napier.d("Handing a manual sync to background sync")
                syncManager.sync(startNow = true)
                _syncFeedback.value = SyncFeedback.Started
            } catch (e: Exception) {
                Napier.e("Could not start sync", e)
                _syncFeedback.value = SyncFeedback.Failed(describeSyncFailure(null))
            }
        }
    }

    /** Clears the last sync outcome once the UI has shown it. */
    fun consumeSyncFeedback() {
        _syncFeedback.value = null
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setBackgroundSyncEnabled(enabled)
        }
    }
}

/**
 * Sync errors carry exception text meant for the log -- entity ids, on-disk paths, the package
 * name. The snackbar says what happened and what to do about it, and leaves the detail to Napier.
 */
private fun describeSyncFailure(type: SyncErrorType?): String =
    when (type) {
        SyncErrorType.NETWORK_ERROR -> "Couldn't reach LogDate Cloud. Check your connection and try again."
        SyncErrorType.SERVER_ERROR -> "LogDate Cloud had a problem. Try again in a moment."
        SyncErrorType.STORAGE_ERROR -> "Some changes couldn't be uploaded. Review them in Sync issues."
        SyncErrorType.CONFLICT_ERROR -> "Some changes need review before they can sync."
        else -> "Sync didn't finish. Try again in a moment."
    }
