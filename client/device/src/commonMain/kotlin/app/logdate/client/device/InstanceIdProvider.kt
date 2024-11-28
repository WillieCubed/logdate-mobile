package app.logdate.client.device

import kotlinx.coroutines.flow.SharedFlow

/**
 * A service that provides an ID that identifiers the current device.
 *
 * This service is used to identify the current device in the context of a user account. The
 * identifier is unique to an installation of the app on a device and is used to identify the device
 * across multiple sessions.
 */
interface InstanceIdProvider {

    /**
     * The current device instance ID.
     */
    val currentInstanceId: SharedFlow<String>

    /**
     * Resets and regenerates the current device instance ID.
     *
     * The new instance ID can be observed using [currentInstanceId].
     */
    fun resetInstanceId()
}