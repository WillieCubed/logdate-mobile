package app.logdate.client.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * A monitor for network availability.
 *
 * This should be used to determine whether the device is connected to the internet.
 */
class AndroidNetworkAvailabilityMonitor(
    context: Context,
    private val applicationScope: CoroutineScope,
) : NetworkAvailabilityMonitor {
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

    override fun isNetworkAvailable(): Boolean {
        // TODO: Figure out whether this is the right API to be using
        return connectivityManager?.activeNetwork != null
    }

    /**
     * Subscribes an observer to the network state.
     *
     * @return A [SharedFlow] that is updated whenever
     */
    override fun observeNetwork(): SharedFlow<NetworkState> {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        if (connectivityManager == null) {
            Napier.w(tag = "NetworkAvailabilityMonitor", message = "ConnectivityManager is null")
            return networkState
        }
        connectivityManager.registerNetworkCallback(networkRequest, connectivityCallback)
        return networkState
    }
}

