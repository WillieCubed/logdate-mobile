package app.logdate.client.networking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.NetworkInterface
import kotlin.time.Clock

/**
 * A [NetworkAvailabilityMonitor] that functions on desktop (JVM) platforms.
 */
class DesktopNetworkAvailabilityMonitor(
    private val availabilityProbe: () -> Boolean = ::defaultAvailabilityProbe,
) : NetworkAvailabilityMonitor {
    private val networkStateFlow =
        MutableStateFlow<NetworkState>(
            if (availabilityProbe()) {
                NetworkState.Connected(Clock.System.now())
            } else {
                NetworkState.NotConnected(Clock.System.now())
            },
        )

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
