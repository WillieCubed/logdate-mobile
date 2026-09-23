package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.shared.model.user.AppSecurityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PrivacySettingsState(
    val isBiometricsEnabled: Boolean,
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

class PrivacySettingsViewModel(
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val userStateRepository: UserStateRepository,
    private val biometricGatekeeper: BiometricGatekeeper,
    private val identityKeyManager: IdentityKeyManager,
    private val supportsSystemSearchVisibilityToggle: Boolean = false,
) : ViewModel() {
    private val _recoveryPhraseRevealState = MutableStateFlow<RecoveryPhraseRevealState>(RecoveryPhraseRevealState.Hidden)
    val recoveryPhraseRevealState: StateFlow<RecoveryPhraseRevealState> = _recoveryPhraseRevealState

    val state: StateFlow<PrivacySettingsState> =
        combine(
            preferencesDataSource.observeSystemSearchVisibilityEnabled(),
            userStateRepository.userData,
        ) { isSystemSearchVisibilityEnabled, userData ->
            PrivacySettingsState(
                isBiometricsEnabled = userData.securityLevel == AppSecurityLevel.BIOMETRIC,
                isSystemSearchVisibilityEnabled = isSystemSearchVisibilityEnabled,
                showSystemSearchVisibilityToggle = supportsSystemSearchVisibilityToggle,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PrivacySettingsState(
                isBiometricsEnabled = false,
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
}
