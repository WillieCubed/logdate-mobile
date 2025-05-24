package app.logdate.client.networking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.datetime.Clock

/**
 * A [NetworkAvailabilityMonitor] that functions on desktop (JVM) platforms.
 */
class DesktopNetworkAvailabilityMonitor : NetworkAvailabilityMonitor {
    private val _networkState =
        MutableStateFlow<NetworkState>(NetworkState.NotConnected(Clock.System.now()))

    // TODO: Implement this
    override fun isNetworkAvailable(): Boolean {
        return true
    }

    override fun observeNetwork(): SharedFlow<NetworkState> {
        return _networkState
    }
}