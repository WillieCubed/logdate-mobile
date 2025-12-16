package app.logdate.client.device

/**
 * Android implementation of BuildConfigAppInfoProvider.
 */
actual class BuildConfigAppInfoProvider : AppInfoProvider {
    
    actual override fun getAppInfo(): AppInfo {
        return AppInfo(
            versionName = "1.0.0", // Hardcoded for now to fix build
            versionCode = 1,
            packageName = "app.logdate"
        )
    }
}