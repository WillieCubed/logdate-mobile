package app.logdate.client.repository.user

import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing user data and app-level user preferences.
 * 
 * This handles user metadata like onboarding status, security preferences,
 * and other user-specific application data.
 */
interface UserDataRepository {
    /**
     * Current user data as a Flow.
     */
    val userData: Flow<UserData>
    
    /**
     * Update user data.
     * 
     * @param userData The updated user data
     * @return Result indicating success or failure
     */
    suspend fun updateUserData(userData: UserData): Result<UserData>
    
    /**
     * Mark user as onboarded.
     * 
     * @return Result indicating success or failure
     */
    suspend fun markUserAsOnboarded(): Result<UserData>
    
    /**
     * Get the current user data synchronously.
     * 
     * @return The current UserData or default values if none exists
     */
    suspend fun getCurrentUserData(): UserData
    
    /**
     * Clear all user data (used for account switching or reset).
     */
    suspend fun clearUserData(): Result<Unit>
}