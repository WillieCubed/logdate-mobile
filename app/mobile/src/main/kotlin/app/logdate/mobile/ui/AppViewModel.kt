package app.logdate.mobile.ui

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.user.UserStateRepository
import app.logdate.core.datastore.model.AppSecurityLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * A view model that provides app-wide state and actions.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    userStateRepository: UserStateRepository,
    private val biometricGatekeeper: BiometricGatekeeper,
) : ViewModel() {

    private val biometricState = biometricGatekeeper.authState

    val uiState: StateFlow<LaunchAppUiState> =
        userStateRepository.userData.combine(biometricState) { userState, authState ->
            LaunchAppUiState.Loaded(
                isOnboarded = userState.isOnboarded,
                isBiometricEnabled = userState.securityLevel == AppSecurityLevel.BIOMETRIC,
                windowIsSecure = authState == AppAuthState.AUTHENTICATED,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LaunchAppUiState.Loading,
        )

    /**
     * Show the biometric prompt to the user.
     */
    fun showBiometricPrompt(activity: FragmentActivity) {
        biometricGatekeeper.authenticate(activity)
    }
}

