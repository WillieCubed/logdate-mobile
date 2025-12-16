package app.logdate.feature.core.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.profile.UpdateProfileUseCase
import app.logdate.client.repository.account.AccountRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.feature.core.profile.ui.ProfileDisplayModel
import app.logdate.feature.core.profile.ui.ProfileEditState
import app.logdate.feature.core.profile.ui.ProfileUiState
import app.logdate.feature.core.profile.ui.ProfileUpdateState
import app.logdate.feature.core.profile.ui.createProfileDisplayModel
import app.logdate.shared.model.profile.LogDateProfile
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for the profile screen with local-first approach.
 * 
 * Manages local profile data (always available) with optional cloud account integration
 * for progressive enhancement. Uses Material 3 Expressive patterns.
 */
class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val accountRepository: AccountRepository,
    private val userStateRepository: UserStateRepository,
    private val updateProfileUseCase: UpdateProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            localProfile = LogDateProfile(),
            account = null,
            userData = null,
            isLoading = true
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    /**
     * Load profile data from both local and cloud sources.
     * Local profile data is always available, cloud account data provides progressive enhancement.
     */
    private fun loadProfile() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                combine(
                    profileRepository.currentProfile,
                    accountRepository.currentAccount,
                    userStateRepository.userData
                ) { localProfile, account, userData ->
                    Triple(localProfile, account, userData)
                }.collect { (localProfile, account, userData) ->
                    _uiState.value = _uiState.value.copy(
                        localProfile = localProfile,
                        account = account,
                        userData = userData,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Napier.e("Failed to load profile data", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load profile: ${e.message}"
                )
            }
        }
    }

    /**
     * Start editing the display name using local profile data.
     */
    fun startEditingDisplayName() {
        val currentDisplayName = _uiState.value.localProfile.displayName
        _uiState.value = _uiState.value.copy(
            editState = ProfileEditState.DisplayName(currentDisplayName),
            updateState = ProfileUpdateState.Idle
        )
    }


    /**
     * Cancel current editing operation.
     */
    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(
            editState = ProfileEditState.None,
            updateState = ProfileUpdateState.Idle
        )
    }

    /**
     * Save display name changes to local profile (always works) and sync to cloud if connected.
     */
    fun saveDisplayName(newDisplayName: String) {
        if (newDisplayName.isBlank()) {
            _uiState.value = _uiState.value.copy(
                updateState = ProfileUpdateState.Error("Display name cannot be empty")
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(updateState = ProfileUpdateState.Updating)
                
                // First, update the local profile (always available)
                val localResult = profileRepository.updateDisplayName(newDisplayName)
                
                if (localResult.isFailure) {
                    val exception = localResult.exceptionOrNull()
                    Napier.e("Failed to update local profile", exception)
                    _uiState.value = _uiState.value.copy(
                        updateState = ProfileUpdateState.Error("Failed to save locally: ${exception?.message}")
                    )
                    return@launch
                }
                
                // If we have a cloud account, try to sync there too
                if (_uiState.value.hasCloudAccount) {
                    val cloudResult = updateProfileUseCase(displayName = newDisplayName)
                    
                    when (cloudResult) {
                        is UpdateProfileUseCase.Result.Error -> {
                            // Local save succeeded, but cloud sync failed - still show success but log the sync error
                            Napier.w("Local profile updated but cloud sync failed: ${cloudResult.error}")
                        }
                        is UpdateProfileUseCase.Result.Success -> {
                            Napier.d("Display name updated both locally and on cloud")
                        }
                    }
                }
                
                // Success - local update worked (cloud sync is best-effort)
                _uiState.value = _uiState.value.copy(
                    editState = ProfileEditState.None,
                    updateState = ProfileUpdateState.Success
                )
                
                // Clear success state after a brief moment
                kotlinx.coroutines.delay(2000)
                if (_uiState.value.updateState is ProfileUpdateState.Success) {
                    _uiState.value = _uiState.value.copy(updateState = ProfileUpdateState.Idle)
                }
                
            } catch (e: Exception) {
                Napier.e("Unexpected error saving display name", e)
                _uiState.value = _uiState.value.copy(
                    updateState = ProfileUpdateState.Error("Unexpected error: ${e.message}")
                )
            }
        }
    }


    /**
     * Refresh profile data from the server.
     */
    fun refreshProfile() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                val result = accountRepository.refreshAccount()
                if (result.isFailure) {
                    val exception = result.exceptionOrNull()
                    Napier.e("Failed to refresh profile", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to refresh profile: ${exception?.message}"
                    )
                } else {
                    // Data will be updated through the flow collection
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                Napier.e("Unexpected error refreshing profile", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Unexpected error: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear error messages.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Clear update state.
     */
    fun clearUpdateState() {
        _uiState.value = _uiState.value.copy(updateState = ProfileUpdateState.Idle)
    }

    /**
     * Get the current profile display model for UI rendering using local-first approach.
     */
    val profileDisplayModel: ProfileDisplayModel
        get() = createProfileDisplayModel(
            localProfile = _uiState.value.localProfile,
            account = _uiState.value.account,
            userData = _uiState.value.userData
        )
}