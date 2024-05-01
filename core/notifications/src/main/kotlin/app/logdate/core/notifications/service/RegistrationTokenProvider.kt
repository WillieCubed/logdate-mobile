package app.logdate.core.notifications.service

/**
 * A provider for device registration tokens.
 */
interface RegistrationTokenProvider {
    /**
     * Retrieves a device registration token for Firebase Cloud Messaging.
     *
     * @return The current device registration token.
     */
    suspend fun getToken(): String
}