package app.logdate.client.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidNetworkAvailabilityMonitorTest {
    @Test
    fun `internet capable network is available before Android validation finishes`() {
        val context = mockk<Context>(relaxed = true)
        val manager = mockk<ConnectivityManager>(relaxed = true)
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        every { manager.activeNetwork } returns network
        every { manager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false

        assertTrue(AndroidNetworkAvailabilityMonitor(context).isNetworkAvailable())
    }
}
