package app.logdate.client.networking

import kotlinx.datetime.Instant

sealed interface NetworkState {
    data class Connected(
        val lastConnected: Instant,
    ) : NetworkState

    data class NotConnected(
        val lastConnected: Instant,
    ) : NetworkState
}