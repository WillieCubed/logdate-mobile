package app.logdate.client.domain.reminders

import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ReminderClaimWireTest {
    private val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val claim = ReminderDayClaim(context, LocalDate(2026, 10, 12), "00000000-0000-4000-8000-000000000001", PeerRole.WATCH)
    private val coordinator =
        CoordinatedReminderClaims(
            ReminderDayClaims(FakeKeyValueStorage(), PeerRole.PHONE),
            PeerRole.PHONE,
            ReminderClaimPeer { null },
            isCurrent = { it == context },
            enabled = { true },
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-10-12T12:00:00Z")
                },
            timeZone = { TimeZone.UTC },
        )
    private val responder = ReminderClaimResponder(coordinator, PeerRole.PHONE)

    @Test
    fun watchClaimRoundTripReservesPhoneDay() =
        runTest {
            val requester =
                WireReminderClaimPeer(PeerRole.WATCH) { node, path, bytes ->
                    assertEquals("phone", node)
                    assertEquals("/logdate/reminders/claim", path)
                    responder.answer("watch", bytes)
                }
            assertEquals(true, requester.request(claim))
            assertEquals(true, requester.request(claim))
            assertNull(coordinator.tryClaim(context))
        }

    @Test
    fun foreignSourceCannotConsumeTheDailyReservation() =
        runTest {
            assertNull(responder.answer("foreign", request()))
            assertNull(responder.answer("phone", request()))
            val bytes = assertNotNull(responder.answer("watch", request()))
            val response =
                assertIs<Decoded.Supported<ReminderClaimResponse>>(
                    WearJson.decode(bytes, 1, ReminderClaimResponse.serializer()),
                ).value
            assertEquals(claim, response.claim)
            assertTrue(response.granted)
        }

    @Test
    fun futureMalformedOrOversizedRequestCannotReserveDay() =
        runTest {
            for (bytes in listOf(request(schema = 2), "bad".encodeToByteArray(), ByteArray(8_193))) {
                assertNull(responder.answer("watch", bytes))
            }
            assertNotNull(coordinator.tryClaim(context))
        }

    @Test
    fun obsoleteContextGetsAnExplicitRefusal() =
        runTest {
            val obsolete = claim.copy(context = context.copy(watchNode = "replacement"))
            val bytes = assertNotNull(responder.answer("replacement", request(obsolete)))
            val response =
                assertIs<Decoded.Supported<ReminderClaimResponse>>(
                    WearJson.decode(bytes, 1, ReminderClaimResponse.serializer()),
                ).value
            assertFalse(response.granted)
            assertNotNull(coordinator.tryClaim(context))
        }

    @Test
    fun mismatchedOrMalformedReplyIsRefusalRatherThanOfflineFallback() =
        runTest {
            val replies =
                listOf(
                    "bad".encodeToByteArray(),
                    ByteArray(8_193),
                    WearJson.encodeToBytes(
                        ReminderClaimResponse.serializer(),
                        ReminderClaimResponse(
                            claim = claim.copy(id = "00000000-0000-4000-8000-000000000002"),
                            granted = true,
                        ),
                    ),
                    WearJson.encodeToBytes(
                        ReminderClaimResponse.serializer(),
                        ReminderClaimResponse(schema = 2, claim = claim, granted = true),
                    ),
                )
            for (reply in replies) {
                assertEquals(false, WireReminderClaimPeer(PeerRole.WATCH) { _, _, _ -> reply }.request(claim))
            }
        }

    @Test
    fun unavailablePeerAllowsOfflineFallback() =
        runTest {
            assertNull(WireReminderClaimPeer(PeerRole.WATCH) { _, _, _ -> null }.request(claim))
        }

    @Test
    fun phoneRequestTargetsTheExactWatch() =
        runTest {
            val phoneClaim = claim.copy(role = PeerRole.PHONE)
            val requester =
                WireReminderClaimPeer(PeerRole.PHONE) { node, path, bytes ->
                    assertEquals("watch", node)
                    assertEquals("/logdate/reminders/claim", path)
                    val decoded =
                        assertIs<Decoded.Supported<ReminderClaimRequest>>(
                            WearJson.decode(bytes, 1, ReminderClaimRequest.serializer()),
                        ).value
                    assertEquals(phoneClaim, decoded.claim)
                    WearJson.encodeToBytes(ReminderClaimResponse.serializer(), ReminderClaimResponse(claim = phoneClaim, granted = true))
                }
            assertEquals(true, requester.request(phoneClaim))
        }

    private fun request(
        value: ReminderDayClaim = claim,
        schema: Int = 1,
    ): ByteArray = WearJson.encodeToBytes(ReminderClaimRequest.serializer(), ReminderClaimRequest(schema, value))
}
