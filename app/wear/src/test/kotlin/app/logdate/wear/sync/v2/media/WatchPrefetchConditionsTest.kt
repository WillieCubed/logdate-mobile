package app.logdate.wear.sync.v2.media

import app.logdate.client.domain.watch.WatchAudioDownloadPolicy
import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.networking.saver.NetworkSaverState
import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sensor.battery.BatteryState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchPrefetchConditionsTest {
    private val network = mockk<NetworkSaverModeProvider>()
    private val battery = mockk<BatteryInfoProvider>()
    private val conditions = WatchPrefetchConditions(network, battery)

    @Test fun unvalidatedConnectivityNeverDownloadsEvenWhileCharging() =
        runTest {
            coEvery { network.getCurrentDataSaverState() } returns NetworkSaverState(false, NetworkConnectionType.WIFI, false, false)
            coEvery { battery.getCurrentBatteryState() } returns BatteryState(90, true, false)
            assertFalse(conditions.permits(WatchAudioDownloadPolicy.WIFI_OR_CHARGING))
        }

    @Test fun chargerRemovalAndDataSaverStopMeteredPrefetch() =
        runTest {
            var charging = true
            var dataSaver = false
            coEvery { network.getCurrentDataSaverState() } coAnswers { NetworkSaverState(dataSaver, NetworkConnectionType.CELLULAR) }
            coEvery { battery.getCurrentBatteryState() } coAnswers { BatteryState(80, charging, false) }
            assertTrue(conditions.permits(WatchAudioDownloadPolicy.WIFI_OR_CHARGING))
            charging = false
            assertFalse(conditions.permits(WatchAudioDownloadPolicy.WIFI_OR_CHARGING))
            charging = true
            dataSaver = true
            assertFalse(conditions.permits(WatchAudioDownloadPolicy.WIFI_OR_CHARGING))
        }

    @Test fun onDemandDoesNotEvenReadBackgroundConditions() =
        runTest {
            assertFalse(conditions.permits(WatchAudioDownloadPolicy.ON_DEMAND))
        }
}
