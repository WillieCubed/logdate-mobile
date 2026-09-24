package app.logdate.client.sensor.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.logdate.client.networking.DataRestriction
import app.logdate.client.networking.DefaultDataUsagePolicy
import app.logdate.client.networking.saver.NetworkConnectionType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Android host tests for network-wide capabilities and this app's background restriction status. */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidNetworkSaverModeProviderTest {
    private val context = mockk<Context>(relaxed = true)
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    private val activeNetwork = mockk<Network>()
    private val capabilities = mockk<NetworkCapabilities>()

    private fun buildProvider(): AndroidNetworkSaverModeProvider {
        every { context.getSystemService(ConnectivityManager::class.java) } returns connectivityManager
        return AndroidNetworkSaverModeProvider(context)
    }

    private fun stubBackgroundRestriction(enabled: Boolean) {
        every { connectivityManager.restrictBackgroundStatus } returns
            if (enabled) {
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            } else {
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
            }
    }

    private fun stubActiveNetwork(
        notRestricted: Boolean,
        transport: Int = NetworkCapabilities.TRANSPORT_CELLULAR,
    ) {
        every { connectivityManager.activeNetwork } returns activeNetwork
        every { connectivityManager.getNetworkCapabilities(activeNetwork) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) } returns notRestricted
        every { capabilities.hasTransport(any()) } returns false
        every { capabilities.hasTransport(transport) } returns true
    }

    @Test
    fun `restricted network capability does not imply background data is disabled`() =
        runTest {
            stubBackgroundRestriction(enabled = false)
            // A restricted network capability says nothing about this app's background setting.
            stubActiveNetwork(notRestricted = false)
            val provider = buildProvider()

            val state = provider.getCurrentDataSaverState()

            assertFalse(state.isDataSaverEnabled, "a network capability does not describe this app's background data setting")
            assertEquals(NetworkConnectionType.CELLULAR, state.connectionType)
        }

    @Test
    fun `restricted network capability does not pause backup`() =
        runTest {
            stubBackgroundRestriction(enabled = false)
            stubActiveNetwork(notRestricted = false)
            val provider = buildProvider()
            val policy = DefaultDataUsagePolicy(provider)

            assertEquals(DataRestriction.NONE, policy.currentRestriction())
        }

    @Test
    fun `unrestricted network with Data Saver off reports no restriction`() =
        runTest {
            stubBackgroundRestriction(enabled = false)
            stubActiveNetwork(notRestricted = true)
            val provider = buildProvider()

            val state = provider.getCurrentDataSaverState()

            assertFalse(state.isDataSaverEnabled)
            assertEquals(NetworkConnectionType.CELLULAR, state.connectionType)
        }

    @Test
    fun `background restriction status remains authoritative regardless of network capability`() =
        runTest {
            stubBackgroundRestriction(enabled = true)
            // This network-wide capability does not override Android's background status.
            stubActiveNetwork(notRestricted = true)
            val provider = buildProvider()

            val state = provider.getCurrentDataSaverState()

            assertTrue(state.isDataSaverEnabled)
        }

    @Test
    fun `no active network is not treated as a per-app restriction`() =
        runTest {
            stubBackgroundRestriction(enabled = false)
            every { connectivityManager.activeNetwork } returns null
            val provider = buildProvider()

            val state = provider.getCurrentDataSaverState()

            assertFalse(state.isDataSaverEnabled)
            assertEquals(NetworkConnectionType.NONE, state.connectionType)
        }

    @Test
    fun `current state reads a newly connected network even if a callback was missed`() =
        runTest {
            stubBackgroundRestriction(enabled = false)
            every { connectivityManager.activeNetwork } returns null
            val provider = buildProvider()
            assertEquals(NetworkConnectionType.NONE, provider.getCurrentDataSaverState().connectionType)

            stubActiveNetwork(notRestricted = true, transport = NetworkCapabilities.TRANSPORT_WIFI)
            assertEquals(NetworkConnectionType.WIFI, provider.getCurrentDataSaverState().connectionType)
        }
}
