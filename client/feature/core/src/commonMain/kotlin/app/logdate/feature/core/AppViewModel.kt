package app.logdate.feature.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.domain.account.TryRestoreSignInUseCase
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.user.AppSecurityLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A view model that provides app-wide state and actions.
 */
class AppViewModel(
    private val userStateRepository: UserStateRepository,
    private val biometricGatekeeper: BiometricGatekeeper,
    networkMonitor: NetworkAvailabilityMonitor,
    private val sessionStorage: SessionStorage,
    private val tryRestoreSignInUseCase: TryRestoreSignInUseCase,
) : ViewModel() {
    private val biometricState = biometricGatekeeper.authState
    private val appLockState = MutableStateFlow(AppLockState.Unlocked)
    private val securityLevelState = MutableStateFlow(AppSecurityLevel.NONE)
    private var hasAttemptedCloudRestoreSignIn = false

    private val networkState: Flow<NetworkState> = networkMonitor.observeNetwork()

    /**
     * The current UI state of the app.
     */
    val uiState: StateFlow<GlobalAppUiState> =
        combine(
            userStateRepository.userData,
            biometricState,
            networkState,
            appLockState,
        ) { userState, authState, networkState, lockState ->
            GlobalAppUiLoadedState(
                isOnboarded = userState.isOnboarded,
                authState =
                    resolveAuthState(
                        securityLevel = userState.securityLevel,
                        biometricState = authState,
                        appLockState = lockState,
                    ),
                isOnline = networkState is NetworkState.Connected,
                displayName = userState.displayName,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = GlobalAppUiLoadingState,
        )

    init {
        viewModelScope.launch {
            var isInitialEmission = true
            userStateRepository.userData.collect { userData ->
                val newLevel = userData.securityLevel
                securityLevelState.value = newLevel

                when {
                    newLevel != AppSecurityLevel.BIOMETRIC ->
                        appLockState.value = AppLockState.Unlocked
                    isInitialEmission ->
                        appLockState.value = AppLockState.Locked
                    // Mid-session toggle to BIOMETRIC: leave the lock state alone.
                    // The user just authenticated to enable the setting; the lock will
                    // re-assert on next backgrounding or cold start.
                }
                isInitialEmission = false
            }
        }

        viewModelScope.launch {
            biometricGatekeeper.authState.collect { authState ->
                if (authState == AppAuthState.AUTHENTICATED) {
                    appLockState.value = AppLockState.Unlocked
                }
            }
        }
    }

    /**
     * Silently attempts to sign into the cloud account using a restore credential from the
     * device's backup after Android startup has detected a cloud restore.
     */
    fun tryRestoreSignInAfterCloudRestore() {
        if (hasAttemptedCloudRestoreSignIn) return
        hasAttemptedCloudRestoreSignIn = true

        viewModelScope.launch {
            tryRestoreSignInAfterCloudRestoreInternal()
        }
    }

    private suspend fun tryRestoreSignInAfterCloudRestoreInternal() {
        val userData = userStateRepository.userData.first()
        if (!userData.isOnboarded) return
        if (sessionStorage.hasValidSession()) return

        Napier.i("Cloud restore detected with no valid session — attempting restore sign-in")
        when (val result = tryRestoreSignInUseCase()) {
            is TryRestoreSignInUseCase.Result.Success ->
                Napier.i("Restore sign-in succeeded for ${result.account.username}")
            is TryRestoreSignInUseCase.Result.NoCredential ->
                Napier.d("No restore credential on cloud-restored device — using manual sign-in")
            is TryRestoreSignInUseCase.Result.Error ->
                Napier.i("Restore sign-in fell back to manual sign-in: ${result.message}")
        }
    }

    /**
     * Show the biometric prompt to the user.
     *
     * This relies on native OS biometric unlock mechanisms.
     */
    fun showNativeUnlockPrompt() {
        biometricGatekeeper.authenticate()
    }

    /**
     * Marks the app as locked when it moves to the background.
     */
    fun onAppBackgrounded() {
        if (securityLevelState.value == AppSecurityLevel.BIOMETRIC) {
            appLockState.value = AppLockState.Locked
        }
    }
}

private fun resolveAuthState(
    securityLevel: AppSecurityLevel,
    biometricState: AppAuthState,
    appLockState: AppLockState,
): AppAuthState {
    if (securityLevel != AppSecurityLevel.BIOMETRIC) {
        return AppAuthState.NO_PROMPT_NEEDED
    }

    return when (biometricState) {
        AppAuthState.REQUEST_ENROLLMENT,
        AppAuthState.UNSUPPORTED,
        AppAuthState.UPDATE_REQUIRED,
        AppAuthState.UNKNOWN,
        -> biometricState
        AppAuthState.AUTHENTICATED -> AppAuthState.AUTHENTICATED
        AppAuthState.NO_PROMPT_NEEDED,
        AppAuthState.REQUIRE_PROMPT,
        ->
            if (appLockState == AppLockState.Locked) {
                AppAuthState.REQUIRE_PROMPT
            } else {
                AppAuthState.NO_PROMPT_NEEDED
            }
    }
}

private enum class AppLockState {
    Locked,
    Unlocked,
}
