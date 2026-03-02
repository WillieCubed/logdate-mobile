package app.logdate.client.networking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Clock

/**
 * Basic network availability monitor for iOS until a native observer is wired.
 */
class IosNetworkAvailabilityMonitor : NetworkAvailabilityMonitor {
    private val networkStateFlow =
        MutableStateFlow<NetworkState>(
            NetworkState.Connected(Clock.System.now()),
        )

    override fun isNetworkAvailable(): Boolean = true

    override fun observeNetwork(): SharedFlow<NetworkState> = networkStateFlow
}
