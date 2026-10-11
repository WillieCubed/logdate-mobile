package app.logdate.wear.sync.v2.delivery

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.client.sync.peer.delivery.RouteConditions
import app.logdate.client.sync.peer.delivery.WatchNetwork
import app.logdate.client.sync.peer.delivery.watchNetworkOf
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import app.logdate.wear.sync.v2.link.WatchLinkRecord
import app.logdate.wear.sync.v2.link.WatchLinkStore
import app.logdate.wear.sync.v2.link.peerLinkState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** Reads what the watch knows when it routes a change: the phone, the link, the server, the network and the charger. */
class WatchRouteConditions(
    private val transport: DataLayerTransportV2,
    private val phoneCloud: suspend () -> CloudPresence,
    private val links: WatchLinkStore,
    private val config: LogDateConfigRepository,
    private val network: NetworkSaverModeProvider,
    private val battery: BatteryInfoProvider,
    private val sessions: SessionStorage,
) {
    suspend fun current(): RouteConditions {
        val before = binding()
        val reach = transport.reach()
        val cloud = phoneCloud()
        val network = readNetwork()
        val charging = readCharging()
        val usable = before == binding() && before.usable()
        val linked = usable && before.link?.mode == LinkMode.CLOUD
        return RouteConditions(
            phone = if (usable) reach else PhoneReach.UNREACHABLE,
            phoneCloud = if (usable) cloud else CloudPresence.UNKNOWN,
            linked = linked,
            serverReady = linked && serverReady(before.descriptor),
            network = network,
            charging = charging,
        )
    }

    private suspend fun binding(): Binding =
        Binding(
            link = links.current(),
            session = sessions.getOriginBoundSession(),
            backend = config.getCurrentBackendUrl(),
            descriptor = config.getCurrentServerDescriptor(),
        )

    private data class Binding(
        val link: WatchLinkRecord?,
        val session: OriginBoundSession?,
        val backend: String,
        val descriptor: ServerDescriptor?,
    ) {
        fun usable(): Boolean {
            val record = link ?: return false
            if (record.phoneNode.isBlank() || record.stale || record.installing || record.problem != null) return false
            if (record.mode == LinkMode.PAIR) return record.accountId == null && session == null
            if (record.peerLinkState() != PeerLinkState.LINKED) return false
            val origin = record.serverOrigin?.takeIf { it.isNotBlank() } ?: return false
            val account = record.accountId?.takeIf { it.isNotBlank() } ?: return false
            return session?.session?.accountId == account &&
                session.origin == origin &&
                backend == origin &&
                (descriptor == null || descriptor.serverOrigin == origin)
        }
    }

    private suspend fun readNetwork(): WatchNetwork =
        try {
            watchNetworkOf(network.getCurrentDataSaverState())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not read the watch's network; it counts as none", e)
            WatchNetwork.NONE
        }

    private suspend fun readCharging(): Boolean =
        try {
            battery.getCurrentBatteryState().isCharging
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not read whether the watch is charging; it counts as not charging", e)
            false
        }
}

/** A watch uploads only to a server that links devices and creates without overwriting. */
internal fun serverReady(descriptor: ServerDescriptor?): Boolean =
    descriptor != null &&
        descriptor.hasProtocolFeature(ServerProtocolFeature.DEVICE_LINKING_V1) &&
        descriptor.hasProtocolFeature(ServerProtocolFeature.CONDITIONAL_CREATE_V1)
