package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.domain.account.CreatePasskeyUseCase
import app.logdate.client.domain.account.DeletePasskeyUseCase
import app.logdate.client.domain.account.GetCurrentAccountUseCase
import app.logdate.client.domain.account.GetPasskeysUseCase
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.user.AppSecurityLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.logdate.shared.model.PasskeyInfo as SharedPasskeyInfo

data class PrivacySettingsState(
    val isBiometricsEnabled: Boolean,
    val isAuthenticated: Boolean,
    val passkeys: List<PasskeyInfo>,
    val isSystemSearchVisibilityEnabled: Boolean,
    val showSystemSearchVisibilityToggle: Boolean,
)

sealed class RecoveryPhraseRevealState {
    data object Hidden : RecoveryPhraseRevealState()

    data object Loading : RecoveryPhraseRevealState()

    data class Revealed(
        val words: List<String>,
    ) : RecoveryPhraseRevealState()

    data object Missing : RecoveryPhraseRevealState()

    data class Error(
        val message: String,
    ) : RecoveryPhraseRevealState()
}

sealed class PasskeyRevocationState {
    data object Idle : PasskeyRevocationState()

    data object Revoking : PasskeyRevocationState()

    data object Success : PasskeyRevocationState()

    data class Error(
        val message: String,
    ) : PasskeyRevocationState()
}

class PrivacySettingsViewModel(
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val userStateRepository: UserStateRepository,
    private val sessionStorage: SessionStorage,
    private val getCurrentAccountUseCase: GetCurrentAccountUseCase,
    private val createPasskeyUseCase: CreatePasskeyUseCase,
    private val deletePasskeyUseCase: DeletePasskeyUseCase,
    private val getPasskeysUseCase: GetPasskeysUseCase,
    private val biometricGatekeeper: BiometricGatekeeper,
    private val identityKeyManager: IdentityKeyManager,
    private val supportsSystemSearchVisibilityToggle: Boolean = false,
) : ViewModel() {
    private val _passkeyCreationState = MutableStateFlow<PasskeyCreationState>(PasskeyCreationState.Idle)
    val passkeyCreationState: StateFlow<PasskeyCreationState> = _passkeyCreationState

    private val _passkeyRevocationState = MutableStateFlow<PasskeyRevocationState>(PasskeyRevocationState.Idle)
    val passkeyRevocationState: StateFlow<PasskeyRevocationState> = _passkeyRevocationState

    private val _recoveryPhraseRevealState = MutableStateFlow<RecoveryPhraseRevealState>(RecoveryPhraseRevealState.Hidden)
    val recoveryPhraseRevealState: StateFlow<RecoveryPhraseRevealState> = _recoveryPhraseRevealState

    private val currentAccountFlow: Flow<LogDateAccount?> =
        flow {
            val result = getCurrentAccountUseCase(GetCurrentAccountUseCase.AccountRequest.GetCurrentAccount)
            when (result) {
                is GetCurrentAccountUseCase.AccountResult.CurrentAccount -> {
                    result.account.collect { emit(it) }
                }
                else -> emit(null)
            }
        }

    /**
     * Details for each passkey, fetched separately because the account payload carries only IDs.
     *
     * Starts empty and fills in, so the list appears immediately from what the account already
     * knows rather than waiting on the network to show anything at all.
     */
    private val passkeyDetails = MutableStateFlow<List<SharedPasskeyInfo>>(emptyList())

    init {
        // Load whenever a session appears, and clear on sign-out so one account's device names
        // cannot be left on screen for the next person to sign in.
        viewModelScope.launch {
            sessionStorage.getSessionFlow().collect { session ->
                passkeyDetails.value = if (session == null) emptyList() else getPasskeysUseCase()
            }
        }
    }

    val state: StateFlow<PrivacySettingsState> =
        combine(
            preferencesDataSource.observeSystemSearchVisibilityEnabled(),
            userStateRepository.userData,
            sessionStorage.getSessionFlow(),
            currentAccountFlow,
            passkeyDetails,
        ) { isSystemSearchVisibilityEnabled, userData, session, account, details ->
            PrivacySettingsState(
                isBiometricsEnabled = userData.securityLevel == AppSecurityLevel.BIOMETRIC,
                isAuthenticated = session != null,
                passkeys = account.orDefault().toPasskeyInfoList(details),
                isSystemSearchVisibilityEnabled = isSystemSearchVisibilityEnabled,
                showSystemSearchVisibilityToggle = supportsSystemSearchVisibilityToggle,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PrivacySettingsState(
                isBiometricsEnabled = false,
                isAuthenticated = false,
                passkeys = emptyList(),
                isSystemSearchVisibilityEnabled = false,
                showSystemSearchVisibilityToggle = supportsSystemSearchVisibilityToggle,
            ),
        )

    /**
     * Reloads the passkey details.
     *
     * Failure is deliberately quiet: the list still renders from the credential IDs the account
     * carries, and an error banner over supplementary detail would be noise on a settings screen.
     */
    fun refreshPasskeyDetails() {
        viewModelScope.launch {
            passkeyDetails.value = getPasskeysUseCase()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch {
                userStateRepository.setBiometricEnabled(false)
            }
            return
        }
        biometricGatekeeper.authenticate(
            title = "Enable biometric lock",
            subtitle = "Authenticate to turn on biometric lock",
            description = "LogDate will require biometrics or your device passcode to unlock.",
            onResult = { result ->
                if (result == AppAuthState.AUTHENTICATED) {
                    viewModelScope.launch {
                        userStateRepository.setBiometricEnabled(true)
                    }
                }
            },
        )
    }

    fun setSystemSearchVisibilityEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setSystemSearchVisibilityEnabled(enabled)
        }
    }

    fun revealRecoveryPhrase() {
        _recoveryPhraseRevealState.value = RecoveryPhraseRevealState.Loading
        biometricGatekeeper.authenticate(
            title = "Show recovery phrase",
            subtitle = "Authenticate to view your recovery phrase",
            description = "Anyone with this phrase can recover your encrypted LogDate data.",
            onResult = { result ->
                if (result == AppAuthState.AUTHENTICATED || result == AppAuthState.NO_PROMPT_NEEDED) {
                    viewModelScope.launch {
                        _recoveryPhraseRevealState.value =
                            runCatching { identityKeyManager.getStoredRecoveryPhrase() }
                                .fold(
                                    onSuccess = { phrase ->
                                        if (phrase == null) {
                                            RecoveryPhraseRevealState.Missing
                                        } else {
                                            RecoveryPhraseRevealState.Revealed(phrase.words)
                                        }
                                    },
                                    onFailure = { error ->
                                        RecoveryPhraseRevealState.Error(
                                            error.message ?: "Could not load recovery phrase",
                                        )
                                    },
                                )
                    }
                } else {
                    _recoveryPhraseRevealState.value =
                        RecoveryPhraseRevealState.Error("Authentication is required to show your recovery phrase")
                }
            },
        )
    }

    fun hideRecoveryPhrase() {
        _recoveryPhraseRevealState.value = RecoveryPhraseRevealState.Hidden
    }

    fun createPasskey() {
        viewModelScope.launch {
            _passkeyCreationState.value = PasskeyCreationState.Creating
            val result = createPasskeyUseCase(CreatePasskeyUseCase.CreatePasskeyRequest())
            _passkeyCreationState.value =
                when (result) {
                    is CreatePasskeyUseCase.CreatePasskeyResult.Success -> {
                        // The new passkey's nickname and device come from the server, so the list
                        // stays a credential short until it is read back.
                        refreshPasskeyDetails()
                        PasskeyCreationState.Success(result.account)
                    }
                    is CreatePasskeyUseCase.CreatePasskeyResult.Error -> {
                        PasskeyCreationState.Error(result.message)
                    }
                }
        }
    }

    fun revokePasskey(credentialId: String) {
        viewModelScope.launch {
            _passkeyRevocationState.value = PasskeyRevocationState.Revoking
            val result = deletePasskeyUseCase(DeletePasskeyUseCase.DeletePasskeyRequest(credentialId))
            _passkeyRevocationState.value =
                when (result) {
                    is DeletePasskeyUseCase.DeletePasskeyResult.Success -> {
                        // Drop the revoked credential's details rather than leaving a row
                        // describing something the account no longer has.
                        refreshPasskeyDetails()
                        PasskeyRevocationState.Success
                    }
                    is DeletePasskeyUseCase.DeletePasskeyResult.Error -> PasskeyRevocationState.Error(result.message)
                }
        }
    }
}
