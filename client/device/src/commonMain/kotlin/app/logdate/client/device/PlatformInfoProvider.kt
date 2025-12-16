package app.logdate.client.device

import app.logdate.client.device.models.DeviceInfo

/**
 * An interface for providing platform-specific information about the device.
 *
 * This can include details such as device model, OS version, unique identifiers,
 * etc. It is expected to be implemented by platform-specific source sets (e.g.,
 * Android, iOS, Desktop).
 */
interface PlatformInfoProvider {
    // TODO: implement, separate from device manager
    /**
     * Retrieves platform-specific information about the device.
     *
     * @return A DeviceInfo object containing details about the device.
     */
    fun getDeviceInfo(): DeviceInfo
}