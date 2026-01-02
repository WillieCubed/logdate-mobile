package app.logdate.client.domain.fakes

import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.datetime.Clock

/**
 * Simple configurable network availability monitor for tests.
 */
class FakeNetworkAvailabilityMonitor(
    private var available: Boolean = true
) : NetworkAvailabilityMonitor {
    private val networkStateFlow = MutableSharedFlow<NetworkState>(replay = 1).apply {
        tryEmit(
            if (available) {
                NetworkState.Connected(Clock.System.now())
            } else {
                NetworkState.NotConnected(Clock.System.now())
            }
        )
    }

    override fun isNetworkAvailable(): Boolean = available

    override fun observeNetwork(): SharedFlow<NetworkState> = networkStateFlow

    fun setAvailable(isAvailable: Boolean) {
        available = isAvailable
        networkStateFlow.tryEmit(
            if (available) {
                NetworkState.Connected(Clock.System.now())
            } else {
                NetworkState.NotConnected(Clock.System.now())
            }
        )
    }
}
