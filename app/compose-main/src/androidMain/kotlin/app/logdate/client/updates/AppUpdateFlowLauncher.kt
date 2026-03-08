package app.logdate.client.updates

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions

/**
 * Small launcher abstraction so the Play controller can be tested without a real
 * Activity Result API instance.
 */
fun interface AppUpdateFlowLauncher {
    /**
     * Asks Google Play to start the chosen in-app update flow.
     *
     * Returns `true` when Play accepted the launch request.
     */
    fun launch(
        appUpdateManager: AppUpdateManager,
        appUpdateInfo: AppUpdateInfo,
        options: AppUpdateOptions,
    ): Boolean
}

/**
 * Production launcher backed by AndroidX's Activity Result API.
 */
class ActivityResultAppUpdateFlowLauncher(
    private val launcher: ActivityResultLauncher<IntentSenderRequest>,
) : AppUpdateFlowLauncher {
    /** Delegates the Play flow launch to the AndroidX Activity Result API. */
    override fun launch(
        appUpdateManager: AppUpdateManager,
        appUpdateInfo: AppUpdateInfo,
        options: AppUpdateOptions,
    ): Boolean = appUpdateManager.startUpdateFlowForResult(appUpdateInfo, launcher, options)
}
