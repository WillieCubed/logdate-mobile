package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.data.maintenance.DataIntegrityService
import app.logdate.client.data.maintenance.IntegrityRepairResult
import app.logdate.client.data.maintenance.IntegrityReport
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.domain.quota.ObserveCloudQuotaUseCase
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.CloudStorageQuota
import app.logdate.shared.model.ServerCapability
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

data class DataSettingsState(
    val quotaState: CloudStorageQuota,
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

    private val quotaFlow = observeCloudQuotaUseCase()
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
            }

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
                quotaState = quotaState.orDefault(),
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
                quotaState = (null as CloudStorageQuota?).orDefault(),
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

    fun syncNow() {
        viewModelScope.launch {
            try {
                Napier.d("Starting manual sync...")
                val result = syncManager.fullSync()
                if (result.success) {
                    Napier.i("Sync completed successfully: uploaded=${result.uploadedItems}, downloaded=${result.downloadedItems}")
                } else {
                    val errorMessage = result.errors.firstOrNull()?.message ?: "Unknown sync error"
                    Napier.w("Sync failed: $errorMessage")
                }
            } catch (e: Exception) {
                Napier.e("Sync failed with exception", e)
            }
        }
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setBackgroundSyncEnabled(enabled)
        }
    }
}
