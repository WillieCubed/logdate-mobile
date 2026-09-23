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

/**
 * On a real device, LogDate could have global Data Saver off yet still have its own
 * "Background data" toggle turned off per-app (Settings > Apps > LogDate > Data usage). That
 * combination silently killed background sync over cellular with no UI signal, because the old
 * detection only read [ConnectivityManager.getRestrictBackgroundStatus], which only reports the
 * device-wide toggle.
 *
 * These tests run on the JVM via `androidHostTest` - no emulator, no Robolectric. Android
 * framework types ([Context], [ConnectivityManager], [NetworkCapabilities]) are mocked directly,
 * matching the pattern already used for local unit tests elsewhere in this codebase (e.g.
 * `AndroidAudioPlaybackManagerTest`).
 */
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

    private fun stubGlobalDataSaver(enabled: Boolean) {
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
    fun `per-app background data restriction is detected when global Data Saver is off`() =
        runTest {
            stubGlobalDataSaver(enabled = false)
            // NET_CAPABILITY_NOT_RESTRICTED absent: this app is background-restricted even
            // though the system-wide Data Saver toggle is off.
            stubActiveNetwork(notRestricted = false)
            val provider = buildProvider()

            val state = provider.getCurrentDataSaverState()

            assertTrue(state.isDataSaverEnabled, "per-app background data restriction should read as data saver enabled")
            assertEquals(NetworkConnectionType.CELLULAR, state.connectionType)
        }

    @Test
    fun `per-app background data restriction maps to BACKGROUND_DATA_BLOCKED downstream`() =
        runTest {
            stubGlobalDataSaver(enabled = false)
            stubActiveNetwork(notRestricted = false)
            val provider = buildProvider()
            val policy = DefaultDataUsagePolicy(provider)

            assertEquals(DataRestriction.BACKGROUND_DATA_BLOCKED, policy.currentRestriction())
        }

    @Test
    fun `unrestricted network with Data Saver off reports no restriction`() =
        runTest {
            stubGlobalDataSaver(enabled = false)
            stubActiveNetwork(notRestricted = true)
            val provider = buildProvider()

            val state = provider.getCurrentDataSaverState()

            assertFalse(state.isDataSaverEnabled)
            assertEquals(NetworkConnectionType.CELLULAR, state.connectionType)
        }

    @Test
    fun `global Data Saver enabled still reports data saver enabled regardless of per-app capability`() =
        runTest {
            stubGlobalDataSaver(enabled = true)
            // Not restricted per-app, but the device-wide toggle is still authoritative.
            stubActiveNetwork(notRestricted = true)
            val provider = buildProvider()

            val state = provider.getCurrentDataSaverState()

            assertTrue(state.isDataSaverEnabled)
        }

    @Test
    fun `no active network is not treated as a per-app restriction`() =
        runTest {
            stubGlobalDataSaver(enabled = false)
            every { connectivityManager.activeNetwork } returns null
            val provider = buildProvider()

            val state = provider.getCurrentDataSaverState()

            assertFalse(state.isDataSaverEnabled)
            assertEquals(NetworkConnectionType.NONE, state.connectionType)
        }
}
