package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.domain.account.CreatePasskeyUseCase
import app.logdate.client.domain.account.DeletePasskeyUseCase
import app.logdate.client.domain.account.GetCurrentAccountUseCase
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

data class PrivacySettingsState(
    val isBiometricsEnabled: Boolean,
    val isAuthenticated: Boolean,
    val passkeys: List<PasskeyInfo>,
    val isSystemSearchVisibilityEnabled: Boolean,
    val showSystemSearchVisibilityToggle: Boolean,
)

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
    private val biometricGatekeeper: BiometricGatekeeper,
    private val supportsSystemSearchVisibilityToggle: Boolean = false,
) : ViewModel() {
    private val _passkeyCreationState = MutableStateFlow<PasskeyCreationState>(PasskeyCreationState.Idle)
    val passkeyCreationState: StateFlow<PasskeyCreationState> = _passkeyCreationState

    private val _passkeyRevocationState = MutableStateFlow<PasskeyRevocationState>(PasskeyRevocationState.Idle)
    val passkeyRevocationState: StateFlow<PasskeyRevocationState> = _passkeyRevocationState

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

    val state: StateFlow<PrivacySettingsState> =
        combine(
            preferencesDataSource.observeSystemSearchVisibilityEnabled(),
            userStateRepository.userData,
            sessionStorage.getSessionFlow(),
            currentAccountFlow,
        ) { isSystemSearchVisibilityEnabled, userData, session, account ->
            PrivacySettingsState(
                isBiometricsEnabled = userData.securityLevel == AppSecurityLevel.BIOMETRIC,
                isAuthenticated = session != null,
                passkeys = account.orDefault().toPasskeyInfoList(),
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

    fun createPasskey() {
        viewModelScope.launch {
            _passkeyCreationState.value = PasskeyCreationState.Creating
            val result = createPasskeyUseCase(CreatePasskeyUseCase.CreatePasskeyRequest())
            _passkeyCreationState.value =
                when (result) {
                    is CreatePasskeyUseCase.CreatePasskeyResult.Success -> {
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
                    is DeletePasskeyUseCase.DeletePasskeyResult.Success -> PasskeyRevocationState.Success
                    is DeletePasskeyUseCase.DeletePasskeyResult.Error -> PasskeyRevocationState.Error(result.message)
                }
        }
    }
}
