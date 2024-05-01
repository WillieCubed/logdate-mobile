package app.logdate.core.sync.datasaver

import android.net.ConnectivityManager
import android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
import android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED
import javax.inject.Inject

class DataUsageState @Inject constructor(
    private val connectivityManager: ConnectivityManager,
) {
    val currentDataUsageSettings: DataUsageSettings
        get() = if (!connectivityManager.isActiveNetworkMetered) {
            DataUsageSettings.Disabled
        } else if (connectivityManager.restrictBackgroundStatus == RESTRICT_BACKGROUND_STATUS_DISABLED) {
            DataUsageSettings.Disabled
        } else {
            DataUsageSettings.Enabled(isWhitelisted = connectivityManager.restrictBackgroundStatus == RESTRICT_BACKGROUND_STATUS_WHITELISTED)
        }

    val shouldUseBackgroundServices: Boolean
        get() = currentDataUsageSettings !is DataUsageSettings.Enabled
}

/**
 * A representation of how the app should behave based on the current Data Saver status.
 */
sealed interface DataUsageSettings {
    /**
     * Data Saver is enabled system-wide.
     *
     * In this state, the the app should make an effort to limit data usage in the foreground and
     * gracefully handle restrictions to background data usage.
     */
    data class Enabled(
        /**
         * Whether the use has whitelisted the app from Data Saver restrictions.
         *
         * Even if Data Saver is disabled, the app should use less data wherever possible.
         */
        val isWhitelisted: Boolean = false,
    ) : DataUsageSettings

    /**
     * Data Saver is disabled system-wide.
     *
     * In this state, the app can use data in the foreground and background without restrictions.
     */
    data object Disabled : DataUsageSettings
}
