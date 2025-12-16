package app.logdate.client.domain.app

import app.logdate.client.device.AppInfoProvider

/**
 * Use case for retrieving application information.
 */
class GetAppInfoUseCase(
    private val appInfoProvider: AppInfoProvider
) {
    /**
     * Gets application information.
     */
    suspend operator fun invoke(): AppInfoResult {
        return try {
            val appInfo = appInfoProvider.getAppInfo()
            
            AppInfoResult.Success(
                versionName = appInfo.versionName,
                versionCode = appInfo.versionCode,
                packageName = appInfo.packageName
            )
        } catch (e: Exception) {
            AppInfoResult.Error(e.message ?: "Failed to retrieve app info")
        }
    }
    
    /**
     * Result types for app information requests.
     */
    sealed class AppInfoResult {
        /** Result containing app information. */
        data class Success(
            val versionName: String,
            val versionCode: Int,
            val packageName: String
        ) : AppInfoResult()
        
        /** Result when an error occurs retrieving app information. */
        data class Error(val message: String) : AppInfoResult()
    }
}