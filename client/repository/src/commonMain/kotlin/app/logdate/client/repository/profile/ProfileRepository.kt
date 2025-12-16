package app.logdate.client.repository.profile

import app.logdate.shared.model.profile.LogDateProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Repository for managing local LogDate Profile data.
 * 
 * This repository handles the local profile information that's always available
 * to users for app personalization, independent of cloud account connectivity.
 */
interface ProfileRepository {
    /**
     * Current local profile information as a Flow.
     */
    val currentProfile: Flow<LogDateProfile>
    
    /**
     * Update the user's display name locally.
     * 
     * @param displayName The new display name
     * @return Result indicating success or failure
     */
    suspend fun updateDisplayName(displayName: String): Result<LogDateProfile>
    
    /**
     * Update the user's birthday locally.
     * 
     * @param birthday The new birthday, or null to remove
     * @return Result indicating success or failure
     */
    suspend fun updateBirthday(birthday: Instant?): Result<LogDateProfile>
    
    /**
     * Update the user's profile photo URI locally.
     * 
     * @param profilePhotoUri The new profile photo URI, or null to remove
     * @return Result indicating success or failure
     */
    suspend fun updateProfilePhoto(profilePhotoUri: String?): Result<LogDateProfile>
    
    /**
     * Update the user's bio locally.
     * 
     * @param bio The new bio text, or null to remove
     * @param originalBio The original bio text before any LLM processing, or null to remove
     * @return Result indicating success or failure
     */
    suspend fun updateBio(bio: String?, originalBio: String? = null): Result<LogDateProfile>
    
    /**
     * Get the current profile synchronously (useful for initial state).
     * 
     * @return The current LogDateProfile or a default profile if none exists
     */
    suspend fun getCurrentProfile(): LogDateProfile
    
    /**
     * Clear all local profile data (used for account switching or reset).
     */
    suspend fun clearProfile(): Result<Unit>
}