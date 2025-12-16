package app.logdate.client.sync.cloud.account

/**
 * Provider for platform-specific device information.
 *
 * This interface abstracts the platform-specific logic for retrieving
 * device information that is used during account creation and authentication.
 */
interface PlatformInfoProvider {
    /**
     * Gets platform-specific information about the current device.
     *
     * @return PlatformInfo containing device details.
     */
    fun getPlatformInfo(): PlatformInfo
}

/**
 * Platform-specific information about the device.
 *
 * @property platform The platform name (e.g., "Android", "iOS", "Desktop").
 * @property deviceName The name of the device (may be null on some platforms).
 * @property deviceType The type of device (e.g., "phone", "tablet", "desktop").
 * @property osVersion The operating system version.
 * @property appVersion The application version.
 */
data class PlatformInfo(
    val platform: String,
    val deviceName: String?,
    val deviceType: String,
    val osVersion: String,
    val appVersion: String
)