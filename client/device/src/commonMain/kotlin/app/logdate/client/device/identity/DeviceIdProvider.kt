package app.logdate.client.device.identity

import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides a unique identifier for the current device.
 * This identifier persists across app restarts and updates.
 */
interface DeviceIdProvider {
    /**
     * Provides a StateFlow of the current device identifier.
     * Always contains the current value and emits updates if the ID changes.
     *
     * @return StateFlow containing the device identifier as a Uuid
     */
    fun getDeviceId(): StateFlow<Uuid>
    
    /**
     * Generates a new device identifier, replacing any existing one.
     * This should be used in cases where device identity needs to be reset
     * or when the existing ID is invalid.
     * 
     * This triggers an update in the device ID StateFlow.
     */
    suspend fun refreshDeviceId()
}