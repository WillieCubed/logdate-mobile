package app.logdate.client.sync.cloud.account

import app.logdate.client.device.BuildConfigAppInfoProvider
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice

/**
 * iOS-specific implementation of [PlatformInfoProvider].
 *
 * Retrieves device information specific to iOS platforms.
 *
 * @param appInfoProvider Provider for application information.
 */
class IosPlatformInfoProvider(
    private val appInfoProvider: BuildConfigAppInfoProvider
) : PlatformInfoProvider {
    
    /**
     * Gets platform-specific information about the current iOS device.
     *
     * @return PlatformInfo containing iOS device details.
     */
    override fun getPlatformInfo(): PlatformInfo {
        return PlatformInfo(
            platform = "iOS",
            deviceName = getDeviceName(),
            deviceType = getDeviceType(),
            osVersion = UIDevice.currentDevice.systemVersion,
            appVersion = getAppVersion()
        )
    }
    
    /**
     * Gets the device name.
     *
     * Note: For privacy reasons, iOS may restrict access to the actual device name.
     * This will return a generic name based on model in those cases.
     *
     * @return The device name or a generic name based on model.
     */
    private fun getDeviceName(): String {
        // UIDevice.currentDevice.name might return the user-assigned device name,
        // which could contain personal information. In a production app,
        // you might want to use a different approach or filter this.
        return UIDevice.currentDevice.model
    }
    
    /**
     * Gets the device type.
     *
     * @return "tablet" for iPads, "phone" for iPhones.
     */
    private fun getDeviceType(): String {
        return when (UIDevice.currentDevice.userInterfaceIdiom) {
            1L -> "phone" // UIUserInterfaceIdiomPhone
            2L -> "tablet" // UIUserInterfaceIdiomPad
            else -> "unknown"
        }
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