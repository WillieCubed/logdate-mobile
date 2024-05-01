package app.logdate.core.installreferrer

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.logdate.core.installreferrer.model.InstallReferrerData
import app.logdate.core.installreferrer.model.ReferrerState
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.datetime.Instant
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/**
 * An implementation of [InstallReferrer] for fetching install referrer data from the Google Play Store.
 *
 * Consumers of this class should ensure that the Google Play Store is available on the device before
 * using this class.
 */
@ActivityScoped
class GooglePlayReferrer @Inject constructor(private val context: Context) : InstallReferrer {

    private val referrerClient = InstallReferrerClient.newBuilder(context).build()

    override suspend fun getReferrerState(): ReferrerState = suspendCoroutine {
        if (!context.isPlayStoreAvailable()) {
            it.resume(ReferrerState.UNAVAILABLE)
            return@suspendCoroutine
        }
        val listener = object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) = when (responseCode) {
                InstallReferrerClient.InstallReferrerResponse.OK -> it.resume(ReferrerState.CONNECTED)
                InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> it.resume(
                    ReferrerState.NOT_SUPPORTED
                )

                InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR -> {
                    Log.e("GooglePlayReferrer", "Developer error during setup")
                    it.resume(ReferrerState.UNAVAILABLE)
                }

                InstallReferrerClient.InstallReferrerResponse.SERVICE_DISCONNECTED -> {
                    Log.w("GooglePlayReferrer", "Service currently disconnected")
                    it.resume(ReferrerState.UNAVAILABLE)
                }

                else -> it.resume(ReferrerState.UNAVAILABLE)
            }

            override fun onInstallReferrerServiceDisconnected() {
                throw ReferrerServiceDisconnectedException()
            }
        }
        referrerClient.startConnection(listener)
    }

    override suspend fun getReferrerData(): InstallReferrerData {
        if (!referrerClient.isReady) {
            throw ReferrerServiceDisconnectedException()
        }
        return referrerClient.installReferrer.toInstallReferrerData()
    }
}

/**
 * Returns `true` if the Google Play Store is available on the device for this context.
 */
fun Context.isPlayStoreAvailable(): Boolean = try {
    packageManager.getPackageInfo("com.android.vending", 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

/**
 * Converts a Google Play [ReferrerDetails] object to an [InstallReferrerData] object.
 */
private fun ReferrerDetails.toInstallReferrerData() = InstallReferrerData(
    referralUrl = installReferrer,
    isFromInstantExperience = googlePlayInstantParam,
    referrerClickTimestamp = Instant.fromEpochSeconds(referrerClickTimestampSeconds),
    installationTimestamp = Instant.fromEpochSeconds(installBeginTimestampSeconds),
)
