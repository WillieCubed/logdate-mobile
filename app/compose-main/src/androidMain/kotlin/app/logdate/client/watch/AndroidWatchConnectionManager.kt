package app.logdate.client.watch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val WATCH_CAPABILITY = "logdate_watch_app"
private const val SYNC_REQUEST_PATH = "/logdate/sync/request"
private const val WEAR_APP_PACKAGE = "app.logdate.wear"

/**
 * Android implementation of [WatchConnectionManager] using Wear transport plus
 * Companion Device association state from the phone app.
 */
class AndroidWatchConnectionManager(
    private val context: Context,
    private val associationManager: WatchCompanionAssociationManager,
    private val ioDispatcher: CoroutineDispatcher,
) : WatchConnectionManager {
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val remoteActivityHelper: RemoteActivityHelper = RemoteActivityHelper(context)

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

    override suspend fun requestSync() {
        val nodes = nodeClient.connectedNodes.await()
        for (node in nodes) {
            messageClient.sendMessage(node.id, SYNC_REQUEST_PATH, byteArrayOf()).await()
            Napier.d { "Sent sync request to ${node.displayName}" }
        }
    }

    override suspend fun installAppOnWatch() {
        val targetNode = nodeClient.connectedNodes.await().firstOrNull() ?: return

        val playStoreIntent =
            Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse("market://details?id=$WEAR_APP_PACKAGE"))

        try {
            withContext(ioDispatcher) {
                remoteActivityHelper.startRemoteActivity(playStoreIntent, targetNode.id).get()
            }
        } catch (e: Exception) {
            Napier.e(e) { "Failed to launch Play Store on watch" }
        }
    }

    override suspend fun openAppOnWatch() {
        val targetNode = nodeClient.connectedNodes.await().firstOrNull() ?: return

        val launchIntent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(WEAR_APP_PACKAGE)

        try {
            withContext(ioDispatcher) {
                remoteActivityHelper.startRemoteActivity(launchIntent, targetNode.id).get()
            }
        } catch (e: Exception) {
            Napier.e(e) { "Failed to open app on watch" }
        }
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
                Uri.parse("wear://*/$WATCH_CAPABILITY"),
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
                    .getCapability(WATCH_CAPABILITY, CapabilityClient.FILTER_ALL)
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
