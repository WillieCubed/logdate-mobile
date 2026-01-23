package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.domain.account.CreatePasskeyUseCase
import app.logdate.client.domain.account.DeletePasskeyUseCase
import app.logdate.client.domain.account.GetCurrentAccountUseCase
import app.logdate.client.domain.profile.UpdateProfileUseCase
import app.logdate.client.domain.quota.ObserveCloudQuotaUseCase
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.clearAllLogDateTables
import app.logdate.client.networking.ServerHealthChecker
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.CloudStorageQuota
import app.logdate.feature.core.export.ExportLauncher
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.user.UserData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
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
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val passkeyAccountRepository: PasskeyAccountRepository,
    private val syncManager: SyncManager,
    private val sessionStorage: SessionStorage,
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val database: LogDateDatabase,
    private val serverHealthChecker: ServerHealthChecker,
    private val configRepository: LogDateConfigRepository,
) : ViewModel() {
    
    private val _passkeyRevocationState = MutableStateFlow<PasskeyRevocationState>(PasskeyRevocationState.Idle)
    val passkeyRevocationState: StateFlow<PasskeyRevocationState> = _passkeyRevocationState.asStateFlow()
    
    private val _passkeyCreationState = MutableStateFlow<PasskeyCreationState>(PasskeyCreationState.Idle)
    val passkeyCreationState: StateFlow<PasskeyCreationState> = _passkeyCreationState.asStateFlow()
    
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()
    
    private val _birthdayUpdateState = MutableStateFlow<BirthdayUpdateState>(BirthdayUpdateState.Idle)
    val birthdayUpdateState: StateFlow<BirthdayUpdateState> = _birthdayUpdateState.asStateFlow()

    private val _profileUpdateState = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)
    val profileUpdateState: StateFlow<ProfileUpdateState> = _profileUpdateState.asStateFlow()

    private val _syncStatusState = MutableStateFlow<SyncStatusState>(SyncStatusState.Idle)
    val syncStatusState: StateFlow<SyncStatusState> = _syncStatusState.asStateFlow()

    private val _serverSelectionState = MutableStateFlow(
        ServerSelectionState(
            localServerAddress = DefaultLogDateConfigRepository.DEFAULT_LOCAL_SERVER_ADDRESS
        )
    )
    val serverSelectionState: StateFlow<ServerSelectionState> = _serverSelectionState.asStateFlow()

    private val cloudQuotaFlow = observeCloudQuotaUseCase()
    private val sessionFlow = sessionStorage.getSessionFlow()
    private val backgroundSyncEnabledFlow = preferencesDataSource.backgroundSyncEnabled

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
     * Consolidated settings state assembled from core data sources.
     *
     * @property userData Local user profile and settings data.
     * @property quotaState Cloud storage usage details.
     * @property currentAccount Authenticated account metadata.
     * @property exportState Export workflow state.
     * @property syncStatus Latest sync status from the sync manager.
     */
    private data class SettingsCoreState(
        val userData: UserData?,
        val quotaState: CloudStorageQuota?,
        val currentAccount: LogDateAccount?,
        val exportState: ExportState,
        val syncStatus: app.logdate.client.sync.SyncStatus?
    )

    private val coreStateFlow = combine(
        userStateRepository.userData,
        cloudQuotaFlow,
        currentAccountFlow,
        _exportState,
        syncStatusFlow
    ) { userData, quotaState, currentAccount, exportState, syncStatus ->
        SettingsCoreState(
            userData = userData,
            quotaState = quotaState,
            currentAccount = currentAccount,
            exportState = exportState,
            syncStatus = syncStatus
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        coreStateFlow,
        sessionFlow,
        _passkeyCreationState,
        backgroundSyncEnabledFlow
    ) { coreState, session, passkeyCreationState, isBackgroundSyncEnabled ->
        val isAuthenticated = session != null
        SettingsUiState(
            userData = coreState.userData.orDefault(),
            quotaState = coreState.quotaState.orDefault(),
            currentAccount = coreState.currentAccount.orDefault(),
            passkeyCreationState = passkeyCreationState,
            exportState = coreState.exportState,
            syncStatus = coreState.syncStatus,
            isAuthenticated = isAuthenticated,
            isBackgroundSyncEnabled = isBackgroundSyncEnabled
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
            isAuthenticated = false,
            isBackgroundSyncEnabled = true
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
     * Updates profile details for the current account.
     */
    fun updateProfile(displayName: String, username: String) {
        val trimmedDisplayName = displayName.trim()
        val trimmedUsername = username.trim()
        val displayNameUpdate = trimmedDisplayName.takeIf { it.isNotEmpty() }
        val usernameUpdate = trimmedUsername.takeIf { it.isNotEmpty() }

        if (displayNameUpdate == null && usernameUpdate == null) {
            _profileUpdateState.value = ProfileUpdateState.Error("No profile changes to save")
            return
        }

        viewModelScope.launch {
            _profileUpdateState.value = ProfileUpdateState.Updating

            when (val result = updateProfileUseCase(displayName = displayNameUpdate, username = usernameUpdate)) {
                is UpdateProfileUseCase.Result.Success -> {
                    if (displayNameUpdate != null) {
                        preferencesDataSource.updateDisplayName(displayNameUpdate)
                    }
                    getCurrentAccountUseCase(GetCurrentAccountUseCase.AccountRequest.RefreshAccountInfo)
                    _profileUpdateState.value = ProfileUpdateState.Success
                }
                is UpdateProfileUseCase.Result.Error -> {
                    val error = result.error
                    val message = when (error) {
                        is UpdateProfileUseCase.ProfileUpdateError.InvalidDisplayName -> "Invalid display name"
                        is UpdateProfileUseCase.ProfileUpdateError.InvalidUsername -> "Invalid username"
                        is UpdateProfileUseCase.ProfileUpdateError.NetworkError -> "Network error updating profile"
                        is UpdateProfileUseCase.ProfileUpdateError.Unknown -> error.message
                    }
                    _profileUpdateState.value = ProfileUpdateState.Error(message)
                }
            }
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

    /**
     * Resets the birthday update state to Idle.
     * Should be called after successfully navigating back from the birthday settings screen.
     */
    fun resetBirthdayUpdateState() {
        _birthdayUpdateState.value = BirthdayUpdateState.Idle
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
     * Enable or disable background sync.
     */
    fun setBackgroundSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setBackgroundSyncEnabled(enabled)
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
     * Clears all local content and metadata stored in the database.
     */
    fun clearLocalData(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    database.clearAllLogDateTables()
                }
            }

            if (result.isFailure) {
                Napier.e("Failed to clear local data", result.exceptionOrNull())
            }

            onComplete?.invoke()
        }
    }

    /**
     * Clears local data, preferences, and session state for a full reset.
     */
    fun resetApp(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            clearLocalData()

            val prefsResult = preferencesDataSource.clearUserData()
            if (prefsResult.isFailure) {
                Napier.e("Failed to clear user preferences", prefsResult.exceptionOrNull())
            }

            passkeyAccountRepository.signOut()
            userStateRepository.setIsOnboardingComplete(false)

            onComplete?.invoke()
        }
    }

    /**
     * Signs out the current user.
     * Clears the session and disables background sync.
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                Napier.i("Signing out user")
                passkeyAccountRepository.signOut()
                preferencesDataSource.setBackgroundSyncEnabled(false)
                Napier.i("Session cleared successfully")
            } catch (e: Exception) {
                Napier.e("Failed to sign out", e)
            }
        }
    }

    /**
     * Selects a server preset.
     */
    fun selectServerPreset(preset: ServerPreset) {
        _serverSelectionState.update {
            it.copy(
                selectedPreset = preset,
                validationState = ServerValidationState.Idle
            )
        }
    }

    /**
     * Updates the local server address.
     */
    fun updateLocalServerAddress(address: String) {
        _serverSelectionState.update {
            it.copy(
                localServerAddress = address,
                validationState = ServerValidationState.Idle
            )
        }
    }

    /**
     * Updates the custom server URL.
     */
    fun updateCustomServerUrl(url: String) {
        _serverSelectionState.update {
            it.copy(
                customServerUrl = url,
                validationState = ServerValidationState.Idle
            )
        }
    }

    /**
     * Validates the server connection and saves the configuration if successful.
     */
    fun validateAndSaveServer() {
        val currentState = _serverSelectionState.value

        // For production, just save immediately
        if (currentState.selectedPreset == ServerPreset.PRODUCTION) {
            saveServerConfiguration(DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL)
            return
        }

        // For non-production, validate first
        val serverUrl = when (currentState.selectedPreset) {
            ServerPreset.LOCAL -> {
                val address = currentState.localServerAddress
                if (address.startsWith("http")) address else "http://$address"
            }
            ServerPreset.CUSTOM -> currentState.customServerUrl
            ServerPreset.PRODUCTION -> DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
        }

        if (serverUrl.isBlank()) {
            _serverSelectionState.update {
                it.copy(validationState = ServerValidationState.Error("Server URL cannot be empty"))
            }
            return
        }

        viewModelScope.launch {
            _serverSelectionState.update {
                it.copy(validationState = ServerValidationState.Validating)
            }

            val result = serverHealthChecker.checkServerHealth(serverUrl)

            result.fold(
                onSuccess = { healthInfo ->
                    Napier.i("Server health check succeeded: $healthInfo")
                    _serverSelectionState.update {
                        it.copy(validationState = ServerValidationState.Success(healthInfo.version))
                    }
                    saveServerConfiguration(serverUrl)
                },
                onFailure = { error ->
                    Napier.e("Server health check failed", error)
                    _serverSelectionState.update {
                        it.copy(
                            validationState = ServerValidationState.Error(
                                error.message ?: "Failed to connect to server"
                            )
                        )
                    }
                }
            )
        }
    }

    private fun saveServerConfiguration(serverUrl: String) {
        viewModelScope.launch {
            try {
                configRepository.updateBackendUrl(serverUrl)

                // Also save the local server address if using local preset
                val currentState = _serverSelectionState.value
                if (currentState.selectedPreset == ServerPreset.LOCAL) {
                    configRepository.updateLocalServerAddress(currentState.localServerAddress)
                }

                Napier.i("Server configuration saved: $serverUrl")
            } catch (e: Exception) {
                Napier.e("Failed to save server configuration", e)
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

/**
 * Represents the state of a profile update operation.
 */
sealed class ProfileUpdateState {
    /**
     * No profile update is in progress.
     */
    data object Idle : ProfileUpdateState()

    /**
     * A profile update operation is in progress.
     */
    data object Updating : ProfileUpdateState()

    /**
     * A profile update operation completed successfully.
     */
    data object Success : ProfileUpdateState()

    /**
     * A profile update operation failed.
     */
    data class Error(val message: String) : ProfileUpdateState()
}
