package app.logdate.client.sync.v2.continuity

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LiveRecordingState
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordingPhase
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import com.google.android.gms.wearable.MessageClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PhoneRecordingAudienceTest {
    private var audience =
        PeerDeliveryContext(
            LinkWire.encodeKey(ByteArray(32) { 1 }),
            "phone",
            "watch",
            LinkMode.CLOUD,
            "account-a",
            "https://example.test",
        )
    private val peers =
        mockk<PhoneDeliveryPeers> {
            coEvery { current("watch") } answers { audience }
            coEvery { isCurrent(any()) } answers { firstArg<PeerDeliveryContext>() == audience }
        }
    private val messages = mockk<MessageClient>()
    private val companion = PhoneRecordingContinuity(peers, messages, nowMs = { 1_000L })

    @Test
    fun `relinking the same watch discards the previous recording session`() =
        runTest {
            receive()
            assertNotNull(companion.current())
            audience = audience.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
            assertNull(companion.current())
            assertFalse(companion.stop())
        }

    @Test
    fun `changing account cannot expose or stop the old recording session`() =
        runTest {
            receive()
            audience = audience.copy(accountId = "account-b")
            assertNull(companion.current())
            assertFalse(companion.stop())
        }

    private suspend fun receive() {
        companion.receive(
            "watch",
            WearJson.encodeToBytes(
                LiveRecordingState.serializer(),
                LiveRecordingState(
                    sessionId = "recording",
                    sequence = 1,
                    phase = RecordingPhase.RECORDING,
                    elapsedMs = 500,
                    sentAt = 1_000,
                ),
            ),
        )
    }
}
