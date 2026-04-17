package app.logdate.feature.core.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.account.AuthenticateWithPasskeyUseCase
import app.logdate.client.domain.account.CheckUsernameAvailabilityUseCase
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.domain.account.TriggerInitialSyncUseCase
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.feature.core.settings.ui.ServerConfigurationCoordinator
import app.logdate.feature.core.settings.ui.ServerPreset
import app.logdate.feature.core.settings.ui.ServerSelectionState
import app.logdate.feature.core.settings.ui.ServerValidationState
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.ServerDescriptor
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CloudAccountOnboardingViewModel(
    private val createPasskeyAccountUseCase: CreatePasskeyAccountUseCase,
    private val checkUsernameAvailabilityUseCase: CheckUsernameAvailabilityUseCase,
    private val authenticateWithPasskeyUseCase: AuthenticateWithPasskeyUseCase,
    private val triggerInitialSyncUseCase: TriggerInitialSyncUseCase,
    private val passkeyManager: PasskeyManager,
    private val profileRepository: ProfileRepository,
    private val serverConfigurationCoordinator: ServerConfigurationCoordinator,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            CloudAccountOnboardingUiState(
                serverSelectionState = serverConfigurationCoordinator.initialSelectionState(),
            ),
        )
    val uiState: StateFlow<CloudAccountOnboardingUiState> = _uiState.asStateFlow()

    private var usernameCheckJob: Job? = null
    private var entryStep: OnboardingStep = OnboardingStep.Welcome

    init {
        checkPasskeySupport()
    }

    /**
     * Sets the initial step for the flow, skipping earlier steps.
     *
     * **Side-effects:**
     * - Updates [CloudAccountOnboardingUiState.currentStep] to [step].
     * - Records [step] as the entry point: [goToPreviousStep] will set
     *   [CloudAccountOnboardingUiState.isExitRequested] instead of navigating
     *   when the user is already at this step.
     * - When [step] is not [OnboardingStep.Welcome], triggers
     *   [prepareServerSelection] to validate and persist the server
     *   configuration (required before DisplayName/SignIn steps can proceed).
     *   This launches a coroutine that may update [CloudAccountOnboardingUiState.serverSelectionState].
     *
     * Should be called once before the composable renders, typically from a
     * `LaunchedEffect(Unit)` block.
     */
    fun setInitialStep(step: OnboardingStep) {
        entryStep = step
        _uiState.value = _uiState.value.copy(currentStep = step)
        if (step != OnboardingStep.Welcome) {
            prepareServerSelection(step)
        }
    }

    fun goToNextStep() {
        val currentStep = _uiState.value.currentStep
        when (currentStep) {
            OnboardingStep.Welcome -> prepareServerSelection(OnboardingStep.DisplayName)
            OnboardingStep.SignIn -> _uiState.value = _uiState.value.copy(currentStep = OnboardingStep.Complete)
            OnboardingStep.DisplayName -> _uiState.value = _uiState.value.copy(currentStep = OnboardingStep.Username)
            OnboardingStep.Username -> _uiState.value = _uiState.value.copy(currentStep = OnboardingStep.PasskeyCreation)
            OnboardingStep.PasskeyCreation -> _uiState.value = _uiState.value.copy(currentStep = OnboardingStep.Complete)
            OnboardingStep.Complete -> _uiState.value = _uiState.value.copy(currentStep = OnboardingStep.Complete)
        }
    }

    fun goToPreviousStep() {
        val currentStep = _uiState.value.currentStep
        if (currentStep == entryStep) {
            _uiState.value = _uiState.value.copy(isExitRequested = true)
            return
        }
        val previousStep =
            when (currentStep) {
                OnboardingStep.Welcome -> OnboardingStep.Welcome
                OnboardingStep.SignIn -> OnboardingStep.Welcome
                OnboardingStep.DisplayName -> OnboardingStep.Welcome
                OnboardingStep.Username -> OnboardingStep.DisplayName
                OnboardingStep.PasskeyCreation -> OnboardingStep.Username
                OnboardingStep.Complete -> OnboardingStep.Complete
            }

        _uiState.value = _uiState.value.copy(currentStep = previousStep)
    }

    fun updateDisplayName(displayName: String) {
        _uiState.value =
            _uiState.value.copy(
                displayName = displayName,
                displayNameError = null,
            )
    }

    fun updateUsername(username: String) {
        _uiState.value =
            _uiState.value.copy(
                username = username,
                usernameError = null,
            )

        // Cancel previous username check
        usernameCheckJob?.cancel()

        if (username.isNotBlank() && username.length >= 3) {
            usernameCheckJob =
                viewModelScope.launch {
                    delay(300)
                    checkUsernameAvailability(username)
                }
        } else {
            _uiState.value =
                _uiState.value.copy(
                    usernameAvailability = UsernameAvailability.Unknown,
                )
        }
    }

    fun updateBio(bio: String) {
        _uiState.value = _uiState.value.copy(bio = bio)
    }

    fun createAccount() {
        val currentState = _uiState.value

        // Validate inputs
        if (!validateInputs()) {
            return
        }

        _uiState.value =
            currentState.copy(
                isCreatingAccount = true,
                errorMessage = null,
            )

        viewModelScope.launch {
            val result =
                createPasskeyAccountUseCase(
                    username = currentState.username,
                    displayName = currentState.displayName,
                    bio = currentState.bio.takeIf { it.isNotBlank() },
                )

            when (result) {
                is CreatePasskeyAccountUseCase.Result.Success -> {
                    syncDisplayNameToLocalProfile(result.account.displayName)
                    _uiState.value =
                        _uiState.value.copy(
                            isCreatingAccount = false,
                            createdAccount = result.account,
                            currentStep = OnboardingStep.Complete,
                            isInitialSyncing = true,
                            initialSyncStatus = InitialSyncStatus.Running,
                        )
                    performInitialSync(markCompletedBy = SyncCompletion.ACCOUNT_CREATED)
                }
                is CreatePasskeyAccountUseCase.Result.Error -> {
                    _uiState.value =
                        currentState.copy(
                            isCreatingAccount = false,
                            errorMessage = mapErrorToMessage(result.error),
                        )
                }
            }
        }
    }

    /**
     * Runs the blocking portion of the first sync and transitions to [OnboardingStep.Complete]
     * when it settles (success, partial, timeout, or error). The onboarding flow does not fail
     * because sync had issues — we still let the user into the app — but the UI reflects the
     * outcome so the global sync-status banner can prompt retries downstream.
     *
     * [markCompletedBy] determines which terminal flag flips at the end. This is what drives the
     * screen's completion LaunchedEffect, so it must only flip once sync is done — otherwise the
     * host navigates away mid-sync and the user never sees the sync progress state.
     */
    private suspend fun performInitialSync(markCompletedBy: SyncCompletion) {
        val syncResult = triggerInitialSyncUseCase()
        val status =
            when (syncResult) {
                is TriggerInitialSyncUseCase.Result.Success -> InitialSyncStatus.Success
                is TriggerInitialSyncUseCase.Result.Partial -> {
                    Napier.w(
                        "Initial sync partial: uploads=${syncResult.uploadedItems}, " +
                            "downloads=${syncResult.downloadedItems}, errors=${syncResult.errorMessages}",
                    )
                    InitialSyncStatus.Partial
                }
                is TriggerInitialSyncUseCase.Result.TimedOut -> InitialSyncStatus.TimedOut
                is TriggerInitialSyncUseCase.Result.Error -> InitialSyncStatus.Failed
            }
        _uiState.value =
            _uiState.value.copy(
                isInitialSyncing = false,
                initialSyncStatus = status,
                currentStep = OnboardingStep.Complete,
                isAccountCreated = _uiState.value.isAccountCreated || markCompletedBy == SyncCompletion.ACCOUNT_CREATED,
                isSignedIn = _uiState.value.isSignedIn || markCompletedBy == SyncCompletion.SIGNED_IN,
            )
    }

    private enum class SyncCompletion {
        ACCOUNT_CREATED,
        SIGNED_IN,
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun goToSignIn() {
        prepareServerSelection(OnboardingStep.SignIn)
    }

    fun signInWithPasskey(username: String) {
        _uiState.value =
            _uiState.value.copy(
                isSigningIn = true,
                errorMessage = null,
            )

        viewModelScope.launch {
            val usernameParam = username.takeIf { it.isNotBlank() }
            val result = authenticateWithPasskeyUseCase(usernameParam)

            when (result) {
                is AuthenticateWithPasskeyUseCase.Result.Success -> {
                    syncDisplayNameToLocalProfile(result.account.displayName)
                    _uiState.value =
                        _uiState.value.copy(
                            isSigningIn = false,
                            createdAccount = result.account,
                            currentStep = OnboardingStep.Complete,
                            isInitialSyncing = true,
                            initialSyncStatus = InitialSyncStatus.Running,
                        )
                    performInitialSync(markCompletedBy = SyncCompletion.SIGNED_IN)
                }
                is AuthenticateWithPasskeyUseCase.Result.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSigningIn = false,
                            errorMessage = mapAuthErrorToMessage(result.error),
                        )
                }
            }
        }
    }

    fun skipOnboarding() {
        _uiState.value =
            _uiState.value.copy(
                currentStep = OnboardingStep.Complete,
                isSkipped = true,
            )
    }

    fun resetFlow() {
        _uiState.value =
            CloudAccountOnboardingUiState(
                serverSelectionState = serverConfigurationCoordinator.initialSelectionState(),
            )
        checkPasskeySupport()
    }

    fun selectServerPreset(preset: ServerPreset) {
        _uiState.value =
            _uiState.value.copy(
                serverSelectionState =
                    _uiState.value.serverSelectionState.copy(
                        selectedPreset = preset,
                        validationState = ServerValidationState.Idle,
                    ),
                errorMessage = null,
            )
    }

    fun updateCustomServerUrl(url: String) {
        _uiState.value =
            _uiState.value.copy(
                serverSelectionState =
                    _uiState.value.serverSelectionState.copy(
                        customServerUrl = url,
                        validationState = ServerValidationState.Idle,
                    ),
                errorMessage = null,
            )
    }

    private fun checkPasskeySupport() {
        viewModelScope.launch {
            val capabilities = passkeyManager.getCapabilities()
            _uiState.value =
                _uiState.value.copy(
                    isPasskeySupported = capabilities.isSupported,
                    isPlatformAuthenticatorAvailable = capabilities.isPlatformAuthenticatorAvailable,
                )
        }
    }

    private suspend fun checkUsernameAvailability(username: String) {
        _uiState.value =
            _uiState.value.copy(
                usernameAvailability = UsernameAvailability.Checking,
            )

        val result = checkUsernameAvailabilityUseCase(username)

        _uiState.value =
            _uiState.value.copy(
                usernameAvailability =
                    when (result) {
                        is CheckUsernameAvailabilityUseCase.Result.Success -> {
                            if (result.isAvailable) {
                                UsernameAvailability.Available
                            } else {
                                UsernameAvailability.Taken
                            }
                        }
                        is CheckUsernameAvailabilityUseCase.Result.Error -> {
                            UsernameAvailability.Error
                        }
                    },
            )
    }

    private fun prepareServerSelection(nextStep: OnboardingStep) {
        viewModelScope.launch {
            val serverSelectionState = _uiState.value.serverSelectionState
            when (serverSelectionState.selectedPreset) {
                ServerPreset.PRODUCTION ->
                    persistServerSelection(
                        nextStep = nextStep,
                        failureMessage = "Failed to configure LogDate Cloud",
                    ) {
                        serverConfigurationCoordinator.saveLogDateCloudSelection()
                    }

                ServerPreset.CUSTOM -> {
                    val customServerUrl = serverSelectionState.customServerUrl
                    if (customServerUrl.isBlank()) {
                        updateServerSelectionState(
                            validationState = ServerValidationState.Error("Server URL cannot be empty"),
                            errorMessage = "Server URL cannot be empty",
                        )
                        return@launch
                    }

                    updateServerSelectionState(
                        validationState = ServerValidationState.Validating,
                        errorMessage = null,
                    )

                    persistServerSelection(
                        nextStep = nextStep,
                        failureMessage = "Failed to connect to server",
                    ) {
                        serverConfigurationCoordinator.validateAndSaveCustomServer(customServerUrl)
                    }
                }
            }
        }
    }

    private suspend fun persistServerSelection(
        nextStep: OnboardingStep,
        failureMessage: String,
        save: suspend () -> Result<ServerConfigurationCoordinator.SaveResult>,
    ) {
        save()
            .onSuccess { result ->
                updateServerSelectionState(
                    validationState = ServerValidationState.Success(result.serverVersion),
                    errorMessage = null,
                    nextStep = nextStep,
                    activeServerDescriptor = result.descriptor,
                    customServerUrl =
                        result.serverOrigin.takeIf {
                            _uiState.value.serverSelectionState.selectedPreset == ServerPreset.CUSTOM
                        },
                )
            }.onFailure { error ->
                updateServerSelectionState(
                    validationState = ServerValidationState.Error(error.message ?: failureMessage),
                    errorMessage = error.message ?: failureMessage,
                )
            }
    }

    private fun updateServerSelectionState(
        validationState: ServerValidationState,
        errorMessage: String?,
        nextStep: OnboardingStep? = null,
        activeServerDescriptor: ServerDescriptor? = _uiState.value.serverSelectionState.activeServerDescriptor,
        customServerUrl: String? = null,
    ) {
        _uiState.value =
            _uiState.value.copy(
                currentStep = nextStep ?: _uiState.value.currentStep,
                errorMessage = errorMessage,
                serverSelectionState =
                    _uiState.value.serverSelectionState.copy(
                        validationState = validationState,
                        activeServerDescriptor = activeServerDescriptor,
                        customServerUrl = customServerUrl ?: _uiState.value.serverSelectionState.customServerUrl,
                    ),
            )
    }

    private fun validateInputs(): Boolean {
        val currentState = _uiState.value
        var isValid = true

        // Display name validation
        if (currentState.displayName.isBlank()) {
            _uiState.value =
                currentState.copy(
                    displayNameError = "Display name is required",
                )
            isValid = false
        } else if (currentState.displayName.length > 100) {
            _uiState.value =
                currentState.copy(
                    displayNameError = "Display name must be less than 100 characters",
                )
            isValid = false
        }

        // Username validation
        if (currentState.username.isBlank() || currentState.username.length < 3) {
            _uiState.value =
                currentState.copy(
                    usernameError = "Username must be at least 3 characters",
                )
            isValid = false
        } else if (!currentState.username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            _uiState.value =
                currentState.copy(
                    usernameError = "Username can only contain letters, numbers, and underscores",
                )
            isValid = false
        } else if (currentState.usernameAvailability == UsernameAvailability.Taken) {
            _uiState.value =
                currentState.copy(
                    usernameError = "Username is already taken",
                )
            isValid = false
        }

        return isValid
    }

    private suspend fun syncDisplayNameToLocalProfile(displayName: String) {
        if (displayName.isNotBlank()) {
            profileRepository.updateDisplayName(displayName).onFailure { e ->
                Napier.e("Failed to sync display name to local profile", e)
            }
        }
    }

    private fun mapAuthErrorToMessage(error: AuthenticateWithPasskeyUseCase.AuthenticationError): String =
        when (error) {
            AuthenticateWithPasskeyUseCase.AuthenticationError.PasskeyNotSupported ->
                "Passkeys are not supported on this device."
            AuthenticateWithPasskeyUseCase.AuthenticationError.PasskeyCancelled ->
                "Authentication was cancelled. Please try again."
            AuthenticateWithPasskeyUseCase.AuthenticationError.PasskeyFailed ->
                "Failed to authenticate with passkey. Please try again."
            AuthenticateWithPasskeyUseCase.AuthenticationError.NoCredentialsFound ->
                "No passkey found for this account. Please check your username or create a new account."
            AuthenticateWithPasskeyUseCase.AuthenticationError.AccountNotFound ->
                "Account not found. Please check your username or create a new account."
            AuthenticateWithPasskeyUseCase.AuthenticationError.NetworkError ->
                "Network error. Please check your connection and try again."
            is AuthenticateWithPasskeyUseCase.AuthenticationError.Unknown ->
                "An unexpected error occurred: ${error.message}"
        }

    private fun mapErrorToMessage(error: CreatePasskeyAccountUseCase.CreateAccountError): String =
        when (error) {
            CreatePasskeyAccountUseCase.CreateAccountError.UsernameTaken ->
                "Username is already taken. Please choose a different one."
            CreatePasskeyAccountUseCase.CreateAccountError.UsernameInvalid ->
                "Username is invalid. Use only letters, numbers, and underscores."
            CreatePasskeyAccountUseCase.CreateAccountError.DisplayNameInvalid ->
                "Display name is invalid. Please enter a valid name."
            CreatePasskeyAccountUseCase.CreateAccountError.PasskeyNotSupported ->
                "Passkeys are not supported on this device."
            CreatePasskeyAccountUseCase.CreateAccountError.PasskeyCancelled ->
                "Passkey creation was cancelled. Please try again."
            CreatePasskeyAccountUseCase.CreateAccountError.PasskeyFailed ->
                "Failed to create passkey. Please check your device security settings."
            CreatePasskeyAccountUseCase.CreateAccountError.NetworkError ->
                "Network error. Please check your connection and try again."
            is CreatePasskeyAccountUseCase.CreateAccountError.Unknown ->
                "An unexpected error occurred: ${error.message}"
        }
}

data class CloudAccountOnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.Welcome,
    val serverSelectionState: ServerSelectionState = ServerSelectionState(),
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val displayNameError: String? = null,
    val usernameError: String? = null,
    val usernameAvailability: UsernameAvailability = UsernameAvailability.Unknown,
    val isPasskeySupported: Boolean = true,
    val isPlatformAuthenticatorAvailable: Boolean = true,
    val isCreatingAccount: Boolean = false,
    val isAccountCreated: Boolean = false,
    val isSigningIn: Boolean = false,
    val isSignedIn: Boolean = false,
    val isInitialSyncing: Boolean = false,
    val initialSyncStatus: InitialSyncStatus = InitialSyncStatus.NotStarted,
    val isSkipped: Boolean = false,
    /**
     * True when the user pressed back from the entry step set via
     * [CloudAccountOnboardingViewModel.setInitialStep], signaling
     * that the host should pop this flow from the navigation stack.
     */
    val isExitRequested: Boolean = false,
    val createdAccount: LogDateAccount? = null,
    val errorMessage: String? = null,
) {
    val canContinueFromDisplayName: Boolean
        get() = displayName.isNotBlank() && displayNameError == null

    val canContinueFromUsername: Boolean
        get() =
            username.isNotBlank() &&
                usernameError == null &&
                usernameAvailability == UsernameAvailability.Available

    val canCreateAccount: Boolean
        get() =
            canContinueFromDisplayName &&
                canContinueFromUsername &&
                isPasskeySupported &&
                !isCreatingAccount
}

enum class OnboardingStep {
    Welcome,
    SignIn,
    DisplayName,
    Username,
    PasskeyCreation,
    Complete,
}

/**
 * Outcome of the blocking first sync that follows a successful account creation or sign-in.
 *
 *  - [NotStarted]: no sync has been attempted yet.
 *  - [Running]: sync is in flight; the UI shows "Syncing your library…" instead of "Ready".
 *  - [Success]: first round-trip completed cleanly; the user has seen the server state.
 *  - [Partial]: the call finished but reported errors (e.g. some uploads failed). The user gets
 *    into the app and the global sync banner can prompt retries.
 *  - [TimedOut]: network was too slow to finish within the onboarding budget; the periodic worker
 *    will retry in the background.
 *  - [Failed]: an unexpected error escaped; surface in the banner for retry.
 */
enum class InitialSyncStatus {
    NotStarted,
    Running,
    Success,
    Partial,
    TimedOut,
    Failed,
}
