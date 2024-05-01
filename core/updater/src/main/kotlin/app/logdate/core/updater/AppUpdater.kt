package app.logdate.core.updater

import kotlinx.coroutines.flow.Flow

/**
 * Interface for handling LogDate subscriptions and in-app billing.
 */
interface AppUpdater {

    /**
     * A flow that emits `true` when an update is available, and `false` otherwise.
     */
    val updateIsAvailable: Flow<Boolean>

    /**
     * Checks for updates to the app.
     *
     * Implementations should check the device's app store for updates to the app and emit `true` to
     * [updateIsAvailable] if an update is available.
     */
    suspend fun checkForUpdates()

    /**
     * Starts the update process.
     *
     * Implementations should request the device's app store to start the update process for the app.
     * If no update is available, this method should do nothing.
     */
    suspend fun startUpdate()
}
