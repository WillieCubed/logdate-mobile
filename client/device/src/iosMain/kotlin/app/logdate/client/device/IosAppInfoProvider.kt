package app.logdate.client.device

import io.github.aakira.napier.Napier
import platform.Foundation.NSBundle

/**
 * iOS implementation of AppInfoProvider that retrieves information from the main bundle.
 */
class IosAppInfoProvider : AppInfoProvider {
    
    override fun getAppInfo(): AppInfo {
        try {
            val bundle = NSBundle.mainBundle
            
            val versionName = bundle.infoDictionary
                ?.get("CFBundleShortVersionString") as? String ?: "unknown"
                
            val buildNumberString = bundle.infoDictionary
                ?.get("CFBundleVersion") as? String ?: "0"
                
            val versionCode = buildNumberString.toIntOrNull() ?: 0
            
            val packageName = bundle.bundleIdentifier ?: "app.logdate.ios"
            
            return AppInfo(
                versionName = versionName,
                versionCode = versionCode,
                packageName = packageName
            )
        } catch (e: Exception) {
            Napier.e("Failed to retrieve app info from iOS bundle", e)
            throw AppInfoRetrievalException("Failed to retrieve app info from iOS bundle", e)
        }
    }
}