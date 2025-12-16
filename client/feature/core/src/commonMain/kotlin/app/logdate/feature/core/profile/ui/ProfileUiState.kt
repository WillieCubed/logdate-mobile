package app.logdate.feature.core.profile.ui

import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.shared.model.user.UserData
import kotlinx.datetime.Instant

/**
 * UI state for the profile screen with local-first approach.
 */
data class ProfileUiState(
    val localProfile: LogDateProfile,
    val account: LogDateAccount?,
    val userData: UserData?,
    val isLoading: Boolean = false,
    val editState: ProfileEditState = ProfileEditState.None,
    val updateState: ProfileUpdateState = ProfileUpdateState.Idle,
    val errorMessage: String? = null
) {
    val isEditing: Boolean
        get() = editState != ProfileEditState.None
    
    val canSave: Boolean
        get() = isEditing && updateState != ProfileUpdateState.Updating
    
    val hasCloudAccount: Boolean
        get() = account != null
}

/**
 * Represents which field is currently being edited.
 */
sealed class ProfileEditState {
    data object None : ProfileEditState()
    data class DisplayName(val currentValue: String) : ProfileEditState()
}

/**
 * Represents the state of profile update operations.
 */
sealed class ProfileUpdateState {
    data object Idle : ProfileUpdateState()
    data object Updating : ProfileUpdateState()
    data object Success : ProfileUpdateState()
    data class Error(val message: String) : ProfileUpdateState()
}

/**
 * Profile display model for UI rendering with local-first approach.
 * 
 * This model combines local profile data (always available) with cloud account data
 * (when available) to provide progressive enhancement.
 */
data class ProfileDisplayModel(
    // Local profile data (always available)
    val displayName: String,
    val birthday: Instant?,
    val hasProfilePhoto: Boolean = false,
    val profileCreatedAt: Instant,
    
    // Cloud account data (progressive enhancement when connected)
    val username: String? = null,
    val joinDate: Instant? = null,
    val isAuthenticated: Boolean = false
) {
    val hasCloudAccount: Boolean
        get() = isAuthenticated
}

/**
 * Create ProfileDisplayModel from local profile and optional cloud account data.
 * 
 * This function implements the local-first approach where local profile data is always
 * available, and cloud account data provides progressive enhancement when connected.
 */
fun createProfileDisplayModel(
    localProfile: LogDateProfile,
    account: LogDateAccount? = null,
    userData: UserData? = null
): ProfileDisplayModel {
    return ProfileDisplayModel(
        // Local profile data (always available)
        displayName = localProfile.displayName.ifEmpty { "Your name" },
        birthday = localProfile.birthday ?: userData?.birthday?.takeIf { it != Instant.DISTANT_PAST },
        hasProfilePhoto = !localProfile.profilePhotoUri.isNullOrEmpty(),
        profileCreatedAt = localProfile.createdAt,
        
        // Cloud account data (progressive enhancement)
        username = account?.username?.takeIf { it.isNotEmpty() },
        joinDate = account?.createdAt,
        isAuthenticated = account?.passkeyCredentialIds?.isNotEmpty() == true
    )
}