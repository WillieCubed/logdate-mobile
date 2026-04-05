package app.logdate.client.networking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import kotlin.time.Clock

/**
 * A [NetworkAvailabilityMonitor] that functions on desktop (JVM) platforms.
 *
 * The initial network state is set to [NetworkState.NotConnected] synchronously, then updated
 * asynchronously on [Dispatchers.IO] to avoid blocking the calling thread during construction.
 * On macOS, [NetworkInterface.getNetworkInterfaces] is a JNI call that can block for several
 * seconds (e.g. under VPNs or unusual network conditions), which would freeze the UI if called
 * on the main thread.
 */
class DesktopNetworkAvailabilityMonitor(
    private val coroutineScope: CoroutineScope,
    private val availabilityProbe: () -> Boolean = ::defaultAvailabilityProbe,
) : NetworkAvailabilityMonitor {
    private val networkStateFlow =
        MutableStateFlow<NetworkState>(NetworkState.NotConnected(Clock.System.now()))

    init {
        coroutineScope.launch(Dispatchers.IO) {
            networkStateFlow.value =
                if (availabilityProbe()) {
                    NetworkState.Connected(Clock.System.now())
                } else {
                    NetworkState.NotConnected(Clock.System.now())
                }
        }
    }

    override fun isNetworkAvailable(): Boolean {
        val available = availabilityProbe()
        networkStateFlow.value =
            if (available) {
                NetworkState.Connected(Clock.System.now())
            } else {
                NetworkState.NotConnected(Clock.System.now())
            }
        return available
    }

    override fun observeNetwork(): SharedFlow<NetworkState> = networkStateFlow

    companion object {
        private fun defaultAvailabilityProbe(): Boolean =
            runCatching {
                NetworkInterface.getNetworkInterfaces().toList().any { it.isUp && !it.isLoopback }
            }.getOrElse { false }
    }
}
