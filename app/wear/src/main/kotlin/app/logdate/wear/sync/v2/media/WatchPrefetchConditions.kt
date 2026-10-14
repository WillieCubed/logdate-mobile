package app.logdate.wear.sync.v2.media

import app.logdate.client.domain.watch.WatchAudioDownloadPolicy
import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sync.peer.delivery.WatchNetwork
import app.logdate.client.sync.peer.delivery.watchNetworkOf
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** Read at run time and at each media-range authorization, so cost/charger changes stop downloading. */
class WatchPrefetchConditions(
    private val network: NetworkSaverModeProvider,
    private val battery: BatteryInfoProvider,
) {
    suspend fun permits(policy: WatchAudioDownloadPolicy): Boolean =
        try {
            if (policy == WatchAudioDownloadPolicy.ON_DEMAND) {
                false
            } else {
                val state = network.getCurrentDataSaverState()
                val route = watchNetworkOf(state)
                val unmetered = route == WatchNetwork.UNMETERED
                policy.permits(
                    unmetered,
                    route != WatchNetwork.NONE && (!state.isDataSaverEnabled || unmetered),
                    battery.getCurrentBatteryState().isCharging,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not verify background audio conditions; downloads wait", e)
            false
        }
}
