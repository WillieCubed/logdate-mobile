package app.logdate.feature.core.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.account.CheckUsernameAvailabilityUseCase
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.permissions.PasskeyManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PasskeyAccountCreationViewModel(
    private val createPasskeyAccountUseCase: CreatePasskeyAccountUseCase,
    private val checkUsernameAvailabilityUseCase: CheckUsernameAvailabilityUseCase,
    private val passkeyManager: PasskeyManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PasskeyAccountCreationUiState())
    val uiState: StateFlow<PasskeyAccountCreationUiState> = _uiState.asStateFlow()
    
    private var usernameCheckJob: Job? = null
    
    init {
        checkPasskeySupport()
    }
    
    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(
            username = username,
            usernameError = null
        )
        
        // Cancel previous username check
        usernameCheckJob?.cancel()
        
        if (username.isNotBlank() && username.length >= 3) {
            usernameCheckJob = viewModelScope.launch {
                checkUsernameAvailability(username)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                usernameAvailability = UsernameAvailability.Unknown
            )
        }
    }
    
    fun updateDisplayName(displayName: String) {
        _uiState.value = _uiState.value.copy(
            displayName = displayName,
            displayNameError = null
        )
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
        
        _uiState.value = currentState.copy(
            isCreatingAccount = true,
            errorMessage = null
        )
        
        viewModelScope.launch {
            val result = createPasskeyAccountUseCase(
                username = currentState.username,
                displayName = currentState.displayName,
                bio = currentState.bio.takeIf { it.isNotBlank() }
            )
            
            when (result) {
                is CreatePasskeyAccountUseCase.Result.Success -> {
                    _uiState.value = currentState.copy(
                        isCreatingAccount = false,
                        isAccountCreated = true,
                        createdAccount = result.account
                    )
                }
                is CreatePasskeyAccountUseCase.Result.Error -> {
                    _uiState.value = currentState.copy(
                        isCreatingAccount = false,
                        errorMessage = mapErrorToMessage(result.error)
                    )
                }
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun resetState() {
        _uiState.value = PasskeyAccountCreationUiState()
        checkPasskeySupport()
    }
    
    private fun checkPasskeySupport() {
        viewModelScope.launch {
            val capabilities = passkeyManager.getCapabilities()
            _uiState.value = _uiState.value.copy(
                isPasskeySupported = capabilities.isSupported,
                isPlatformAuthenticatorAvailable = capabilities.isPlatformAuthenticatorAvailable
            )
        }
    }
    
    private suspend fun checkUsernameAvailability(username: String) {
        _uiState.value = _uiState.value.copy(
            usernameAvailability = UsernameAvailability.Checking
        )
        
        val result = checkUsernameAvailabilityUseCase(username)
        
        _uiState.value = _uiState.value.copy(
            usernameAvailability = when (result) {
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
            }
        )
    }
    
    private fun validateInputs(): Boolean {
        val currentState = _uiState.value
        var isValid = true
        
        // Username validation
        if (currentState.username.isBlank() || currentState.username.length < 3) {
            _uiState.value = currentState.copy(
                usernameError = "Username must be at least 3 characters"
            )
            isValid = false
        } else if (!currentState.username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            _uiState.value = currentState.copy(
                usernameError = "Username can only contain letters, numbers, and underscores"
            )
            isValid = false
        } else if (currentState.usernameAvailability == UsernameAvailability.Taken) {
            _uiState.value = currentState.copy(
                usernameError = "Username is already taken"
            )
            isValid = false
        }
        
        // Display name validation
        if (currentState.displayName.isBlank()) {
            _uiState.value = _uiState.value.copy(
                displayNameError = "Display name is required"
            )
            isValid = false
        } else if (currentState.displayName.length > 100) {
            _uiState.value = _uiState.value.copy(
                displayNameError = "Display name must be less than 100 characters"
            )
            isValid = false
        }
        
        return isValid
    }
    
    private fun mapErrorToMessage(error: CreatePasskeyAccountUseCase.CreateAccountError): String {
        return when (error) {
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
}

data class PasskeyAccountCreationUiState(
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val usernameError: String? = null,
    val displayNameError: String? = null,
    val usernameAvailability: UsernameAvailability = UsernameAvailability.Unknown,
    val isPasskeySupported: Boolean = true,
    val isPlatformAuthenticatorAvailable: Boolean = true,
    val isCreatingAccount: Boolean = false,
    val isAccountCreated: Boolean = false,
    val createdAccount: app.logdate.shared.model.LogDateAccount? = null,
    val errorMessage: String? = null
) {
    val canCreateAccount: Boolean
        get() = username.isNotBlank() && 
                displayName.isNotBlank() && 
                usernameError == null && 
                displayNameError == null && 
                usernameAvailability == UsernameAvailability.Available &&
                isPasskeySupported &&
                !isCreatingAccount
}

enum class UsernameAvailability {
    Unknown,
    Checking,
    Available,
    Taken,
    Error
}