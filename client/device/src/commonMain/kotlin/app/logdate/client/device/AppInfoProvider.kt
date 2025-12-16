package app.logdate.client.device

/**
 * Data class containing application information.
 */
data class AppInfo(
    val versionName: String,
    val versionCode: Int,
    val packageName: String
)

/**
 * Exception thrown when app info cannot be retrieved.
 */
class AppInfoRetrievalException(message: String, cause: Throwable? = null) : 
    RuntimeException(message, cause)

/**
 * Interface for providing application information.
 * Platform-specific implementations must provide correct values or throw an exception.
 */
interface AppInfoProvider {
    /**
     * Gets the application information.
     * 
     * @return AppInfo with valid values
     * @throws AppInfoRetrievalException if app info cannot be retrieved
     */
    fun getAppInfo(): AppInfo
}

/**
 * Implementation of AppInfoProvider that uses default values.
 * Each platform should override this with appropriate implementations.
 */
expect class BuildConfigAppInfoProvider() : AppInfoProvider {
    override fun getAppInfo(): AppInfo
}