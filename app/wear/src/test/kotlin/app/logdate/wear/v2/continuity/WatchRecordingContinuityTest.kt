package app.logdate.wear.v2.continuity

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LiveCommandResult
import app.logdate.client.sync.datalayer.v2.LiveRecordingCommand
import app.logdate.client.sync.datalayer.v2.LiveRecordingState
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordingCommandType
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.v2.recorder.LiveMode
import app.logdate.wear.v2.recorder.RecorderState
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WatchRecordingContinuityTest {
    @Test
    fun `stop acknowledges only the current verified session after saving`() =
        runTest {
            val peer = mockk<WatchDeliveryPeer>()
            val context = PeerDeliveryContext("link", "phone", "watch", LinkMode.PAIR)
            coEvery { peer.current() } returns context
            coEvery { peer.isCurrent(context) } returns true
            val messages = mockk<MessageClient>()
            var message: LiveRecordingState? = null
            every { messages.sendMessage(any(), any(), any()) } answers {
                message =
                    (
                        WearJson.decode(
                            thirdArg<ByteArray>(),
                            WearProtocol.LIVE_SCHEMA,
                            LiveRecordingState.serializer(),
                        ) as Decoded.Supported
                    ).value
                Tasks.forResult(1)
            }
            val bridge = WatchRecordingContinuity(peer, messages)
            val state = MutableStateFlow<RecorderState>(RecorderState.Live(LiveMode.LOCKED))
            var saved = false
            bridge.attach(backgroundScope, state) {
                saved = true
                true
            }
            kotlinx.coroutines.withContext(UnconfinedTestDispatcher(testScheduler)) { kotlinx.coroutines.yield() }
            testScheduler.runCurrent()
            val command = LiveRecordingCommand(sessionId = message!!.sessionId, command = RecordingCommandType.STOP)
            val bytes = WearJson.encodeToBytes(LiveRecordingCommand.serializer(), command)
            assertFalse(decode(bridge.answer("stranger", bytes)).accepted)
            assertFalse(saved)
            assertTrue(decode(bridge.answer("phone", bytes)).accepted)
            assertTrue(saved)
        }

    private fun decode(bytes: ByteArray) =
        (WearJson.decode(bytes, WearProtocol.LIVE_SCHEMA, LiveCommandResult.serializer()) as Decoded.Supported).value
}
