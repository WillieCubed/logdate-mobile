package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.shared.model.user.AppSecurityLevel
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

class PrivacySettingsViewModel(
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val userStateRepository: UserStateRepository,
    private val biometricGatekeeper: BiometricGatekeeper,
    private val supportsSystemSearchVisibilityToggle: Boolean = false,
) : ViewModel() {
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
}
