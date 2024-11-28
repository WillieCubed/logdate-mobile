package app.logdate.client.networking

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * A [NetworkAvailabilityMonitor] that functions on desktop (JVM) platforms.
 */
class DesktopNetworkAvailabilityMonitor : NetworkAvailabilityMonitor {
    private val networkState = MutableSharedFlow<NetworkState>()

    // TODO: Implement this
    override fun isNetworkAvailable(): Boolean {
        return true
    }

    override fun observeNetwork(): SharedFlow<NetworkState> {
        return networkState
    }
}