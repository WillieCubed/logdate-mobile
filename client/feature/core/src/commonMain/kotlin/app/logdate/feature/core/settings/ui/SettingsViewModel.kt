package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.domain.account.CreatePasskeyUseCase
import app.logdate.client.domain.account.DeletePasskeyUseCase
import app.logdate.client.domain.account.GetCurrentAccountUseCase
import app.logdate.client.domain.quota.ObserveCloudQuotaUseCase
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.sync.SyncManager
import app.logdate.shared.model.CloudStorageQuota
import app.logdate.feature.core.export.ExportLauncher
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.user.UserData
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
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/**
 * A view model for accessing and updating the user's settings.
 */
class SettingsViewModel(
    private val userStateRepository: UserStateRepository,
    private val observeCloudQuotaUseCase: ObserveCloudQuotaUseCase,
    private val exportLauncher: ExportLauncher,
    private val getCurrentAccountUseCase: GetCurrentAccountUseCase,
    private val deletePasskeyUseCase: DeletePasskeyUseCase,
    private val createPasskeyUseCase: CreatePasskeyUseCase,
    private val syncManager: SyncManager,
    private val sessionStorage: SessionStorage,
) : ViewModel() {
    
    // State for tracking passkey revocation operations
    private val _passkeyRevocationState = MutableStateFlow<PasskeyRevocationState>(PasskeyRevocationState.Idle)
    val passkeyRevocationState: StateFlow<PasskeyRevocationState> = _passkeyRevocationState.asStateFlow()
    
    // State for tracking passkey creation operations
    private val _passkeyCreationState = MutableStateFlow<PasskeyCreationState>(PasskeyCreationState.Idle)
    val passkeyCreationState: StateFlow<PasskeyCreationState> = _passkeyCreationState.asStateFlow()
    
    // State for tracking export operations and destination
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()
    
    // State for tracking birthday update operations
    private val _birthdayUpdateState = MutableStateFlow<BirthdayUpdateState>(BirthdayUpdateState.Idle)
    val birthdayUpdateState: StateFlow<BirthdayUpdateState> = _birthdayUpdateState.asStateFlow()

    // State for tracking sync operations
    private val _syncStatusState = MutableStateFlow<SyncStatusState>(SyncStatusState.Idle)
    val syncStatusState: StateFlow<SyncStatusState> = _syncStatusState.asStateFlow()

    private val cloudQuotaFlow = observeCloudQuotaUseCase()

    // Poll sync status periodically
    private val syncStatusFlow = flow {
        while (true) {
            val status = syncManager.getSyncStatus()
            emit(status)
            delay(5000) // Poll every 5 seconds
        }
    }
    
    private val currentAccountFlow = flow {
        val result = getCurrentAccountUseCase(GetCurrentAccountUseCase.AccountRequest.GetCurrentAccount)
        when (result) {
            is GetCurrentAccountUseCase.AccountResult.CurrentAccount -> {
                result.account.collect { emit(it) }
            }
            else -> emit(null)
        }
    }

    /**
     * Consolidated UI state combining all settings-related data.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        userStateRepository.userData,
        cloudQuotaFlow,
        currentAccountFlow,
        _exportState,
        syncStatusFlow
    ) { userData, quotaState, currentAccount, exportState, syncStatus ->
        val isAuthenticated = sessionStorage.getSession() != null
        SettingsUiState(
            userData = userData.orDefault(),
            quotaState = quotaState.orDefault(),
            currentAccount = currentAccount.orDefault(),
            passkeyCreationState = PasskeyCreationState.Idle,
            exportState = exportState,
            syncStatus = syncStatus,
            isAuthenticated = isAuthenticated
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState(
            userData = (null as UserData?).orDefault(),
            quotaState = (null as CloudStorageQuota?).orDefault(),
            currentAccount = (null as LogDateAccount?).orDefault(),
            passkeyCreationState = PasskeyCreationState.Idle,
            exportState = ExportState.Idle,
            syncStatus = null,
            isAuthenticated = false
        )
    )

    /**
     * Triggers an app-wide reset.
     */
    fun reset() {
        viewModelScope.launch {
            userStateRepository.setIsOnboardingComplete(false)
        }
    }

    /**
     * Sets the security level for the app.
     */
    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userStateRepository.setBiometricEnabled(enabled)
        }
    }
    
    /**
     * Updates the user's birthday.
     * 
     * @param birthday The new birthday date
     */
    fun updateBirthday(birthday: Instant) {
        Napier.d("ViewModel: updateBirthday called with $birthday")
        viewModelScope.launch {
            try {
                _birthdayUpdateState.value = BirthdayUpdateState.Updating
                Napier.d("ViewModel: calling repository with $birthday")
                userStateRepository.setBirthday(birthday)
                Napier.d("ViewModel: repository call completed")
                _birthdayUpdateState.value = BirthdayUpdateState.Success
            } catch (e: Exception) {
                Napier.e("ViewModel: failed to update birthday", e)
                _birthdayUpdateState.value = BirthdayUpdateState.Error(
                    e.message ?: "Failed to update birthday"
                )
            }
        }
    }

    init {
        // Set up export completion callback to update state when export completes
        exportLauncher.setExportCompletionCallback { path ->
            if (path == null) {
                _exportState.update { ExportState.Idle }
            } else {
                _exportState.update { ExportState.Selected(path, true) }
            }
        }
    }

    /**
     * Starts the export process using the platform-specific export launcher.
     * This will trigger a file selection dialog on the platform.
     */
    fun exportContent() {
        _exportState.update { ExportState.Selecting }
        exportLauncher.startExport()
    }
    
    /**
     * Cancels any ongoing export operation.
     */
    fun cancelExport() {
        exportLauncher.cancelExport()
        _exportState.update { ExportState.Idle }
    }
    
    /**
     * Marks the export completion snackbar as shown.
     * This prevents showing the snackbar again on recomposition.
     */
    fun markExportSnackbarShown() {
        _exportState.update { 
            if (it is ExportState.Selected) {
                it.copy(showSnackbar = false)
            } else {
                it
            }
        }
    }
    
    /**
     * Creates a new passkey for the current user's account.
     * This will show the system passkey creation dialog.
     */
    fun createPasskey() {
        viewModelScope.launch {
            _passkeyCreationState.value = PasskeyCreationState.Creating
            
            val result = createPasskeyUseCase(CreatePasskeyUseCase.CreatePasskeyRequest())
            
            _passkeyCreationState.value = when (result) {
                is CreatePasskeyUseCase.CreatePasskeyResult.Success -> {
                    PasskeyCreationState.Success(result.account)
                }
                is CreatePasskeyUseCase.CreatePasskeyResult.Error -> {
                    PasskeyCreationState.Error(result.message)
                }
            }
        }
    }
    
    /**
     * Revokes (deletes) a passkey by its credential ID.
     *
     * @param credentialId The ID of the passkey credential to revoke
     */
    fun revokePasskey(credentialId: String) {
        viewModelScope.launch {
            _passkeyRevocationState.value = PasskeyRevocationState.Revoking

            val result = deletePasskeyUseCase(DeletePasskeyUseCase.DeletePasskeyRequest(credentialId))

            _passkeyRevocationState.value = when (result) {
                is DeletePasskeyUseCase.DeletePasskeyResult.Success -> {
                    PasskeyRevocationState.Success
                }
                is DeletePasskeyUseCase.DeletePasskeyResult.Error -> {
                    PasskeyRevocationState.Error(result.message)
                }
            }
        }
    }

    /**
     * Triggers a manual sync operation.
     * Uploads pending changes and downloads remote changes.
     */
    fun syncNow() {
        viewModelScope.launch {
            try {
                _syncStatusState.value = SyncStatusState.Syncing
                Napier.d("Starting manual sync...")

                val result = syncManager.fullSync()

                if (result.success) {
                    Napier.i("Sync completed successfully: uploaded=${result.uploadedItems}, downloaded=${result.downloadedItems}")
                    _syncStatusState.value = SyncStatusState.Success(
                        uploadedItems = result.uploadedItems,
                        downloadedItems = result.downloadedItems
                    )
                } else {
                    val errorMessage = result.errors.firstOrNull()?.message ?: "Unknown sync error"
                    Napier.w("Sync failed: $errorMessage")
                    _syncStatusState.value = SyncStatusState.Error(errorMessage)
                }
            } catch (e: Exception) {
                Napier.e("Sync failed with exception", e)
                _syncStatusState.value = SyncStatusState.Error(e.message ?: "Sync failed")
            }
        }
    }

    /**
     * Clears the sync status message.
     */
    fun clearSyncStatus() {
        _syncStatusState.value = SyncStatusState.Idle
    }

    /**
     * Signs out the current user.
     * Clears the session and disables background sync.
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                Napier.i("Signing out user")
                sessionStorage.clearSession()
                Napier.i("Session cleared successfully")
            } catch (e: Exception) {
                Napier.e("Failed to sign out", e)
            }
        }
    }
}

/**
 * Represents the state of an export operation.
 */
sealed class ExportState {
    /**
     * No export operation is in progress.
     */
    data object Idle : ExportState()
    
    /**
     * User is currently selecting an export destination.
     */
    data object Selecting : ExportState()
    
    /**
     * Export destination has been selected.
     * 
     * @param path The path to the exported file
     * @param showSnackbar Whether to show a snackbar notification
     */
    data class Selected(
        val path: String, 
        val showSnackbar: Boolean = true
    ) : ExportState()
}

/**
 * Represents the state of a passkey revocation operation.
 */
sealed class PasskeyRevocationState {
    /**
     * No revocation operation is in progress.
     */
    data object Idle : PasskeyRevocationState()
    
    /**
     * A revocation operation is in progress.
     */
    data object Revoking : PasskeyRevocationState()
    
    /**
     * A revocation operation completed successfully.
     */
    data object Success : PasskeyRevocationState()
    
    /**
     * A revocation operation failed.
     */
    data class Error(val message: String) : PasskeyRevocationState()
}

/**
 * Represents the state of a birthday update operation.
 */
sealed class BirthdayUpdateState {
    /**
     * No birthday update operation is in progress.
     */
    data object Idle : BirthdayUpdateState()

    /**
     * A birthday update operation is in progress.
     */
    data object Updating : BirthdayUpdateState()

    /**
     * A birthday update operation completed successfully.
     */
    data object Success : BirthdayUpdateState()

    /**
     * A birthday update operation failed.
     */
    data class Error(val message: String) : BirthdayUpdateState()
}

/**
 * Represents the state of a sync operation.
 */
sealed class SyncStatusState {
    /**
     * No sync operation is in progress.
     */
    data object Idle : SyncStatusState()

    /**
     * A sync operation is in progress.
     */
    data object Syncing : SyncStatusState()

    /**
     * A sync operation completed successfully.
     */
    data class Success(
        val uploadedItems: Int,
        val downloadedItems: Int
    ) : SyncStatusState()

    /**
     * A sync operation failed.
     */
    data class Error(val message: String) : SyncStatusState()
}
