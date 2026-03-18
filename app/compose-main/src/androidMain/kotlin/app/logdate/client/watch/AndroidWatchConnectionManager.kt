package app.logdate.client.watch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val WATCH_CAPABILITY = "logdate_watch_app"
private const val SYNC_REQUEST_PATH = "/logdate/sync/request"
private const val WEAR_APP_PACKAGE = "app.logdate.wear"

/**
 * Android implementation of [WatchConnectionManager] using the Wearable Data Layer API.
 *
 * Detects paired watches via [NodeClient], checks for LogDate installation via
 * [CapabilityClient], and uses [MessageClient] for sync requests.
 */
class AndroidWatchConnectionManager(
    private val context: Context,
) : WatchConnectionManager {
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val remoteActivityHelper: RemoteActivityHelper = RemoteActivityHelper(context)

    override fun observeConnectionState(): Flow<WatchConnectionState> =
        callbackFlow {
            trySend(resolveConnectionState())

            // Listen for capability changes with FILTER_REACHABLE — fires on both
            // app install/uninstall AND reachability changes (in/out of range)
            val capabilityListener =
                CapabilityClient.OnCapabilityChangedListener {
                    launch { trySend(resolveConnectionState()) }
                }
            capabilityClient.addListener(
                capabilityListener,
                Uri.parse("wear://*/logdate_watch_app"),
                CapabilityClient.FILTER_REACHABLE,
            )

            awaitClose {
                capabilityClient.removeListener(capabilityListener)
            }
        }

    override suspend fun requestSync() {
        val nodes = nodeClient.connectedNodes.await()
        for (node in nodes) {
            messageClient.sendMessage(node.id, SYNC_REQUEST_PATH, byteArrayOf()).await()
            Napier.d { "Sent sync request to ${node.displayName}" }
        }
    }

    override suspend fun installAppOnWatch() {
        val targetNode = firstConnectedNode() ?: return

        val playStoreIntent =
            Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse("market://details?id=$WEAR_APP_PACKAGE"))

        try {
            withContext(Dispatchers.IO) {
                remoteActivityHelper.startRemoteActivity(playStoreIntent, targetNode.id).get()
            }
        } catch (e: Exception) {
            Napier.e(e) { "Failed to launch Play Store on watch" }
        }
    }

    override suspend fun openAppOnWatch() {
        val targetNode = firstConnectedNode() ?: return

        val launchIntent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(WEAR_APP_PACKAGE)

        try {
            withContext(Dispatchers.IO) {
                remoteActivityHelper.startRemoteActivity(launchIntent, targetNode.id).get()
            }
        } catch (e: Exception) {
            Napier.e(e) { "Failed to open app on watch" }
        }
    }

    private suspend fun firstConnectedNode(): Node? = nodeClient.connectedNodes.await().firstOrNull()

    private suspend fun resolveConnectionState(): WatchConnectionState {
        return try {
            coroutineScope {
                val nodesDeferred = async { nodeClient.connectedNodes.await() }
                val capabilityDeferred =
                    async {
                        capabilityClient.getCapability(WATCH_CAPABILITY, CapabilityClient.FILTER_ALL).await()
                    }

                val nodes = nodesDeferred.await()
                if (nodes.isEmpty()) {
                    return@coroutineScope WatchConnectionState.NoPairedWatch
                }

                val capabilityInfo = capabilityDeferred.await()
                val watchNode = nodes.first()
                val watchName = watchNode.displayName
                val hasApp =
                    capabilityInfo.nodes.any { capNode ->
                        nodes.any { it.id == capNode.id }
                    }

                if (!hasApp) {
                    WatchConnectionState.AppNotInstalled(watchName)
                } else {
                    val isReachable = capabilityInfo.nodes.any { it.isNearby }
                    if (isReachable) {
                        WatchConnectionState.Connected(
                            watchName = watchName,
                            lastSynced = null,
                            pendingCount = 0,
                        )
                    } else {
                        WatchConnectionState.OutOfRange(
                            watchName = watchName,
                            lastSynced = null,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Napier.e(e) { "Failed to resolve watch connection state" }
            WatchConnectionState.NoPairedWatch
        }
    }
}
