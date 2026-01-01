package app.logdate.client.sync.cloud.account

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import app.logdate.client.device.BuildConfigAppInfoProvider

/**
 * Android-specific implementation of [PlatformInfoProvider].
 *
 * Retrieves device information specific to Android platforms.
 *
 * @param context The Android application context.
 * @param appInfoProvider Provider for application information.
 */
class AndroidPlatformInfoProvider(
    private val context: Context,
    private val appInfoProvider: BuildConfigAppInfoProvider
) : PlatformInfoProvider {
    
    /**
     * Gets platform-specific information about the current Android device.
     *
     * @return PlatformInfo containing Android device details.
     */
    override fun getPlatformInfo(): PlatformInfo {
        return PlatformInfo(
            platform = "Android",
            deviceName = getDeviceName(),
            deviceType = getDeviceType(),
            osVersion = Build.VERSION.RELEASE,
            appVersion = getAppVersion()
        )
    }
    
    /**
     * Gets the device name, or a generic name if unavailable.
     *
     * @return The device name or model.
     */
    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.capitalize()
        } else {
            "${manufacturer.capitalize()} $model"
        }
    }
    
    /**
     * Gets the device type based on screen size and other characteristics.
     *
     * @return "tablet" or "phone" depending on device characteristics.
     */
    private fun getDeviceType(): String {
        // This is a simplified implementation. In practice, you'd use screen size,
        // resources configuration, etc. to determine the device type more accurately.
        val isTablet = context.resources.configuration.screenWidthDp >= 600
        return if (isTablet) "tablet" else "phone"
    }
    
    /**
     * Gets the application version.
     *
     * @return The application version string.
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            "${packageInfo.versionName} ($versionCode)"
        } catch (e: PackageManager.NameNotFoundException) {
            val appInfo = appInfoProvider.getAppInfo()
            "${appInfo.versionName} (${appInfo.versionCode})"
        }
    }
    
    /**
     * Helper function to capitalize the first letter of a string.
     */
    private fun String.capitalize(): String {
        return if (isNotEmpty()) this[0].uppercase() + substring(1) else this
    }
}