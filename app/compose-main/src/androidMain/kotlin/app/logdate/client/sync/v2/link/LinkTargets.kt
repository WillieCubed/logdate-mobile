package app.logdate.client.sync.v2.link

import app.logdate.client.sync.datalayer.v2.WearProtocol
import com.google.android.gms.wearable.CapabilityClient
import kotlinx.coroutines.tasks.await

/** A watch the phone can link. */
data class LinkTarget(
    val nodeId: String,
    val displayName: String,
)

fun interface LinkTargets {
    suspend fun reachable(): List<LinkTarget>
}

/** The reachable watches whose redesigned app is on: they advertise [WearProtocol.WATCH_CAPABILITY_V2]. */
class CapabilityLinkTargets(
    private val capabilityClient: CapabilityClient,
) : LinkTargets {
    override suspend fun reachable(): List<LinkTarget> =
        capabilityClient
            .getCapability(WearProtocol.WATCH_CAPABILITY_V2, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .map { node -> LinkTarget(node.id, node.displayName) }
}
