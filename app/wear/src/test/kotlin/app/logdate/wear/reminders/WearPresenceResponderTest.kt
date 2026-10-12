package app.logdate.wear.reminders

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PresenceRequest
import app.logdate.client.sync.datalayer.v2.PresenceResponse
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.Presence
import app.logdate.client.sync.peer.state.PresenceState
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WearPresenceResponderTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private var current: PeerDeliveryContext? = peer
    private var enabled = true
    private var subscriptions = 0
    private var closed = false
    private var reading: ((Float?) -> Unit)? = null
    private val sampler =
        WearPresenceSampler(
            OffBodyReadings { callback ->
                subscriptions++
                reading = callback
                PresenceRegistration { closed = true }
            },
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(10_000)
            },
        )
    private val responder = WearPresenceResponder(sampler, { current }, { enabled })

    @Test
    fun `linked phone receives a scoped reading`() =
        runTest {
            val answer = async { responder.answer("phone", request()) }
            runCurrent()
            reading?.invoke(1f)
            val bytes = assertNotNull(answer.await())
            val response = assertIs<Decoded.Supported<PresenceResponse>>(WearJson.decode(bytes, 1, PresenceResponse.serializer())).value
            assertEquals(peer, response.context)
            assertEquals(Presence(PresenceState.ON_WRIST, 10_000), response.presence)
            assertTrue(closed)
        }

    @Test
    fun `foreign phone or obsolete exchange cannot start sensor`() =
        runTest {
            assertNull(responder.answer("foreign", request()))
            assertNull(responder.answer("phone", request(peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 })))))
            current = null
            assertNull(responder.answer("phone", request()))
            assertEquals(0, subscriptions)
        }

    @Test
    fun `disabled experience does not start sensor`() =
        runTest {
            enabled = false
            assertNull(responder.answer("phone", request()))
            assertEquals(0, subscriptions)
        }

    @Test
    fun `withdrawn link discards reading`() =
        runTest {
            val answer = async { responder.answer("phone", request()) }
            runCurrent()
            current = peer.copy(watchNode = "replacement")
            reading?.invoke(1f)
            assertNull(answer.await())
            assertTrue(closed)
        }

    @Test
    fun `disabled experience during reading discards response`() =
        runTest {
            val answer = async { responder.answer("phone", request()) }
            runCurrent()
            enabled = false
            reading?.invoke(1f)
            assertNull(answer.await())
            assertTrue(closed)
        }

    @Test
    fun `malformed future or oversized request cannot start sensor`() =
        runTest {
            for (bytes in listOf("bad".encodeToByteArray(), request(schema = 2), ByteArray(8_193))) {
                assertNull(responder.answer("phone", bytes))
            }
            assertEquals(0, subscriptions)
        }

    private fun request(
        context: PeerDeliveryContext = peer,
        schema: Int = 1,
    ): ByteArray = WearJson.encodeToBytes(PresenceRequest.serializer(), PresenceRequest(schema, context))
}
