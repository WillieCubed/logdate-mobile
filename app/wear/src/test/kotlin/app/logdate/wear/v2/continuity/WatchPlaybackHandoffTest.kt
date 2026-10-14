package app.logdate.wear.v2.continuity

import app.logdate.client.sync.continuity.PlaybackHandoffResult
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WearProtocolGate
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.playback.MomentPlayer
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.PlaybackState
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.MessageClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class WatchPlaybackHandoffTest {
    @Test
    fun `phone playback acknowledgement can outlast cold startup`() =
        runTest {
            val id = Uuid.random()
            val context = PeerDeliveryContext("link", "phone", "watch", LinkMode.PAIR)
            val peer = mockk<WatchDeliveryPeer>()
            coEvery { peer.current() } returns context
            coEvery { peer.isCurrent(context) } returns true
            val reply = TaskCompletionSource<ByteArray>()
            val messages = mockk<MessageClient>()
            every { messages.sendRequest("phone", WearPaths.LIVE_PLAYBACK_COMMAND, any()) } returns reply.task
            val player = mockk<MomentPlayer>(relaxed = true)
            every { player.state } returns MutableStateFlow(PlaybackState.Idle)
            every { player.speed } returns MutableStateFlow(PlaybackSpeed.NORMAL)
            val gate = WearProtocolGate(flowOf(true), flowOf(true), flowOf(setOf(WearFeatures.PHONE_PLAYBACK)))
            val handoff = WatchPlaybackHandoff(peer, messages, player, mockk<PhoneHandoff>(), gate)

            launch {
                delay(6_000)
                reply.setResult(WearJson.encodeToBytes(PlaybackHandoffResult.serializer(), PlaybackHandoffResult(accepted = true)))
            }

            val outcome = handoff.playOnPhone(id)
            verify(exactly = 1) { messages.sendRequest("phone", WearPaths.LIVE_PLAYBACK_COMMAND, any()) }
            assertEquals(null, outcome)
        }
}
