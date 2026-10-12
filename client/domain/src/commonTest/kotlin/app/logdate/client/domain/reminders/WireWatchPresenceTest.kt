package app.logdate.client.domain.reminders

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PresenceRequest
import app.logdate.client.sync.datalayer.v2.PresenceResponse
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.Presence
import app.logdate.client.sync.peer.state.PresenceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WireWatchPresenceTest {
    private val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val onWrist = Presence(PresenceState.ON_WRIST, 1_000)

    private fun reply(
        peer: PeerDeliveryContext = context,
        schema: Int = 1,
    ) = WearJson.encodeToBytes(
        PresenceResponse.serializer(),
        PresenceResponse(schema, peer, onWrist),
    )

    @Test
    fun requestsOneReadingFromTheVerifiedWatch() =
        runTest {
            val source =
                WireWatchPresence({ node, path, bytes ->
                    assertEquals("watch", node)
                    assertEquals("/logdate/presence", path)
                    assertEquals(
                        context,
                        assertIs<Decoded.Supported<PresenceRequest>>(
                            WearJson.decode(bytes, 1, PresenceRequest.serializer()),
                        ).value.context,
                    )
                    reply()
                }, { it == context })
            assertEquals(onWrist, source.sample(context))
        }

    @Test
    fun timeoutIsUnknownAfterTwoSeconds() =
        runTest {
            assertEquals(Presence(), WireWatchPresence({ _, _, _ -> awaitCancellation() }, { true }).sample(context))
            assertEquals(2_000L, testScheduler.currentTime)
        }

    @Test
    fun unavailableTransportIsUnknownButCancellationPropagates() =
        runTest {
            assertEquals(Presence(), WireWatchPresence({ _, _, _ -> error("unreachable") }, { true }).sample(context))
            assertFailsWith<CancellationException> {
                WireWatchPresence({ _, _, _ -> throw CancellationException("stop") }, { true }).sample(context)
            }
        }

    @Test
    fun foreignFutureSchemaOrOversizedResponseCannotSupplyPresence() =
        runTest {
            for (bytes in listOf(reply(context.copy(watchNode = "foreign")), reply(schema = 2), ByteArray(8193))) {
                assertEquals(Presence(), WireWatchPresence({ _, _, _ -> bytes }, { true }).sample(context))
            }
        }

    @Test
    fun withdrawnLinkCannotSupplyAReadingOrStartAnotherRequest() =
        runTest {
            var current = true
            var calls = 0
            val source =
                WireWatchPresence({ _, _, _ ->
                    calls++
                    current = false
                    reply()
                }, { current })
            assertEquals(Presence(), source.sample(context))
            assertEquals(Presence(), source.sample(context))
            assertEquals(1, calls)
        }
}
