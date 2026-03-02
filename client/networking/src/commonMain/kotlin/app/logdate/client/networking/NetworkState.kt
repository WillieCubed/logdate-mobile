package app.logdate.client.networking

import kotlin.time.Instant

sealed interface NetworkState {
    data class Connected(
        val lastConnected: Instant,
    ) : NetworkState

    data class NotConnected(
        val lastConnected: Instant,
    ) : NetworkState
}
