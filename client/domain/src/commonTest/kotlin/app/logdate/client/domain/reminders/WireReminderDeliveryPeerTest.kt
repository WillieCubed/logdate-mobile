package app.logdate.client.domain.reminders

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WireReminderDeliveryPeerTest {
    private val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val claim = ReminderDayClaim(context, LocalDate(2026, 10, 12), "00000000-0000-4000-8000-000000000001", PeerRole.PHONE)
    private val request = ReminderDeliveryRequest(claim = claim, trigger = ReminderTrigger.ARRIVED_SOMEWHERE_NEW, openMarks = 2)

    @Test
    fun sendsExactClaimTriggerAndMarksToWatchDeliveryPath() =
        runTest {
            val peer =
                WireReminderDeliveryPeer { node, path, bytes ->
                    assertEquals("watch", node)
                    assertEquals("/logdate/reminders/deliver", path)
                    assertEquals(
                        request,
                        assertIs<Decoded.Supported<ReminderDeliveryRequest>>(
                            WearJson.decode(bytes, 1, ReminderDeliveryRequest.serializer()),
                        ).value,
                    )
                    WearJson.encodeToBytes(
                        ReminderDeliveryResponse.serializer(),
                        ReminderDeliveryResponse(
                            claim = claim,
                            status = ReminderDeliveryStatus.POSTED,
                        ),
                    )
                }
            assertEquals(ReminderDeliveryStatus.POSTED, peer.deliver(request))
        }

    @Test
    fun explicitNotPostedIsDistinguishedFromAnUncertainReply() =
        runTest {
            val peer =
                WireReminderDeliveryPeer { _, _, _ ->
                    WearJson.encodeToBytes(
                        ReminderDeliveryResponse.serializer(),
                        ReminderDeliveryResponse(claim = claim, status = ReminderDeliveryStatus.NOT_POSTED),
                    )
                }
            assertEquals(ReminderDeliveryStatus.NOT_POSTED, peer.deliver(request))
        }

    @Test
    fun unrelatedFutureOrOversizedRepliesRemainUncertain() =
        runTest {
            for (response in listOf(
                WearJson.encodeToBytes(
                    ReminderDeliveryResponse.serializer(),
                    ReminderDeliveryResponse(
                        claim = claim.copy(id = "00000000-0000-4000-8000-000000000002"),
                        status = ReminderDeliveryStatus.POSTED,
                    ),
                ),
                WearJson.encodeToBytes(
                    ReminderDeliveryResponse.serializer(),
                    ReminderDeliveryResponse(
                        schema = 2,
                        claim = claim,
                        status = ReminderDeliveryStatus.POSTED,
                    ),
                ),
                ByteArray(8193),
            )) {
                assertNull(WireReminderDeliveryPeer { _, _, _ -> response }.deliver(request))
            }
        }

    @Test
    fun stalledWatchTimesOutAfterTenSeconds() =
        runTest {
            assertNull(WireReminderDeliveryPeer { _, _, _ -> awaitCancellation() }.deliver(request))
            assertEquals(10_000, testScheduler.currentTime)
        }

    @Test
    fun cancellationPropagatesWhileTransportFailureStaysUncertain() =
        runTest {
            assertFailsWith<CancellationException> {
                WireReminderDeliveryPeer { _, _, _ -> throw CancellationException("stop") }.deliver(request)
            }
            assertNull(WireReminderDeliveryPeer { _, _, _ -> error("unreachable") }.deliver(request))
        }
}
