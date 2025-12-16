package app.logdate.feature.core.account.ui

import androidx.lifecycle.ViewModel
import app.logdate.client.domain.account.CheckUsernameAvailabilityUseCase
import app.logdate.client.domain.account.GetAccountSetupDataUseCase
import app.logdate.client.domain.account.AccountSetupData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Consolidated ViewModel for account setup screens except passkey creation.
 * 
 * This ViewModel handles:
 * - Intro screen
 * - Username selection screen
 * - Display name selection screen
 * - Account creation completion screen
 */
class AccountOnboardingViewModel(
    private val checkUsernameAvailabilityUseCase: CheckUsernameAvailabilityUseCase,
    private val getAccountSetupDataUseCase: GetAccountSetupDataUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountOnboardingUiState())
    val uiState: StateFlow<AccountOnboardingUiState> = _uiState.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    /**
     * Helper method to save account setup data for use in passkey creation.
     */
    private fun saveAccountSetupData(username: String, displayName: String = "") {
        scope.launch {
            try {
                val currentData = getAccountSetupDataUseCase()
                val updatedData = AccountSetupData(
                    username = username.takeIf { it.isNotBlank() } ?: currentData.username,
                    displayName = displayName.takeIf { it.isNotBlank() } ?: currentData.displayName
                )
                // Use the new action-based invoke method
                getAccountSetupDataUseCase(
                    action = GetAccountSetupDataUseCase.Action.Save,
                    data = updatedData
                )
            } catch (e: Exception) {
                AccountUtils.logError("Failed to save account setup data", e)
            }
        }
    }
    
    /**
     * Helper method to save just the username.
     */
    private fun saveAccountSetupData(username: String) {
        saveAccountSetupData(username = username, displayName = "")
    }
    
    // INTRO SCREEN FUNCTIONS
    
    /**
     * Continue from intro to username selection.
     * Note: Navigation is now handled directly by the UI callbacks.
     */
    fun onIntroContinue() {
        // This method is no longer needed for navigation
        // but kept for potential future use
    }
    
    /**
     * Skip the account setup.
     * Note: Navigation is now handled directly by the UI callbacks.
     */
    fun onIntroSkip() {
        // This method is no longer needed for navigation
        // but kept for potential future use
    }
    
    // USERNAME SELECTION FUNCTIONS
    
    /**
     * Updates the username input field.
     */
    fun onUsernameChanged(username: String) {
        _uiState.update { 
            it.copy(
                username = username,
                usernameError = null,
                usernameAvailability = UsernameAvailability.UNKNOWN
            ) 
        }
    }
    
    /**
     * Checks if the username is available.
     */
    fun checkUsernameAvailability() {
        val username = _uiState.value.username
        
        // Validate username format first
        val error = AccountUtils.getValidationErrorForUsername(username)
        if (error != null) {
            _uiState.update { it.copy(usernameError = error) }
            return
        }
        
        // Set checking state
        _uiState.update { 
            it.copy(
                usernameAvailability = UsernameAvailability.CHECKING
            ) 
        }
        
        // Check availability
        scope.launch {
            try {
                val result = checkUsernameAvailabilityUseCase(username)
                
                when (result) {
                    is CheckUsernameAvailabilityUseCase.Result.Success -> {
                        _uiState.update { 
                            it.copy(
                                usernameAvailability = if (result.isAvailable) {
                                    UsernameAvailability.AVAILABLE
                                } else {
                                    UsernameAvailability.TAKEN
                                }
                            ) 
                        }
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
                        
                        _uiState.update { 
                            it.copy(
                                usernameAvailability = UsernameAvailability.ERROR,
                                errorMessage = errorMessage
                            ) 
                        }
                    }
                }
            } catch (e: Exception) {
                AccountUtils.logError("Failed to check username availability", e)
                _uiState.update { 
                    it.copy(
                        usernameAvailability = UsernameAvailability.ERROR,
                        errorMessage = "Could not check username availability. Please try again."
                    ) 
                }
            }
        }
    }
    
    /**
     * Continue from username to display name selection.
     */
    fun onUsernameContinue() {
        // Save username to account setup data
        saveAccountSetupData(username = _uiState.value.username)
        // Navigation is now handled directly by the UI callbacks
    }
    
    /**
     * Go back from username to intro.
     * Note: Navigation is now handled directly by the UI callbacks.
     */
    fun onUsernameBack() {
        // Navigation is now handled directly by the UI callbacks
    }
    
    // DISPLAY NAME SELECTION FUNCTIONS
    
    /**
     * Updates the display name input field.
     */
    fun onDisplayNameChanged(displayName: String) {
        _uiState.update { 
            it.copy(
                displayName = displayName,
                displayNameError = null
            ) 
        }
        
        // Validate display name
        val error = AccountUtils.getValidationErrorForDisplayName(displayName)
        if (error != null) {
            _uiState.update { it.copy(displayNameError = error) }
        }
    }
    
    /**
     * Continue from display name to passkey creation.
     */
    fun onDisplayNameContinue() {
        // Validate display name
        val error = AccountUtils.getValidationErrorForDisplayName(_uiState.value.displayName)
        if (error != null) {
            _uiState.update { it.copy(displayNameError = error) }
            return
        }
        
        // Save display name to account setup data
        saveAccountSetupData(
            username = _uiState.value.username,
            displayName = _uiState.value.displayName
        )
        // Navigation is now handled directly by the UI callbacks
    }
    
    /**
     * Go back from display name to username.
     * Note: Navigation is now handled directly by the UI callbacks.
     */
    fun onDisplayNameBack() {
        // Navigation is now handled directly by the UI callbacks
    }
    
    // COMPLETION SCREEN FUNCTIONS
    
    /**
     * Finish the account setup flow.
     * Note: Navigation is now handled directly by the UI callbacks.
     */
    fun onCompletionFinish() {
        // Navigation is now handled directly by the UI callbacks
    }
    
    /**
     * Clears the error message.
     */
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * UI state for the consolidated account setup screens.
 */
data class AccountOnboardingUiState(
    // Common state
    val errorMessage: String? = null,
    
    // Username selection state
    val username: String = "",
    val usernameError: String? = null,
    val usernameAvailability: UsernameAvailability = UsernameAvailability.UNKNOWN,
    
    // Display name selection state
    val displayName: String = "",
    val displayNameError: String? = null
) {
    // Helper properties for username screen
    val canCheckUsernameAvailability: Boolean
        get() = username.isNotBlank() && 
                usernameAvailability != UsernameAvailability.CHECKING
    
    val canContinueFromUsername: Boolean
        get() = usernameAvailability == UsernameAvailability.AVAILABLE && 
                usernameError == null
                
    // Helper properties for display name screen
    val canContinueFromDisplayName: Boolean
        get() = displayName.isNotBlank() && displayNameError == null
}

/**
 * Screens in the account setup flow.
 */
enum class AccountScreen {
    INTRO,
    USERNAME_SELECTION,
    DISPLAY_NAME_SELECTION,
    COMPLETION
}