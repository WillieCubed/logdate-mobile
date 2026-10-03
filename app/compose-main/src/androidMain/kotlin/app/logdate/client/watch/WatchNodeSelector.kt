package app.logdate.client.watch

import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import kotlinx.coroutines.tasks.await

/**
 * Finds the watches a phone message goes to: every reachable node running LogDate's watch app.
 *
 * Every watch build advertises [WATCH_CAPABILITY], so a connected node without it has no LogDate to
 * read the message. A watch connected directly comes before one reachable only through the cloud.
 */
class WatchNodeSelector(
    private val capabilityClient: CapabilityClient,
) {
    suspend fun reachableWatches(): List<Node> =
        capabilityClient
            .getCapability(WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .sortedByDescending { it.isNearby }

    companion object {
        const val WATCH_CAPABILITY = "logdate_watch_app"
    }
}
