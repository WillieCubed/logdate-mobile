package app.logdate.feature.core.account.ui

import io.github.aakira.napier.Napier

/**
 * Utility functions and data classes for the account setup flow.
 */
object AccountUtils {
    /**
     * Validates username format.
     * @return true if the username matches the required format
     */
    fun isValidUsername(username: String): Boolean {
        if (username.isBlank()) {
            return false
        }
        
        return username.matches(Regex("^[a-zA-Z0-9_]+$"))
    }

    /**
     * Returns validation error message for a username or null if valid.
     */
    fun getValidationErrorForUsername(username: String): String? {
        return when {
            username.isBlank() -> "Username cannot be empty"
            !isValidUsername(username) -> "Username can only contain letters, numbers, and underscores"
            else -> null
        }
    }
    
    /**
     * Validates display name format.
     * @return true if the display name is valid
     */
    fun isValidDisplayName(displayName: String): Boolean {
        return displayName.isNotBlank()
    }

    /**
     * Returns validation error message for a display name or null if valid.
     */
    fun getValidationErrorForDisplayName(displayName: String): String? {
        return when {
            displayName.isBlank() -> "Display name cannot be empty"
            else -> null
        }
    }
    
    /**
     * Helper for consistent error logging across ViewModels.
     */
    fun logError(message: String, throwable: Throwable) {
        Napier.e(message, throwable)
    }
}

// State no longer needed as an external shared object

/**
 * Username availability status
 */
enum class UsernameAvailability {
    UNKNOWN,
    CHECKING,
    AVAILABLE,
    TAKEN,
    ERROR
}