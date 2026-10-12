package app.logdate.client.domain.reminders

import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CoordinatedReminderClaimsTest {
    private val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-10-12T12:00:00Z")
        }
    private var active = true
    private var current = true
    private val phoneStore = FakeKeyValueStorage()

    private fun coordinator(
        role: PeerRole = PeerRole.PHONE,
        storage: FakeKeyValueStorage = phoneStore,
        peer: ReminderClaimPeer = ReminderClaimPeer { null },
    ) = CoordinatedReminderClaims(
        ReminderDayClaims(storage, role),
        role,
        peer,
        isCurrent = { current && it == context },
        enabled = { active },
        clock = clock,
        timeZone = { TimeZone.UTC },
        dayStartHour = { 4 },
    )

    @Test
    fun standalonePostWhilePeerAnswersInvalidatesThePendingClaim() =
        runTest {
            var available = true
            val claims =
                CoordinatedReminderClaims(
                    ReminderDayClaims(phoneStore, PeerRole.PHONE),
                    PeerRole.PHONE,
                    ReminderClaimPeer {
                        available = false
                        true
                    },
                    isCurrent = { true },
                    enabled = { true },
                    available = { available },
                    clock = clock,
                    timeZone = { TimeZone.UTC },
                )
            assertNull(claims.tryClaim(context))
        }

    @Test
    fun alreadyUsedDeviceBudgetRefusesLocalAndIncomingClaims() =
        runTest {
            val claims =
                CoordinatedReminderClaims(
                    ReminderDayClaims(phoneStore, PeerRole.PHONE),
                    PeerRole.PHONE,
                    ReminderClaimPeer { error("No transport should be started") },
                    isCurrent = { true },
                    enabled = { true },
                    available = { false },
                    clock = clock,
                    timeZone = { TimeZone.UTC },
                )
            assertNull(claims.tryClaim(context))
            val incoming =
                ReminderDayClaim(
                    context,
                    LocalDate(2026, 10, 12),
                    "00000000-0000-4000-8000-000000000001",
                    PeerRole.WATCH,
                )
            assertFalse(claims.grant(incoming))
        }

    @Test
    fun offlineClaimReservesTheDayAcrossRestart() =
        runTest {
            assertNotNull(coordinator().tryClaim(context))
            assertNull(coordinator().tryClaim(context))
        }

    @Test
    fun reachablePeerRefusalPreventsDelivery() =
        runTest {
            assertNull(coordinator(peer = ReminderClaimPeer { false }).tryClaim(context))
        }

    @Test
    fun simultaneousPhoneAndWatchClaimsProduceOneWinner() =
        runTest {
            val bothPending = CompletableDeferred<Unit>()
            var requests = 0
            lateinit var phone: CoordinatedReminderClaims
            lateinit var watch: CoordinatedReminderClaims

            suspend fun request(claim: ReminderDayClaim): Boolean {
                requests++
                if (requests == 2) bothPending.complete(Unit)
                bothPending.await()
                return if (claim.role == PeerRole.PHONE) watch.grant(claim) else phone.grant(claim)
            }
            phone = coordinator(peer = ReminderClaimPeer { request(it) })
            watch = coordinator(PeerRole.WATCH, FakeKeyValueStorage(), ReminderClaimPeer { request(it) })
            val results = listOf(async { phone.tryClaim(context) }, async { watch.tryClaim(context) }).awaitAll()
            assertEquals(listOf(PeerRole.PHONE), results.filterNotNull().map { it.role })
            assertNull(phone.tryClaim(context))
            assertNull(watch.tryClaim(context))
        }

    @Test
    fun watchThatClaimsFirstKeepsItsGrantedDay() =
        runTest {
            val phone = coordinator()
            val watch = coordinator(PeerRole.WATCH, FakeKeyValueStorage(), ReminderClaimPeer { phone.grant(it) })
            assertNotNull(watch.tryClaim(context))
            assertNull(phone.tryClaim(context))
        }

    @Test
    fun staleOrDisabledPeerCannotReserveTheDay() =
        runTest {
            val phone = coordinator()
            current = false
            assertNull(phone.tryClaim(context))
            current = true
            active = false
            assertNull(phone.tryClaim(context))
            active = true
            assertNotNull(phone.tryClaim(context))
        }

    @Test
    fun accountChangeDuringRequestDiscardsGrant() =
        runTest {
            val phone =
                coordinator(
                    peer =
                        ReminderClaimPeer {
                            current = false
                            true
                        },
                )
            assertNull(phone.tryClaim(context))
        }

    @Test
    fun oldOrFutureDayCannotReserveToday() =
        runTest {
            val phone = coordinator()
            val claim = ReminderDayClaim(context, LocalDate(2026, 10, 11), "00000000-0000-4000-8000-000000000001", PeerRole.WATCH)
            assertFalse(phone.grant(claim))
            assertFalse(phone.grant(claim.copy(day = LocalDate(2026, 10, 13))))
            assertTrue(phone.grant(claim.copy(day = LocalDate(2026, 10, 12))))
        }

    @Test
    fun cancellingPeerRequestDoesNotCommitAClaim() =
        runTest {
            val phone =
                coordinator(
                    peer =
                        ReminderClaimPeer {
                            delay(10_000)
                            true
                        },
                )
            val delivery = async { phone.tryClaim(context) }
            runCurrent()
            delivery.cancel()
            delivery.join()
            assertTrue(delivery.isCancelled)
        }

    @Test
    fun unreachablePeerTimesOutBeforeLocalFallback() =
        runTest {
            val phone =
                coordinator(
                    peer =
                        ReminderClaimPeer {
                            delay(10_000)
                            true
                        },
                )
            assertNotNull(phone.tryClaim(context))
            assertEquals(5_000, testScheduler.currentTime)
        }
}
