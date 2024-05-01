package app.logdate.core.updater

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import app.logdate.core.coroutines.AppDispatcher
import app.logdate.core.coroutines.Dispatcher
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * An entrypoint for providing dependencies for the app updater.
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
interface AppUpdaterEntryPoint {
    fun getUpdaterLauncher(): ActivityResultLauncher<IntentSenderRequest>
}

internal fun getUpdaterLauncher(@ApplicationContext context: Context) =
    EntryPointAccessors.fromApplication(context, AppUpdaterEntryPoint::class.java)
        .getUpdaterLauncher()

/**
 * An implementation of [AppUpdater] for updating the app using the Google Play app store.
 */
class GooglePlayAppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : AppUpdater {

    private val _updateStatus = MutableStateFlow(AppUpdateStatus.UNKNOWN)
    private val appUpdateManager = AppUpdateManagerFactory.create(context)

    override val updateIsAvailable: Flow<Boolean>
        get() = _updateStatus.map { it == AppUpdateStatus.AVAILABLE }

    override suspend fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            _updateStatus.value = when (appUpdateInfo.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE -> AppUpdateStatus.AVAILABLE
                UpdateAvailability.UPDATE_NOT_AVAILABLE -> AppUpdateStatus.UNAVAILABLE
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> AppUpdateStatus.IN_PROGRESS
                UpdateAvailability.UNKNOWN -> AppUpdateStatus.UNKNOWN
                else -> {
                    AppUpdateStatus.UNKNOWN
                }
            }

            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                // This example applies an immediate update. To apply a flexible update
                // instead, pass in AppUpdateType.FLEXIBLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                // Request the update.
            }
        }
    }

    override suspend fun startUpdate() {
        when (_updateStatus.value) {
            AppUpdateStatus.AVAILABLE -> {
                appUpdateManager.appUpdateInfo.addOnSuccessListener {
                    val activityResultLauncher = getUpdaterLauncher(context)
                    appUpdateManager.startUpdateFlowForResult(
                        it,
                        activityResultLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    )
                }
            }

            else -> {
                // Do nothing
            }
        }
    }
}