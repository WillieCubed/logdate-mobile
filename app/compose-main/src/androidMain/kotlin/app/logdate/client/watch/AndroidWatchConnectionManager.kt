package app.logdate.client.watch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.logdate.client.sync.WatchMessageSender
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import app.logdate.feature.core.settings.ui.watch.WatchRequestFailure
import app.logdate.feature.core.settings.ui.watch.WatchRequestResult
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val REMOTE_START_TIMEOUT_SECONDS = 15L

/** Opens LogDate on the watch at Today. The watch's MainActivity declares a matching VIEW filter. */
const val WATCH_TODAY_LINK = "studio.hypertext.logdate://watch/today"

/** LogDate's Play Store listing, which the watch opens in its own Play Store. */
fun playListingLink(packageName: String): String = "market://details?id=$packageName"

/**
 * The only intent shape [RemoteActivityHelper] starts on another device: ACTION_VIEW with a data
 * URI and CATEGORY_BROWSABLE. Anything else fails with IllegalArgumentException before it is sent.
 */
fun watchLinkIntent(link: String): Intent =
    Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setData(Uri.parse(link))

/**
 * Android implementation of [WatchConnectionManager] using Wear transport plus
 * Companion Device association state from the phone app.
 */
class AndroidWatchConnectionManager(
    private val context: Context,
    private val associationManager: WatchCompanionAssociationManager,
    private val messageSender: WatchMessageSender,
    private val ioDispatcher: CoroutineDispatcher,
    private val nodeClient: NodeClient = Wearable.getNodeClient(context),
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context),
    private val remoteActivityHelper: RemoteActivityHelper = RemoteActivityHelper(context),
    private val linkIntent: (String) -> Intent = ::watchLinkIntent,
) : WatchConnectionManager {
    override fun observeConnectionState(): Flow<WatchConnectionState> =
        combine(
            associationManager.observeAssociationState(),
            observeTransportSnapshot(),
        ) { associationState, transportSnapshot ->
            resolveWatchConnectionState(associationState, transportSnapshot)
        }

    override suspend fun beginAssociation() {
        associationManager.beginAssociation()
    }

    /** Asks every reachable watch running LogDate to send what it has waiting. */
    override suspend fun requestSync() {
        check(messageSender.send(WearAudioRequestPaths.SYNC_REQUEST_PATH, ByteArray(0))) {
            "No watch running LogDate received the sync request"
        }
    }

    override suspend fun installAppOnWatch(): WatchRequestResult {
        val watches = connectedWatches() ?: return WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED)
        val target =
            watches.withoutApp.firstOrNull()
                ?: watches.withApp.firstOrNull()
                ?: return WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED)
        return startOnWatch(playListingLink(context.packageName), target)
    }

    override suspend fun openAppOnWatch(): WatchRequestResult {
        val watches = connectedWatches() ?: return WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED)
        if (watches.isEmpty()) return WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED)
        val target = watches.withApp.firstOrNull() ?: return WatchRequestResult.Failed(WatchRequestFailure.NOT_INSTALLED_ON_WATCH)
        return startOnWatch(WATCH_TODAY_LINK, target)
    }

    private suspend fun startOnWatch(
        link: String,
        watch: Node,
    ): WatchRequestResult =
        try {
            withContext(ioDispatcher) {
                remoteActivityHelper
                    .startRemoteActivity(linkIntent(link), watch.id)
                    .get(REMOTE_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            WatchRequestResult.Sent
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e(e) { "${watch.displayName} did not open $link" }
            WatchRequestResult.Failed(WatchRequestFailure.WATCH_DID_NOT_RESPOND)
        }

    /** The connected watches, split by whether they have LogDate, or null when the Wearable API failed. */
    private suspend fun connectedWatches(): ConnectedWatches? =
        try {
            val connected = nodeClient.connectedNodes.await()
            val withAppIds =
                capabilityClient
                    .getCapability(WatchNodeSelector.WATCH_CAPABILITY, CapabilityClient.FILTER_ALL)
                    .await()
                    .nodes
                    .map { it.id }
                    .toSet()
            val (withApp, withoutApp) = connected.partition { it.id in withAppIds }
            ConnectedWatches(withApp = withApp, withoutApp = withoutApp)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e(e) { "Could not list the watches connected to this phone" }
            null
        }

    private class ConnectedWatches(
        val withApp: List<Node>,
        val withoutApp: List<Node>,
    ) {
        fun isEmpty(): Boolean = withApp.isEmpty() && withoutApp.isEmpty()
    }

    /** Watches Wear capability changes and refreshes the current transport snapshot. */
    private fun observeTransportSnapshot(): Flow<WatchTransportSnapshot> =
        callbackFlow {
            suspend fun emitSnapshot() {
                trySend(resolveTransportSnapshot())
            }

            emitSnapshot()

            val capabilityListener =
                CapabilityClient.OnCapabilityChangedListener {
                    launch {
                        emitSnapshot()
                    }
                }
            capabilityClient.addListener(
                capabilityListener,
                Uri.parse("wear://*/${WatchNodeSelector.WATCH_CAPABILITY}"),
                CapabilityClient.FILTER_ALL,
            )

            awaitClose {
                capabilityClient.removeListener(capabilityListener)
            }
        }

    /** Captures the current set of connected nodes and installed LogDate watch nodes. */
    private suspend fun resolveTransportSnapshot(): WatchTransportSnapshot =
        try {
            val connectedNodes =
                nodeClient.connectedNodes.await().map { node ->
                    WatchNodeSnapshot(
                        id = node.id,
                        displayName = node.displayName,
                        isNearby = node.isNearby,
                    )
                }
            val appNodes =
                capabilityClient
                    .getCapability(WatchNodeSelector.WATCH_CAPABILITY, CapabilityClient.FILTER_ALL)
                    .await()
                    .nodes
                    .map { node ->
                        WatchNodeSnapshot(
                            id = node.id,
                            displayName = node.displayName,
                            isNearby = node.isNearby,
                        )
                    }

            WatchTransportSnapshot(
                connectedNodes = connectedNodes,
                appNodes = appNodes,
            )
        } catch (e: Exception) {
            Napier.e(e) { "Failed to resolve watch transport state" }
            WatchTransportSnapshot()
        }
}
