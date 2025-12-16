package app.logdate.client.sync.cloud.account

import app.logdate.client.device.BuildConfigAppInfoProvider
import java.net.InetAddress

/**
 * Desktop-specific implementation of [PlatformInfoProvider].
 *
 * Retrieves device information specific to desktop platforms.
 *
 * @param appInfoProvider Provider for application information.
 */
class DesktopPlatformInfoProvider(
    private val appInfoProvider: BuildConfigAppInfoProvider
) : PlatformInfoProvider {
    
    /**
     * Gets platform-specific information about the current desktop device.
     *
     * @return PlatformInfo containing desktop device details.
     */
    override fun getPlatformInfo(): PlatformInfo {
        return PlatformInfo(
            platform = getOsPlatformName(),
            deviceName = getComputerName(),
            deviceType = "desktop",
            osVersion = getOsVersion(),
            appVersion = getAppVersion()
        )
    }
    
    /**
     * Gets the operating system platform name.
     *
     * @return The platform name (e.g., "Windows", "macOS", "Linux").
     */
    private fun getOsPlatformName(): String {
        val osName = System.getProperty("os.name", "Unknown")
        return when {
            osName.startsWith("Windows", ignoreCase = true) -> "Windows"
            osName.startsWith("Mac", ignoreCase = true) -> "macOS"
            osName.startsWith("Linux", ignoreCase = true) -> "Linux"
            else -> osName
        }
    }
    
    /**
     * Gets the computer hostname.
     *
     * This attempts to get the local hostname but falls back to a generic name
     * if the hostname cannot be determined.
     *
     * @return The computer hostname or a generic name.
     */
    private fun getComputerName(): String {
        return try {
            val hostname = InetAddress.getLocalHost().hostName
            // Sanitize hostname to remove potentially sensitive information
            // In a real implementation, you might want to implement more sophisticated sanitization
            if (hostname.length > 20) {
                "Computer"
            } else {
                hostname
            }
        } catch (e: Exception) {
            "Computer"
        }
    }
    
    /**
     * Gets the operating system version.
     *
     * @return The operating system version string.
     */
    private fun getOsVersion(): String {
        return System.getProperty("os.version", "Unknown")
    }
    
    /**
     * Gets the application version.
     *
     * @return The application version string.
     */
    private fun getAppVersion(): String {
        val appInfo = appInfoProvider.getAppInfo()
        return appInfo.versionName
    }
}