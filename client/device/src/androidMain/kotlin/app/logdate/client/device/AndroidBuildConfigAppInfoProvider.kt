package app.logdate.client.device

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.aakira.napier.Napier

/**
 * Android implementation of AppInfoProvider using PackageManager.
 * This provides accurate version information from the application context.
 */
class AndroidBuildConfigAppInfoProvider(
    private val context: Context
) : AppInfoProvider {
    
    override fun getAppInfo(): AppInfo {
        try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            
            return AppInfo(
                versionName = packageInfo.versionName ?: "unknown",
                versionCode = getVersionCode(packageInfo),
                packageName = packageName
            )
        } catch (e: Exception) {
            Napier.e("Failed to retrieve app info", e)
            throw AppInfoRetrievalException("Failed to retrieve app info from PackageManager", e)
        }
    }
    
    private fun getVersionCode(packageInfo: PackageInfo): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }
}