package app.logdate.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.core.content.getSystemService
import app.logdate.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * A monitor for network availability.
 *
 * This should be used to determine whether the device is connected to the internet.
 */
class NetworkAvailabilityMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val networkState = MutableSharedFlow<NetworkState>()
    private val connectivityManager = context.getSystemService() as ConnectivityManager?
    private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            applicationScope.launch {
                val newState = NetworkState.Connected(Clock.System.now())
                networkState.emit(newState)
            }
        }

        override fun onUnavailable() {
            applicationScope.launch {
                val newState = NetworkState.NotConnected(Clock.System.now())
                networkState.emit(newState)
            }
        }
    }

    fun isNetworkAvailable(): Boolean {
        // TODO: Figure out whether this is the right API to be using
        return connectivityManager?.activeNetwork != null
    }

    /**
     * Subscribes an observer to the network state.
     *
     * @return A [SharedFlow] that is updated whenever
     */
    fun observeNetwork(): SharedFlow<NetworkState> {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        if (connectivityManager == null) {
            Log.w("NetworkAvailabilityMonitor", "ConnectivityManager is null")
            return networkState
        }
        connectivityManager.registerNetworkCallback(networkRequest, connectivityCallback)
        return networkState
    }
}

sealed interface NetworkState {
    data class Connected(
        val lastConnected: Instant,
    ) : NetworkState

    data class NotConnected(
        val lastConnected: Instant,
    ) : NetworkState
}