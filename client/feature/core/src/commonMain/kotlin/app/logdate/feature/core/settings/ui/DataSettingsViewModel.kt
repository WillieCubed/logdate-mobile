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
import app.logdate.feature.core.export.ExportLauncher
import app.logdate.feature.core.restore.RestoreLauncher
import app.logdate.feature.core.restore.RestoreOutcome
import app.logdate.feature.core.restore.RestoreSummary
import app.logdate.shared.model.CloudStorageQuota
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
    val exportState: ExportState,
    val restoreState: RestoreState,
    val integrityState: IntegrityState,
    val conflictsState: ConflictsState,
    val syncStatus: app.logdate.client.sync.SyncStatus?,
    val isAuthenticated: Boolean,
    val isBackgroundSyncEnabled: Boolean,
)

private data class DataSettingsSourceState(
    val quotaState: CloudStorageQuota?,
    val exportState: ExportState,
    val restoreState: RestoreState,
    val integrityState: IntegrityState,
    val conflictsState: ConflictsState,
)

sealed class ExportState {
    data object Idle : ExportState()

    data object Selecting : ExportState()

    data class Selected(
        val path: String,
        val showSnackbar: Boolean = true,
    ) : ExportState()
}

sealed class RestoreState {
    data object Idle : RestoreState()

    data object Selecting : RestoreState()

    data object Restoring : RestoreState()

    data class Completed(
        val summary: RestoreSummary,
        val showSnackbar: Boolean = true,
    ) : RestoreState()

    data class Failed(
        val message: String,
        val showSnackbar: Boolean = true,
    ) : RestoreState()
}

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
    private val exportLauncher: ExportLauncher,
    private val restoreLauncher: RestoreLauncher,
    private val syncManager: SyncManager,
    private val sessionStorage: SessionStorage,
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val dataIntegrityService: DataIntegrityService,
    private val conflictStore: SyncConflictStore,
) : ViewModel() {
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    private val _integrityState = MutableStateFlow(IntegrityState())
    val integrityState: StateFlow<IntegrityState> = _integrityState.asStateFlow()

    private val _conflictsState = MutableStateFlow(ConflictsState())
    val conflictsState: StateFlow<ConflictsState> = _conflictsState.asStateFlow()

    private val quotaFlow = observeCloudQuotaUseCase()
    private val sessionFlow = sessionStorage.getSessionFlow()
    private val backgroundSyncEnabledFlow = preferencesDataSource.backgroundSyncEnabled

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
            _exportState,
            _restoreState,
            _integrityState,
        ) { quotaState, exportState, restoreState, integrityState ->
            DataSettingsSourceState(
                quotaState = quotaState,
                exportState = exportState,
                restoreState = restoreState,
                integrityState = integrityState,
                conflictsState = ConflictsState(),
            )
        }.combine(_conflictsState) { sourceState, conflictsState ->
            sourceState.copy(conflictsState = conflictsState)
        }

    val uiState: StateFlow<DataSettingsState> =
        combine(
            sourceStateFlow,
            syncStatusFlow,
            sessionFlow,
            backgroundSyncEnabledFlow,
        ) { sourceState, syncStatus, session, backgroundSyncEnabled ->
            DataSettingsState(
                quotaState = sourceState.quotaState.orDefault(),
                exportState = sourceState.exportState,
                restoreState = sourceState.restoreState,
                integrityState = sourceState.integrityState,
                conflictsState = sourceState.conflictsState,
                syncStatus = syncStatus,
                isAuthenticated = session != null,
                isBackgroundSyncEnabled = backgroundSyncEnabled,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DataSettingsState(
                quotaState = (null as CloudStorageQuota?).orDefault(),
                exportState = ExportState.Idle,
                restoreState = RestoreState.Idle,
                integrityState = IntegrityState(),
                conflictsState = ConflictsState(),
                syncStatus = null,
                isAuthenticated = false,
                isBackgroundSyncEnabled = true,
            ),
        )

    init {
        exportLauncher.setExportCompletionCallback { path ->
            if (path == null) {
                _exportState.update { ExportState.Idle }
            } else {
                _exportState.update { ExportState.Selected(path, true) }
            }
        }

        restoreLauncher.setRestoreCompletionCallback { outcome ->
            when (outcome) {
                is RestoreOutcome.Started -> _restoreState.update { RestoreState.Restoring }
                is RestoreOutcome.Cancelled -> _restoreState.update { RestoreState.Idle }
                is RestoreOutcome.Success -> {
                    _restoreState.update { RestoreState.Completed(outcome.summary, true) }
                    runIntegrityCheck()
                }
                is RestoreOutcome.Failure ->
                    _restoreState.update {
                        RestoreState.Failed(outcome.message, true)
                    }
            }
        }

        startConflictPolling()
    }

    fun exportContent() {
        _exportState.update { ExportState.Selecting }
        exportLauncher.startExport()
    }

    fun cancelExport() {
        exportLauncher.cancelExport()
        _exportState.update { ExportState.Idle }
    }

    fun markExportSnackbarShown() {
        _exportState.update {
            if (it is ExportState.Selected) {
                it.copy(showSnackbar = false)
            } else {
                it
            }
        }
    }

    fun restoreContent() {
        _restoreState.update { RestoreState.Selecting }
        restoreLauncher.startRestore()
    }

    fun cancelRestore() {
        restoreLauncher.cancelRestore()
        _restoreState.update { RestoreState.Idle }
    }

    fun markRestoreSnackbarShown() {
        _restoreState.update {
            when (it) {
                is RestoreState.Completed -> it.copy(showSnackbar = false)
                is RestoreState.Failed -> it.copy(showSnackbar = false)
                else -> it
            }
        }
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
