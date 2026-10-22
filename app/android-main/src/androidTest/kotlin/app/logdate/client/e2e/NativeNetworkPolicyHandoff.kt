package app.logdate.client.e2e

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

/** Observes the owned Wi-Fi fixture before policy assertions or worker baselines are established. */
internal suspend fun awaitValidatedNativeWifi(context: Context) {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    try {
        withTimeout(30_000) {
            while (true) {
                val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                ) {
                    break
                }
                delay(100)
            }
        }
    } catch (failure: Exception) {
        reportNativeNetworkFailure(context, failure, JSONObject().put("stage", "wifi-preflight"))
        throw failure
    }
}

/** Keeps host custody/ADB latency outside the app's unchanged network callback deadline. */
internal suspend fun awaitUnrestrictedNativeNetwork(
    context: Context,
    policy: DataUsagePolicy,
    callbackTimeoutMillis: Long = 45_000,
) {
    val nonce = InstrumentationRegistry.getArguments().getString("restoreNetworkHandoff")
    require(nonce != null && nonce.matches(Regex("[a-f0-9]{32}")))
    val acknowledgment = File(context.noBackupFilesDir, "restore-network-handoff-$nonce.txt")
    check(!acknowledgment.exists()) { "Network handoff must belong to this instrumentation run" }
    val started = SystemClock.elapsedRealtime()
    var callbackStarted: Long? = null
    var observed = "not collected"
    try {
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("restoreNetwork", "RESTORE_NETWORK_DEFERRED") },
        )
        withTimeout(90_000) {
            while (!acknowledgment.exists()) delay(50)
            check(acknowledgment.readText() == "applied") { "Network handoff was not verified by the host" }
        }
        callbackStarted = SystemClock.elapsedRealtime()
        withTimeout(callbackTimeoutMillis) {
            policy.policy.first {
                observed = it.toString()
                it == DataUsageMode.Unrestricted
            }
        }
    } catch (failure: Exception) {
        val snapshot =
            JSONObject()
                .put("handoffApplied", callbackStarted != null)
                .put("elapsedMillis", SystemClock.elapsedRealtime() - started)
                .put("callbackElapsedMillis", callbackStarted?.let { SystemClock.elapsedRealtime() - it })
                .put("observedPolicy", observed)
        reportNativeNetworkFailure(context, failure, snapshot)
        throw failure
    } finally {
        acknowledgment.delete()
    }
}

private fun reportNativeNetworkFailure(
    context: Context,
    failure: Exception,
    snapshot: JSONObject,
) {
    runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        snapshot
            .put("hasActiveNetwork", connectivity.activeNetwork != null)
            .put("wifi", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .put("cellular", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .put("notMetered", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            .put("validated", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            .put("activeMetered", connectivity.isActiveNetworkMetered)
            .put("restrictBackgroundStatus", connectivity.restrictBackgroundStatus)
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("restoreNetworkFailure", snapshot.toString()) },
        )
    }.exceptionOrNull()?.let(failure::addSuppressed)
}
