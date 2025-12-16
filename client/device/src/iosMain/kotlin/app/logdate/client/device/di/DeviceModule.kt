package app.logdate.client.device.di

import app.logdate.client.device.AppInfo
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.BuildConfigAppInfoProvider
import app.logdate.client.device.identity.di.deviceIdentityModule
import org.koin.core.module.Module
import org.koin.dsl.module

// Removed the 'actual' keyword since this is implementing the common module, not the expect/actual pair
// The common module already has a non-expect 'deviceModule' variable

// iOS app info provider
class IosAppInfoProvider : AppInfoProvider {
    override fun getAppInfo(): AppInfo {
        val bundle = platform.Foundation.NSBundle.mainBundle
        val versionName = bundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "1.0.0"
        val versionCodeString = bundle.infoDictionary?.get("CFBundleVersion") as? String ?: "1"
        val versionCode = versionCodeString.toIntOrNull() ?: 1
        val packageName = bundle.bundleIdentifier ?: "app.logdate"
        
        return AppInfo(
            versionName = versionName,
            versionCode = versionCode,
            packageName = packageName
        )
    }
}

// We're not going to use this as we'll use the common deviceModule
private val iosDeviceModule: Module = module {
    // Include device identity components
    includes(deviceIdentityModule)
    
    // Include device instance module
    includes(deviceInstanceModule)
    
    // Provide the iOS-specific app info provider
    single<AppInfoProvider> { IosAppInfoProvider() }
}