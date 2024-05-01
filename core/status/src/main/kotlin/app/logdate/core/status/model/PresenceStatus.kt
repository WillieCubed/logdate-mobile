package app.logdate.core.status.model

/**
 * Information about a user's current activity.
 */
data class PresenceStatus(
    /**
     * The user's ID.
     */
    val userId: String,
    /**
     * Whether the user is currently online.
     */
    val isOnline: Boolean,
)