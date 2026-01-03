package app.logdate.feature.core.account.ui

import androidx.lifecycle.ViewModel
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.domain.account.CreateRemoteAccountUseCase
import app.logdate.client.domain.account.GetAccountSetupDataUseCase
import app.logdate.shared.model.CloudAccount
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


/**
 * ViewModel for the passkey creation screen in the account setup flow.
 */
class PasskeyCreationViewModel(
    private val createPasskeyAccountUseCase: CreatePasskeyAccountUseCase,
    private val createRemoteAccountUseCase: CreateRemoteAccountUseCase,
    private val getAccountSetupDataUseCase: GetAccountSetupDataUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(PasskeyCreationUiState())
    val uiState: StateFlow<PasskeyCreationUiState> = _uiState.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    /**
     * Creates a passkey and account using the provided data.
     */
    fun createPasskeyAndAccount(username: String, displayName: String) {
        // Check that we have required data
        if (username.isBlank() || displayName.isBlank()) {
            _uiState.update { 
                it.copy(
                    errorMessage = "Username and display name are required to create a passkey"
                ) 
            }
            return
        }
        
        // Set creating state
        _uiState.update { it.copy(isCreatingPasskey = true) }
        
        // Create passkey
        scope.launch {
            try {
                // First create the passkey
                val result = createPasskeyAccountUseCase(
                    username = username,
                    displayName = displayName
                )

                when (result) {
                    is CreatePasskeyAccountUseCase.Result.Success -> {
                        _uiState.update { 
                            it.copy(
                                isCreatingPasskey = false,
                                passkeyCreated = true
                            ) 
                        }
                        // Then create the cloud account
                        createCloudAccount(username, displayName)
                    }
                    is CreatePasskeyAccountUseCase.Result.Error -> {
                        _uiState.update { 
                            it.copy(
                                isCreatingPasskey = false,
                                errorMessage = "Failed to create passkey. Please try again."
                            ) 
                        }
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to create passkey", e)
                _uiState.update { 
                    it.copy(
                        isCreatingPasskey = false,
                        errorMessage = "Failed to create passkey. Please try again."
                    ) 
                }
            }
        }
    }
    
    /**
     * Creates a passkey using account setup data retrieved from the use case.
     * This method is called from the PasskeyCreationScreen.
     */
    fun createPasskey() {
        scope.launch {
            try {
                // Get the account data from the use case
                val accountData = getAccountSetupDataUseCase()
                
                if (accountData.username.isBlank() || accountData.displayName.isBlank()) {
                    _uiState.update { 
                        it.copy(
                            errorMessage = "Username and display name are required. Please go back and complete those steps."
                        ) 
                    }
                    return@launch
                }
                
                // Call the existing method with the actual data
                createPasskeyAndAccount(accountData.username, accountData.displayName)
            } catch (e: Exception) {
                AccountUtils.logError("Failed to get account data", e)
                _uiState.update { 
                    it.copy(
                        errorMessage = "Failed to retrieve account information. Please try again."
                    ) 
                }
            }
        }
    }
    
    private suspend fun createCloudAccount(username: String, displayName: String) {
        _uiState.update { it.copy(isCreatingAccount = true) }
        
        try {
            // Create the account using the use case that now takes username and displayName directly
            val result = createRemoteAccountUseCase(
                username = username,
                displayName = displayName
            )
            
            // Clear the account setup data since we're done with it
            when (result) {
                is CreateRemoteAccountUseCase.Result.Success -> {
                    getAccountSetupDataUseCase(
                        action = GetAccountSetupDataUseCase.Action.Clear
                    )

                    _uiState.update { 
                        it.copy(
                            isCreatingAccount = false,
                            accountCreated = true,
                            navigateToNextScreen = true
                        ) 
                    }
                }
                is CreateRemoteAccountUseCase.Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isCreatingAccount = false,
                            errorMessage = "Failed to create account. Please try again."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Napier.e("Failed to create cloud account", e)
            _uiState.update { 
                it.copy(
                    isCreatingAccount = false,
                    errorMessage = "Failed to create account. Please try again."
                ) 
            }
        }
    }
    
    /**
     * Clears the error message.
     */
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * UI state for the passkey creation screen.
 */
data class PasskeyCreationUiState(
    val isCreatingPasskey: Boolean = false,
    val passkeyCreated: Boolean = false,
    val isCreatingAccount: Boolean = false,
    val accountCreated: Boolean = false,
    val errorMessage: String? = null,
    val navigateToNextScreen: Boolean = false
)
