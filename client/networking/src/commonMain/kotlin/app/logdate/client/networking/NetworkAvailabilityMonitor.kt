package app.logdate.client.networking

import kotlinx.coroutines.flow.SharedFlow

interface NetworkAvailabilityMonitor {
    fun isNetworkAvailable(): Boolean

    /**
     * Subscribes an observer to the network state.
     *
     * @return A [SharedFlow] that is updated whenever
     */
    fun observeNetwork(): SharedFlow<NetworkState>
}