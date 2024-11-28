package app.logdate.feature.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.repository.user.UserStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * A view model that provides app-wide state and actions.
 */
class AppViewModel(
    userStateRepository: UserStateRepository,
    private val biometricGatekeeper: BiometricGatekeeper,
    networkMonitor: NetworkAvailabilityMonitor,
) : ViewModel() {

    private val biometricState = biometricGatekeeper.authState

    private val networkState: Flow<NetworkState> = networkMonitor.observeNetwork()

    /**
     * The current UI state of the app.
     */
    val uiState: StateFlow<GlobalAppUiState> = combine(
        userStateRepository.userData,
        biometricState,
        networkState,
    ) { userState, authState, networkState ->
        GlobalAppUiState(
            isOnboarded = userState.isOnboarded,
            authState = authState,
            isOnline = networkState is NetworkState.Connected,
            isLoaded = true,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = GlobalAppUiState(),
        )

    /**
     * Show the biometric prompt to the user.
     *
     * This relies on native OS biometric unlock mechanisms.
     */
    fun showNativeUnlockPrompt() {
        biometricGatekeeper.authenticate()
    }
}