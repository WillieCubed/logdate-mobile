package app.logdate.client.repository.user.devices

import kotlinx.coroutines.flow.Flow

/**
 * A repository for all devices belonging to the current user.
 */
interface UserDeviceRepository {
    /**
     * The devices that the user has used to access the app.
     */
    val allDevices: Flow<List<UserDevice>>

    val currentDevice: Flow<UserDevice>

    /**
     * Associate a device with the current user.
     */
    suspend fun addDevice(
        label: String,
        operatingSystem: String,
        version: String,
        model: String,
        type: DeviceType,
    )

    /**
     * Remove the device with the given UID.
     *
     * This disassociates the device with the current user.
     */
    suspend fun removeDevice(deviceId: String)

    /**
     * Update the device with the given UID.
     *
     * This will overwrite the device with the given UID with the current device data.
     *
     * @param deviceId The UID of the device to update.
     */
    suspend fun updateDevice(deviceId: String)
}