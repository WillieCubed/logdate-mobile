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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/**
 * A view model that provides app-wide state and actions.
 */
class AppViewModel(
    private val userStateRepository: UserStateRepository,
    private val biometricGatekeeper: BiometricGatekeeper,
    networkMonitor: NetworkAvailabilityMonitor,
    private val sessionStorage: SessionStorage,
    private val tryRestoreSignInUseCase: TryRestoreSignInUseCase,
    private val onCloudRestoreSignIn: () -> Unit = {},
) : ViewModel() {
    private val biometricState = biometricGatekeeper.authState
    private val appLockState = MutableStateFlow(AppLockState.Locked)
    private var appIsForegrounded = true
    private var foregroundGeneration = 0L
    private var unlockRequest = 0L
    private val securityLevelState = MutableStateFlow(AppSecurityLevel.NONE)
    private var hasAttemptedCloudRestoreSignIn = false
    private var pendingNativeRecovery: NativeAccountRecoveryRequest? = null
    private var nativeRecoveryJob: Job? = null
    private var lastNativeFailure: kotlin.time.TimeMark? = null

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
            networkState.collect { if (it is NetworkState.Connected) retryPendingNativeRecovery() }
        }
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
    }

    /**
     * Recovers account access independently of native content delivery. Transferred
     * journals do not need an archive import merely because their session was not portable.
     */
    fun tryRestoreSignInAfterCloudRestore(
        recoverMissingContent: Boolean = true,
        onAccountResolved: () -> Unit = {},
    ) {
        if (hasAttemptedCloudRestoreSignIn) return
        pendingNativeRecovery = NativeAccountRecoveryRequest(recoverMissingContent, onAccountResolved)
        retryPendingNativeRecovery()
    }

    private fun retryPendingNativeRecovery() {
        val request = pendingNativeRecovery ?: return
        if (nativeRecoveryJob?.isActive == true) return
        hasAttemptedCloudRestoreSignIn = true
        nativeRecoveryJob =
            viewModelScope.launch {
                val elapsed = lastNativeFailure?.elapsedNow()?.inWholeMilliseconds ?: RETRY_DELAY_MS
                delay((RETRY_DELAY_MS - elapsed).coerceAtLeast(0))
                try {
                    val resolved =
                        withTimeoutOrNull(ATTEMPT_TIMEOUT_MS) {
                            tryRestoreSignInAfterCloudRestoreInternal(request.recoverMissingContent, request.onAccountResolved)
                        } == true
                    if (resolved && pendingNativeRecovery === request) pendingNativeRecovery = null
                    if (!resolved) lastNativeFailure = TimeSource.Monotonic.markNow()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    lastNativeFailure = TimeSource.Monotonic.markNow()
                    Napier.w("Native account recovery remains pending")
                } finally {
                    hasAttemptedCloudRestoreSignIn = pendingNativeRecovery == null
                }
            }
    }

    private suspend fun tryRestoreSignInAfterCloudRestoreInternal(
        recoverMissingContent: Boolean,
        onAccountResolved: () -> Unit,
    ): Boolean {
        if (sessionStorage.hasValidSession()) {
            onAccountResolved()
            if (recoverMissingContent) onCloudRestoreSignIn()
            return true
        }
        networkState.first { it is NetworkState.Connected }

        Napier.i("Cloud restore detected with no valid session — attempting restore sign-in")
        return when (val result = tryRestoreSignInUseCase()) {
            is TryRestoreSignInUseCase.Result.Success -> {
                Napier.i("Restore sign-in succeeded for ${result.account.username}")
                onAccountResolved()
                if (recoverMissingContent) onCloudRestoreSignIn()
                true
            }
            is TryRestoreSignInUseCase.Result.NoCredential -> {
                Napier.d("No restore credential on cloud-restored device — using manual sign-in")
                false
            }
            is TryRestoreSignInUseCase.Result.Error -> {
                Napier.i("Restore sign-in fell back to manual sign-in: ${result.message}")
                false
            }
        }
    }

    /** Requests the latest Cloud archive after an existing account signs in on this device. */
    fun requestCloudRestoreAfterSignIn() {
        onCloudRestoreSignIn()
    }

    /**
     * Show the biometric prompt to the user.
     *
     * This relies on native OS biometric unlock mechanisms.
     */
    fun showNativeUnlockPrompt() = authenticateNativeUnlock(requestEnrollmentIfNecessary = true)

    private fun authenticateNativeUnlock(requestEnrollmentIfNecessary: Boolean) {
        val request = ++unlockRequest
        biometricGatekeeper.authenticate(requestEnrollmentIfNecessary = requestEnrollmentIfNecessary) { result ->
            val deliveredInForeground = appIsForegrounded
            val generation = foregroundGeneration
            viewModelScope.launch {
                if (result == AppAuthState.AUTHENTICATED &&
                    deliveredInForeground &&
                    appIsForegrounded &&
                    generation == foregroundGeneration &&
                    request == unlockRequest
                ) {
                    appLockState.value = AppLockState.Unlocked
                }
            }
        }
    }

    /** Rechecks security after returning from destination-device enrollment. */
    fun onAppForegrounded() {
        appIsForegrounded = true
        retryPendingNativeRecovery()
        if (securityLevelState.value == AppSecurityLevel.BIOMETRIC &&
            biometricState.value == AppAuthState.REQUEST_ENROLLMENT
        ) {
            authenticateNativeUnlock(requestEnrollmentIfNecessary = false)
        }
    }

    /** Requires a new challenge when the app returns from the background. */
    fun onAppBackgrounded() {
        appIsForegrounded = false
        foregroundGeneration += 1
        if (securityLevelState.value == AppSecurityLevel.BIOMETRIC) {
            appLockState.value = AppLockState.Locked
        }
    }

    private class NativeAccountRecoveryRequest(
        val recoverMissingContent: Boolean,
        val onAccountResolved: () -> Unit,
    )

    private companion object {
        const val RETRY_DELAY_MS = 15_000L
        const val ATTEMPT_TIMEOUT_MS = 30_000L
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
        AppAuthState.AUTHENTICATED ->
            if (appLockState == AppLockState.Unlocked) AppAuthState.AUTHENTICATED else AppAuthState.REQUIRE_PROMPT
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
