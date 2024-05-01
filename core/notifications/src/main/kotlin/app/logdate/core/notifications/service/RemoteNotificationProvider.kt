package app.logdate.core.notifications.service

/**
 * A service that manages a client's connection to a remote notification provider.
 */
interface RemoteNotificationProvider {

    /**
     * Subscribes to a remote notification topic.
     */
    suspend fun subscribe(topic: String)

    /**
     * Unsubscribes from a remote notification topic.
     */
    suspend fun unsubscribe(topic: String)

    /**
     * Toggles automatic initialization for a remote notification provider.
     *
     * @param enabled Whether to enable or disable automatic initialization.
     */
    fun toggleAutoInit(enabled: Boolean = true)
}