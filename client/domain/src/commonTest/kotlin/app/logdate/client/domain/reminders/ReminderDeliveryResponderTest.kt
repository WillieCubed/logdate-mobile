package app.logdate.client.domain.reminders

import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ReminderDeliveryResponderTest {
    private val storage = FakeKeyValueStorage()
    private val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val claim = ReminderDayClaim(context, LocalDate(2026, 10, 12), "00000000-0000-4000-8000-000000000001", PeerRole.PHONE)
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }
    private var enabled = true
    private var current = true
    private var postsSucceed = true
    private val posts = mutableListOf<Pair<ReminderTrigger, Int>>()
    private val recorded = mutableListOf<Instant>()

    private fun coordinator() =
        CoordinatedReminderClaims(
            ReminderDayClaims(storage, PeerRole.WATCH),
            PeerRole.WATCH,
            ReminderClaimPeer { true },
            isCurrent = { current && it == context },
            enabled = { true },
            clock = clock,
            timeZone = { TimeZone.UTC },
        )

    private fun responder() =
        ReminderDeliveryResponder(
            coordinator(),
            { enabled },
            post = { trigger, marks ->
                posts += trigger to marks
                postsSucceed
            },
            recordPostedAt = { recorded += it },
            clock = clock,
        )

    private fun request(
        value: ReminderDayClaim = claim,
        marks: Int = 3,
        schema: Int = 1,
    ) = WearJson.encodeToBytes(
        ReminderDeliveryRequest.serializer(),
        ReminderDeliveryRequest(schema, value, ReminderTrigger.CAMPFIRE_EMBERS, marks),
    )

    private suspend fun status(responder: ReminderDeliveryResponder = responder()): ReminderDeliveryStatus =
        assertIs<Decoded.Supported<ReminderDeliveryResponse>>(
            WearJson.decode(requireNotNull(responder.answer("phone", request())), 1, ReminderDeliveryResponse.serializer()),
        ).value.also { assertEquals(claim, it.claim) }.status

    @Test
    fun grantedPhoneReminderPostsWithMarksAndRecordsActualPostTime() =
        runTest {
            assertTrue(coordinator().grant(claim))
            assertEquals(ReminderDeliveryStatus.POSTED, status())
            assertEquals(listOf(ReminderTrigger.CAMPFIRE_EMBERS to 3), posts)
            assertEquals(listOf(now), recorded)
        }

    @Test
    fun retryAfterReopeningCannotPostAgain() =
        runTest {
            assertTrue(coordinator().grant(claim))
            assertEquals(ReminderDeliveryStatus.POSTED, status())
            assertEquals(ReminderDeliveryStatus.ALREADY_HANDLED, status(responder()))
            assertEquals(1, posts.size)
            assertEquals(1, recorded.size)
        }

    @Test
    fun concurrentDuplicateRequestsPostOnlyOnce() =
        runTest {
            assertTrue(coordinator().grant(claim))
            val responder = responder()
            val results = (1..10).map { async { status(responder) } }.awaitAll()
            assertEquals(1, results.count { it == ReminderDeliveryStatus.POSTED })
            assertEquals(1, posts.size)
        }

    @Test
    fun notificationRefusalDoesNotInventAPostedTime() =
        runTest {
            assertTrue(coordinator().grant(claim))
            postsSucceed = false
            assertEquals(ReminderDeliveryStatus.NOT_POSTED, status())
            assertEquals(emptyList(), recorded)
            assertEquals(ReminderDeliveryStatus.ALREADY_HANDLED, status())
            assertEquals(1, posts.size)
        }

    @Test
    fun failedPostHistoryWriteCannotAuthorizeARetry() =
        runTest {
            assertTrue(coordinator().grant(claim))
            val responder =
                ReminderDeliveryResponder(
                    coordinator(),
                    { true },
                    post = { trigger, marks ->
                        posts += trigger to marks
                        true
                    },
                    recordPostedAt = { error("storage unavailable") },
                    clock = clock,
                )
            assertFailsWith<IllegalStateException> { responder.answer("phone", request()) }
            assertEquals(ReminderDeliveryStatus.ALREADY_HANDLED, status())
            assertEquals(1, posts.size)
        }

    @Test
    fun noReservationCannotAuthorizeNotification() =
        runTest {
            assertEquals(ReminderDeliveryStatus.ALREADY_HANDLED, status())
            assertEquals(emptyList(), posts)
        }

    @Test
    fun optOutDoesNotConsumeReservedDayOrPost() =
        runTest {
            assertTrue(coordinator().grant(claim))
            enabled = false
            assertNull(responder().answer("phone", request()))
            assertEquals(emptyList(), posts)
            enabled = true
            assertEquals(ReminderDeliveryStatus.POSTED, status())
        }

    @Test
    fun revokedLinkForeignSourceAndExpiredDayCannotPost() =
        runTest {
            assertTrue(coordinator().grant(claim))
            val responder = responder()
            assertNull(responder.answer("foreign", request()))
            assertNull(responder.answer("phone", request(claim.copy(day = LocalDate(2026, 10, 11)))))
            assertNull(responder.answer("phone", request(claim.copy(role = PeerRole.WATCH))))
            current = false
            assertNull(responder.answer("phone", request()))
            assertEquals(emptyList(), posts)
        }

    @Test
    fun invalidPayloadsCannotConsumeOrPost() =
        runTest {
            assertTrue(coordinator().grant(claim))
            val responder = responder()
            for (bytes in listOf(ByteArray(8193), "bad".encodeToByteArray(), request(schema = 2), request(marks = -1))) {
                assertNull(responder.answer("phone", bytes))
            }
            assertEquals(emptyList(), posts)
            assertEquals(ReminderDeliveryStatus.POSTED, status())
        }
}
