package app.logdate.client.sensor.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DefaultDataUsagePolicy
import app.logdate.client.networking.saver.NetworkConnectionType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNetworkPolicyCallbackTest {
    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<ConnectivityManager>(relaxed = true)
    private val network = mockk<Network>()
    private val callback = slot<ConnectivityManager.NetworkCallback>()
    private val receiver = slot<BroadcastReceiver>()

    private fun capabilities(
        metered: Boolean,
        transport: Int = NetworkCapabilities.TRANSPORT_WIFI,
    ): NetworkCapabilities =
        mockk<NetworkCapabilities> {
            every { hasTransport(any()) } returns false
            every { hasTransport(transport) } returns true
            every { hasCapability(any()) } returns false
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } returns !metered
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        }

    @AfterTest
    fun cleanupMocks() = unmockkAll()

    private fun provider(): AndroidNetworkSaverModeProvider {
        mockkConstructor(NetworkRequest.Builder::class)
        every { anyConstructed<NetworkRequest.Builder>().build() } returns mockk<NetworkRequest>()
        every { context.getSystemService(ConnectivityManager::class.java) } returns manager
        every { manager.activeNetwork } returns network
        every { manager.getNetworkCapabilities(network) } returns capabilities(metered = true)
        every { manager.isActiveNetworkMetered } returns true
        every { manager.restrictBackgroundStatus } returns ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
        every { manager.registerDefaultNetworkCallback(capture(callback)) } returns Unit
        every { manager.registerNetworkCallback(any<NetworkRequest>(), capture(callback)) } returns Unit
        every { context.registerReceiver(capture(receiver), any<IntentFilter>()) } returns null
        return AndroidNetworkSaverModeProvider(context)
    }

    @Test
    fun `unmetered callback wakes policy even while synchronous getters remain metered`() =
        runTest {
            val provider = provider()
            callback.captured.onAvailable(network)
            callback.captured.onCapabilitiesChanged(network, capabilities(metered = false))

            assertEquals(DataUsageMode.Unrestricted, DefaultDataUsagePolicy(provider).policy.first())
        }

    @Test
    fun `current reads cannot overwrite newer callback capabilities`() =
        runTest {
            val provider = provider()
            callback.captured.onAvailable(network)
            callback.captured.onCapabilitiesChanged(network, capabilities(metered = false))

            assertFalse(provider.getCurrentDataSaverState().isMetered)
            assertFalse(provider.dataSaverModeState.first().isMetered)
        }

    @Test
    fun `Data Saver broadcast retains callback network capabilities`() =
        runTest {
            val provider = provider()
            callback.captured.onAvailable(network)
            callback.captured.onCapabilitiesChanged(network, capabilities(metered = false))
            every { manager.restrictBackgroundStatus } returns ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            receiver.captured.onReceive(context, mockk<Intent>())

            val state = provider.dataSaverModeState.first()
            assertTrue(state.isDataSaverEnabled)
            assertFalse(state.isMetered)
            assertEquals(DataUsageMode.Unrestricted, DefaultDataUsagePolicy(provider).policy.first())
        }

    @Test
    fun `new default waits for its own capabilities and ignores late loss of old network`() =
        runTest {
            val provider = provider()
            callback.captured.onAvailable(network)
            callback.captured.onCapabilitiesChanged(network, capabilities(metered = false))
            val replacement = mockk<Network>()
            callback.captured.onAvailable(replacement)
            assertEquals(NetworkConnectionType.NONE, provider.dataSaverModeState.first().connectionType)
            callback.captured.onCapabilitiesChanged(replacement, capabilities(metered = true, NetworkCapabilities.TRANSPORT_CELLULAR))
            callback.captured.onLost(network)
            callback.captured.onCapabilitiesChanged(network, capabilities(metered = false))

            val state = provider.dataSaverModeState.first()
            assertEquals(NetworkConnectionType.CELLULAR, state.connectionType)
            assertTrue(state.isMetered)
        }

    @Test
    fun `loss of default remains offline despite stale connected getters`() =
        runTest {
            val provider = provider()
            callback.captured.onAvailable(network)
            callback.captured.onCapabilitiesChanged(network, capabilities(metered = false))
            callback.captured.onLost(network)

            assertEquals(NetworkConnectionType.NONE, provider.getCurrentDataSaverState().connectionType)
            assertEquals(DataUsageMode.Restricted, DefaultDataUsagePolicy(provider).policy.first())
        }
}
