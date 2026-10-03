package app.logdate.client.watch

import android.content.Context
import android.content.Intent
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.logdate.client.sync.WatchMessageSender
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.feature.core.settings.ui.watch.WatchRequestFailure
import app.logdate.feature.core.settings.ui.watch.WatchRequestResult
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * "Open LogDate on watch" sent an ACTION_MAIN intent, which RemoteActivityHelper refuses before
 * anything leaves the phone, and both buttons picked whichever watch was listed first. These pin
 * which watch each request goes to, which link it sends, and how each failure reads.
 */
class AndroidWatchConnectionManagerTest {
    private val withLogDate = watch(nodeId = "watch-with-logdate")
    private val withoutLogDate = watch(nodeId = "watch-without-logdate")
    private val nodeClient = mockk<NodeClient>()
    private val capabilityClient = mockk<CapabilityClient>()
    private val remoteActivityHelper = mockk<RemoteActivityHelper>()
    private val linkIntent = mockk<Intent>()
    private val sentLinks = mutableListOf<String>()
    private val accepted = mockk<ListenableFuture<Void>> { every { get(any(), any()) } returns null }
    private val sentMessages = mutableListOf<String>()
    private var messageDelivered = true

    @Test
    fun `open sends the Today link to the watch that has LogDate`() =
        runTest {
            connected(withoutLogDate, withLogDate)
            every { remoteActivityHelper.startRemoteActivity(linkIntent, any()) } returns accepted

            val result = manager().openAppOnWatch()

            assertEquals(WatchRequestResult.Sent, result)
            assertEquals(listOf("studio.hypertext.logdate://watch/today"), sentLinks)
            verify(exactly = 1) { remoteActivityHelper.startRemoteActivity(linkIntent, "watch-with-logdate") }
        }

    @Test
    fun `install opens the Play listing on the watch without LogDate`() =
        runTest {
            connected(withLogDate, withoutLogDate)
            every { remoteActivityHelper.startRemoteActivity(linkIntent, any()) } returns accepted

            val result = manager().installAppOnWatch()

            assertEquals(WatchRequestResult.Sent, result)
            assertEquals(listOf("market://details?id=studio.hypertext.logdate"), sentLinks)
            verify(exactly = 1) { remoteActivityHelper.startRemoteActivity(linkIntent, "watch-without-logdate") }
        }

    @Test
    fun `install opens the Play listing on a watch that has LogDate when it is the only one`() =
        runTest {
            connected(withLogDate)
            every { remoteActivityHelper.startRemoteActivity(linkIntent, any()) } returns accepted

            manager().installAppOnWatch()

            verify(exactly = 1) { remoteActivityHelper.startRemoteActivity(linkIntent, "watch-with-logdate") }
        }

    @Test
    fun `open reports that LogDate is missing instead of sending anything`() =
        runTest {
            connected(withoutLogDate)

            val result = manager().openAppOnWatch()

            assertEquals(WatchRequestResult.Failed(WatchRequestFailure.NOT_INSTALLED_ON_WATCH), result)
            verify(exactly = 0) { remoteActivityHelper.startRemoteActivity(any(), any()) }
        }

    @Test
    fun `both requests report when no watch is connected`() =
        runTest {
            connected()
            val manager = manager()

            assertEquals(WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED), manager.openAppOnWatch())
            assertEquals(WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED), manager.installAppOnWatch())
        }

    @Test
    fun `a watch that refuses the request is reported`() =
        runTest {
            connected(withLogDate)
            val refused =
                mockk<ListenableFuture<Void>> {
                    every { get(any(), any()) } throws
                        ExecutionException(RemoteActivityHelper.RemoteIntentException("There was an error while starting remote activity."))
                }
            every { remoteActivityHelper.startRemoteActivity(linkIntent, any()) } returns refused

            assertEquals(WatchRequestResult.Failed(WatchRequestFailure.WATCH_DID_NOT_RESPOND), manager().openAppOnWatch())
        }

    @Test
    fun `a Wearable API failure reads as no watch connected`() =
        runTest {
            every { nodeClient.connectedNodes } returns Tasks.forException(IllegalStateException("Wearable API unavailable"))

            assertEquals(WatchRequestResult.Failed(WatchRequestFailure.NO_WATCH_CONNECTED), manager().openAppOnWatch())
        }

    @Test
    fun `a sync request goes to the watches running LogDate`() =
        runTest {
            manager().requestSync()

            assertEquals(listOf(WearAudioRequestPaths.SYNC_REQUEST_PATH), sentMessages)
        }

    @Test
    fun `a sync request no watch received fails with the reason`() =
        runTest {
            messageDelivered = false

            val error = assertFailsWith<IllegalStateException> { manager().requestSync() }

            assertEquals("No watch running LogDate received the sync request", error.message)
        }

    private fun connected(vararg watches: Node) {
        every { nodeClient.connectedNodes } returns Tasks.forResult(watches.toList())
        val withApp = mockk<CapabilityInfo> { every { nodes } returns watches.filter { it === withLogDate }.toSet() }
        every { capabilityClient.getCapability("logdate_watch_app", CapabilityClient.FILTER_ALL) } returns Tasks.forResult(withApp)
    }

    private fun manager() =
        AndroidWatchConnectionManager(
            context = mockk<Context> { every { packageName } returns "studio.hypertext.logdate" },
            associationManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            nodeClient = nodeClient,
            capabilityClient = capabilityClient,
            messageSender =
                WatchMessageSender { path, _ ->
                    sentMessages += path
                    messageDelivered
                },
            remoteActivityHelper = remoteActivityHelper,
            linkIntent = { link ->
                sentLinks += link
                linkIntent
            },
        )

    private fun watch(nodeId: String) =
        mockk<Node> {
            every { id } returns nodeId
            every { displayName } returns "Pixel Watch"
            every { isNearby } returns true
        }
}
