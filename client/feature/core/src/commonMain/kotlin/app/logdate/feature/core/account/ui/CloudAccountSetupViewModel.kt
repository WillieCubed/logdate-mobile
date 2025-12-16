package app.logdate.feature.core.account.ui

import app.logdate.client.domain.account.CheckUsernameAvailabilityUseCase
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.domain.account.CreateRemoteAccountUseCase
import app.logdate.shared.model.CloudAccount
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Unified ViewModel for the entire cloud account setup flow.
 * Manages all steps in the account creation process as a state machine.
 */
class CloudAccountSetupViewModel(
    private val checkUsernameAvailabilityUseCase: CheckUsernameAvailabilityUseCase,
    private val createPasskeyAccountUseCase: CreatePasskeyAccountUseCase,
    private val createRemoteAccountUseCase: CreateRemoteAccountUseCase,
    private val coroutineContext: CoroutineContext = Dispatchers.Main
) {
    private val _uiState = MutableStateFlow(CloudAccountSetupState())
    val uiState: StateFlow<CloudAccountSetupState> = _uiState.asStateFlow()
    
    private val scope = CoroutineScope(coroutineContext)
    
    // Navigation functions
    fun moveToStep(step: SetupStep) {
        _uiState.update { it.copy(currentStep = step) }
    }
    
    fun goToNextStep() {
        val currentStep = _uiState.value.currentStep
        val nextStep = when (currentStep) {
            SetupStep.INTRO -> SetupStep.USERNAME_SELECTION
            SetupStep.USERNAME_SELECTION -> SetupStep.DISPLAY_NAME_SELECTION
            SetupStep.DISPLAY_NAME_SELECTION -> SetupStep.PASSKEY_CREATION
            SetupStep.PASSKEY_CREATION -> SetupStep.COMPLETION
            SetupStep.COMPLETION -> SetupStep.COMPLETION // Already at the last step
        }
        
        moveToStep(nextStep)
    }
    
    fun goToPreviousStep() {
        val currentStep = _uiState.value.currentStep
        val previousStep = when (currentStep) {
            SetupStep.INTRO -> SetupStep.INTRO // Already at the first step
            SetupStep.USERNAME_SELECTION -> SetupStep.INTRO
            SetupStep.DISPLAY_NAME_SELECTION -> SetupStep.USERNAME_SELECTION
            SetupStep.PASSKEY_CREATION -> SetupStep.DISPLAY_NAME_SELECTION
            SetupStep.COMPLETION -> SetupStep.PASSKEY_CREATION
        }
        
        moveToStep(previousStep)
    }
    
    fun skipCloudSetup() {
        _uiState.update { it.copy(isSkipped = true) }
    }
    
    // Input handling functions
    fun updateUsername(username: String) {
        _uiState.update { 
            it.copy(
                username = username,
                usernameError = null,
                usernameAvailability = UsernameAvailability.UNKNOWN
            ) 
        }
    }
    
    fun updateDisplayName(displayName: String) {
        _uiState.update { 
            it.copy(
                displayName = displayName,
                displayNameError = null
            ) 
        }
    }
    
    fun updateBio(bio: String) {
        _uiState.update { it.copy(bio = bio) }
    }
    
    // Step-specific actions
    fun checkUsernameAvailability() {
        val username = _uiState.value.username
        
        if (username.isBlank()) {
            _uiState.update { it.copy(usernameError = "Username cannot be empty") }
            return
        }
        
        if (!isValidUsername(username)) {
            _uiState.update { it.copy(usernameError = "Username can only contain letters, numbers, and underscores") }
            return
        }
        
        _uiState.update { it.copy(
            isCheckingAvailability = true,
            usernameAvailability = UsernameAvailability.CHECKING
        ) }
        
        scope.launch {
            try {
                val result = checkUsernameAvailabilityUseCase(username)
                
                when (result) {
                    is CheckUsernameAvailabilityUseCase.Result.Success -> {
                        _uiState.update { it.copy(
                            isCheckingAvailability = false,
                            usernameAvailability = if (result.isAvailable) {
                                UsernameAvailability.AVAILABLE
                            } else {
                                UsernameAvailability.TAKEN
                            }
                        ) }
                    }
                    is CheckUsernameAvailabilityUseCase.Result.Error -> {
                        val errorMessage = when (result.error) {
                            is CheckUsernameAvailabilityUseCase.AvailabilityCheckError.InvalidUsername ->
                                "Username format is invalid"
                            is CheckUsernameAvailabilityUseCase.AvailabilityCheckError.NetworkError ->
                                "Network error checking username"
                            is CheckUsernameAvailabilityUseCase.AvailabilityCheckError.Unknown ->
                                "Unknown error checking username"
                        }
                        
                        _uiState.update { it.copy(
                            isCheckingAvailability = false,
                            usernameAvailability = UsernameAvailability.ERROR,
                            errorMessage = errorMessage
                        ) }
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to check username availability", e)
                _uiState.update { it.copy(
                    isCheckingAvailability = false,
                    usernameAvailability = UsernameAvailability.ERROR,
                    errorMessage = "Could not check username availability. Please try again."
                ) }
            }
        }
    }
    
    fun createPasskey() {
        _uiState.update { it.copy(isCreatingPasskey = true) }
        
        scope.launch {
            try {
                val result = createPasskeyAccountUseCase(
                    username = _uiState.value.username,
                    displayName = _uiState.value.displayName
                )
                
                _uiState.update { it.copy(
                    isCreatingPasskey = false,
                    passkeyCreated = true,
                    accountId = result.toString()
                ) }
                
                // Automatically move to account creation after passkey is created
                createAccount()
            } catch (e: Exception) {
                Napier.e("Failed to create passkey", e)
                _uiState.update { it.copy(
                    isCreatingPasskey = false,
                    errorMessage = "Failed to create passkey. Please try again."
                ) }
            }
        }
    }
    
    private fun createAccount() {
        val state = _uiState.value
        if (!state.passkeyCreated || state.accountId.isNullOrBlank()) {
            _uiState.update { it.copy(
                errorMessage = "Passkey must be created before creating account"
            ) }
            return
        }
        
        _uiState.update { it.copy(isCreatingAccount = true) }
        
        scope.launch {
            try {
                // Use the parameters required by CreateRemoteAccountUseCase
                val result = createRemoteAccountUseCase(
                    username = state.username,
                    displayName = state.displayName
                )
                
                when (result) {
                    is CreateRemoteAccountUseCase.Result.Success -> {
                        _uiState.update { it.copy(
                            isCreatingAccount = false,
                            isAccountCreated = true,
                            currentStep = SetupStep.COMPLETION
                        ) }
                    }
                    is CreateRemoteAccountUseCase.Result.Error -> {
                        val errorMessage = when (result.error) {
                            is CreateRemoteAccountUseCase.AccountCreationError.NetworkError ->
                                "Network error creating account"
                            is CreateRemoteAccountUseCase.AccountCreationError.UsernameTaken ->
                                "Username is already taken"
                            is CreateRemoteAccountUseCase.AccountCreationError.InvalidData ->
                                "Invalid account data"
                            is CreateRemoteAccountUseCase.AccountCreationError.Unknown ->
                                "Unknown error creating account"
                        }
                        
                        _uiState.update { it.copy(
                            isCreatingAccount = false,
                            errorMessage = errorMessage
                        ) }
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to create cloud account", e)
                _uiState.update { it.copy(
                    isCreatingAccount = false,
                    errorMessage = "Failed to create account. Please try again."
                ) }
            }
        }
    }
    
    // Validation helpers
    private fun isValidUsername(username: String): Boolean {
        return username.matches(Regex("^[a-zA-Z0-9_]+$"))
    }
    
    // Error handling
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * Represents the full state of the cloud account setup flow.
 */
data class CloudAccountSetupState(
    val currentStep: SetupStep = SetupStep.INTRO,
    
    // User inputs
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    
    // Validation states
    val usernameError: String? = null,
    val displayNameError: String? = null,
    val usernameAvailability: UsernameAvailability = UsernameAvailability.UNKNOWN,
    
    // Progress indicators
    val isCheckingAvailability: Boolean = false,
    val isCreatingPasskey: Boolean = false,
    val isCreatingAccount: Boolean = false,
    
    // Completion states
    val passkeyCreated: Boolean = false,
    val isAccountCreated: Boolean = false,
    val accountId: String? = null,
    val isSkipped: Boolean = false,
    
    // Error state
    val errorMessage: String? = null
) {
    // Helper properties for step-specific validation
    val canProceedFromUsername: Boolean 
        get() = usernameAvailability == UsernameAvailability.AVAILABLE && 
                usernameError == null && 
                username.isNotBlank()
    
    val canProceedFromDisplayName: Boolean
        get() = displayName.isNotBlank() && displayNameError == null
    
    val canCreatePasskey: Boolean
        get() = canProceedFromUsername && canProceedFromDisplayName
}

/**
 * Steps in the cloud account setup flow.
 */
enum class SetupStep {
    INTRO,
    USERNAME_SELECTION,
    DISPLAY_NAME_SELECTION,
    PASSKEY_CREATION,
    COMPLETION
}

// UsernameAvailability enum is already defined in AccountUtils