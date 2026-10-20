package app.logdate.client.sensor.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.networking.saver.NetworkSaverState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of [NetworkSaverModeProvider] that monitors data saver mode
 * and network connection type using the Android ConnectivityManager.
 */
class AndroidNetworkSaverModeProvider(
    private val context: Context,
) : NetworkSaverModeProvider {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val stateLock = Any()
    private var observedDefaultNetwork = false
    private var defaultNetwork: Network? = null
    private var defaultCapabilities: NetworkCapabilities? = null
    private var backgroundRestricted = readBackgroundRestriction()
    private val networkSaverStateFlow = MutableStateFlow(readInitialState())

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(stateLock) {
                    observedDefaultNetwork = true
                    defaultNetwork = network
                    defaultCapabilities = null
                    publishCallbackState()
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                synchronized(stateLock) {
                    if (network != defaultNetwork) return
                    defaultCapabilities = capabilities
                    publishCallbackState()
                }
            }

            override fun onLost(network: Network) {
                synchronized(stateLock) {
                    if (network != defaultNetwork) return
                    defaultNetwork = null
                    defaultCapabilities = null
                    publishCallbackState()
                }
            }
        }

    private val dataSaverReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                refreshRestriction()
            }
        }

    init {
        // Register for network changes
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // In case of security exception or other issues
        }

        // Register for data saver mode changes
        try {
            val filter = IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
            context.registerReceiver(dataSaverReceiver, filter)
        } catch (e: Exception) {
            // In case of security exception or other issues
        }
    }

    override val dataSaverModeState: Flow<NetworkSaverState> = networkSaverStateFlow.asStateFlow()

    override suspend fun getCurrentDataSaverState(): NetworkSaverState = refreshRestriction()

    private fun refreshRestriction(): NetworkSaverState =
        synchronized(stateLock) {
            backgroundRestricted = readBackgroundRestriction()
            val state = if (observedDefaultNetwork) callbackState() else readInitialState()
            networkSaverStateFlow.value = state
            state
        }

    private fun publishCallbackState() {
        networkSaverStateFlow.value = callbackState()
    }

    // Callback arguments are ordered by Android. Synchronous getters in a callback may
    // still describe the previous network; later policy reads must not overwrite this state.
    private fun callbackState(): NetworkSaverState {
        val connection = defaultCapabilities.toConnectionType()
        return NetworkSaverState(
            isDataSaverEnabled = backgroundRestricted,
            connectionType = connection,
            isMetered =
                connection == NetworkConnectionType.BLUETOOTH_PROXY ||
                    defaultCapabilities?.let {
                        !runCatching { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) }.getOrDefault(false)
                    } == true,
            isValidated = defaultCapabilities.isValidated(),
        )
    }

    override suspend fun isDataSaverModeActive(): Boolean = getCurrentDataSaverState().isDataSaverEnabled

    override fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore if not registered
        }

        try {
            context.unregisterReceiver(dataSaverReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }

    private fun readBackgroundRestriction(): Boolean =
        runCatching {
            connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        }.getOrDefault(false)

    private fun readInitialState(): NetworkSaverState {
        val activeCapabilities = getActiveNetworkCapabilities()
        val connectionType = activeCapabilities.toConnectionType()

        return NetworkSaverState(
            isDataSaverEnabled = backgroundRestricted,
            connectionType = connectionType,
            isMetered =
                connectionType == NetworkConnectionType.BLUETOOTH_PROXY ||
                    runCatching { connectivityManager.isActiveNetworkMetered }.getOrDefault(false),
            isValidated = activeCapabilities.isValidated(),
        )
    }

    private fun getActiveNetworkCapabilities(): NetworkCapabilities? =
        try {
            val activeNetwork = connectivityManager.activeNetwork
            activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        } catch (e: Exception) {
            null
        }

    private fun NetworkCapabilities?.toConnectionType(): NetworkConnectionType {
        if (this == null) return NetworkConnectionType.NONE
        return try {
            when {
                hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkConnectionType.WIFI
                hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkConnectionType.CELLULAR
                hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkConnectionType.ETHERNET
                hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkConnectionType.BLUETOOTH_PROXY
                else -> NetworkConnectionType.OTHER
            }
        } catch (e: Exception) {
            NetworkConnectionType.OTHER
        }
    }

    /** Unknown counts as not validated, so a network that cannot be checked is never trusted for uploads. */
    private fun NetworkCapabilities?.isValidated(): Boolean {
        if (this == null) return false
        return try {
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            false
        }
    }
}
