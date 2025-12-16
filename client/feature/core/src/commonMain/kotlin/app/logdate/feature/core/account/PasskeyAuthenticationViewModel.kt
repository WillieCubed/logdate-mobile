package app.logdate.feature.core.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.account.AuthenticateWithPasskeyUseCase
import app.logdate.client.permissions.PasskeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PasskeyAuthenticationViewModel(
    private val authenticateWithPasskeyUseCase: AuthenticateWithPasskeyUseCase,
    private val passkeyManager: PasskeyManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PasskeyAuthenticationUiState())
    val uiState: StateFlow<PasskeyAuthenticationUiState> = _uiState.asStateFlow()
    
    init {
        checkPasskeySupport()
    }
    
    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }
    
    fun authenticate() {
        val currentState = _uiState.value
        val username = currentState.username.takeIf { it.isNotBlank() }
        
        _uiState.value = currentState.copy(
            isAuthenticating = true,
            errorMessage = null
        )
        
        viewModelScope.launch {
            val result = authenticateWithPasskeyUseCase(username)
            
            when (result) {
                is AuthenticateWithPasskeyUseCase.Result.Success -> {
                    _uiState.value = currentState.copy(
                        isAuthenticating = false,
                        isAuthenticated = true,
                        authenticatedAccount = result.account
                    )
                }
                is AuthenticateWithPasskeyUseCase.Result.Error -> {
                    _uiState.value = currentState.copy(
                        isAuthenticating = false,
                        errorMessage = mapErrorToMessage(result.error)
                    )
                }
            }
        }
    }
    
    fun authenticateWithoutUsername() {
        _uiState.value = _uiState.value.copy(username = "")
        authenticate()
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun resetState() {
        _uiState.value = PasskeyAuthenticationUiState()
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
    
    private fun mapErrorToMessage(error: AuthenticateWithPasskeyUseCase.AuthenticationError): String {
        return when (error) {
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
    }
}

data class PasskeyAuthenticationUiState(
    val username: String = "",
    val isPasskeySupported: Boolean = true,
    val isPlatformAuthenticatorAvailable: Boolean = true,
    val isAuthenticating: Boolean = false,
    val isAuthenticated: Boolean = false,
    val authenticatedAccount: app.logdate.shared.model.LogDateAccount? = null,
    val errorMessage: String? = null
) {
    val canAuthenticate: Boolean
        get() = isPasskeySupported && !isAuthenticating
}