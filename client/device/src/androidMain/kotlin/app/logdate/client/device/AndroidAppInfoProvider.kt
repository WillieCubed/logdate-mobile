package app.logdate.client.device

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import io.github.aakira.napier.Napier

/**
 * Android implementation of AppInfoProvider that retrieves information from the application context.
 * This implementation uses the actual package info as the source of truth.
 */
class AndroidAppInfoProvider(
    private val context: Context
) : AppInfoProvider {

    override fun getAppInfo(): AppInfo {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

            return AppInfo(
                versionName = packageInfo.versionName ?: "unknown",
                versionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toInt(),
                packageName = context.packageName
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Napier.e("Failed to retrieve package info", e)
            throw AppInfoRetrievalException("Failed to retrieve package info", e)
        } catch (e: Exception) {
            Napier.e("Failed to retrieve app info", e)
            throw AppInfoRetrievalException("Failed to retrieve app info", e)
        }
    }
}