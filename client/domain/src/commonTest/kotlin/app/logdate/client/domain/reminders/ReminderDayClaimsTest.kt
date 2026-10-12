package app.logdate.client.domain.reminders

import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderDayClaimsTest {
    private val storage = FakeKeyValueStorage()
    private val phone = ReminderDayClaims(storage, PeerRole.PHONE)
    private val watch = ReminderDayClaims(FakeKeyValueStorage(), PeerRole.WATCH)
    private val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val phoneClaim = ReminderDayClaim(context, LocalDate(2026, 10, 12), "00000000-0000-4000-8000-000000000001", PeerRole.PHONE)
    private val watchClaim = phoneClaim.copy(id = "00000000-0000-4000-8000-000000000002", role = PeerRole.WATCH)

    @Test
    fun delegatedReservationCanBeConsumedOnceAcrossRestart() =
        runTest {
            assertTrue(phone.grantPeer(watchClaim))
            assertTrue(phone.consumePeer(watchClaim))
            assertFalse(ReminderDayClaims(storage, PeerRole.PHONE).consumePeer(watchClaim))
            assertFalse(phone.grantPeer(watchClaim))
        }

    @Test
    fun unreservedOrDifferentClaimCannotConsumeTheDay() =
        runTest {
            assertFalse(phone.consumePeer(watchClaim))
            assertTrue(phone.grantPeer(watchClaim))
            assertFalse(phone.consumePeer(watchClaim.copy(id = "00000000-0000-4000-8000-000000000003")))
            assertFalse(phone.consumePeer(watchClaim.copy(context = context.copy(linkId = "bad"))))
            assertFalse(phone.consumePeer(phoneClaim))
            assertTrue(phone.consumePeer(watchClaim))
        }

    @Test
    fun concurrentDelegatedDeliveriesConsumeOnlyOnce() =
        runTest {
            assertTrue(phone.grantPeer(watchClaim))
            val results = (1..20).map { async { phone.consumePeer(watchClaim) } }.awaitAll()
            assertEquals(1, results.count { it })
        }

    @Test
    fun pendingLocalClaimCanCommitOnlyOnce() =
        runTest {
            assertTrue(phone.begin(phoneClaim))
            assertTrue(phone.commit(phoneClaim))
            assertFalse(phone.commit(phoneClaim))
            assertFalse(phone.begin(phoneClaim.copy(id = "00000000-0000-4000-8000-000000000003")))
        }

    @Test
    fun simultaneousPendingClaimsChoosePhone() =
        runTest {
            assertTrue(phone.begin(phoneClaim))
            assertTrue(watch.begin(watchClaim))
            assertFalse(phone.grantPeer(watchClaim))
            assertTrue(watch.grantPeer(phoneClaim))
            assertTrue(phone.commit(phoneClaim))
            assertFalse(watch.commit(watchClaim))
        }

    @Test
    fun grantedWatchClaimCannotBePreemptedByPhone() =
        runTest {
            assertTrue(phone.grantPeer(watchClaim))
            assertFalse(phone.begin(phoneClaim))
            assertFalse(phone.grantPeer(watchClaim.copy(id = "00000000-0000-4000-8000-000000000003")))
            assertTrue(phone.grantPeer(watchClaim))
        }

    @Test
    fun grantedClaimSurvivesRestart() =
        runTest {
            assertTrue(phone.grantPeer(watchClaim))
            val restarted = ReminderDayClaims(storage, PeerRole.PHONE)
            assertFalse(restarted.begin(phoneClaim))
            assertTrue(restarted.grantPeer(watchClaim))
        }

    @Test
    fun committedLocalClaimSurvivesRestart() =
        runTest {
            assertTrue(phone.begin(phoneClaim))
            assertTrue(phone.commit(phoneClaim))
            assertFalse(ReminderDayClaims(storage, PeerRole.PHONE).commit(phoneClaim))
        }

    @Test
    fun previousDayReservationDoesNotBlockToday() =
        runTest {
            assertTrue(phone.grantPeer(watchClaim.copy(day = LocalDate(2026, 10, 11))))
            assertTrue(phone.begin(phoneClaim))
            assertTrue(phone.commit(phoneClaim))
        }

    @Test
    fun olderDayDoesNotReplaceTodaysReservation() =
        runTest {
            assertTrue(phone.begin(phoneClaim))
            assertFalse(phone.grantPeer(watchClaim.copy(day = LocalDate(2026, 10, 11))))
            assertTrue(phone.commit(phoneClaim))
        }

    @Test
    fun differentExchangeCannotCommitPendingClaim() =
        runTest {
            assertTrue(phone.begin(phoneClaim))
            assertFalse(phone.commit(phoneClaim.copy(context = context.copy(watchNode = "replacement"))))
            assertTrue(phone.commit(phoneClaim))
        }

    @Test
    fun concurrentLocalTriggersGetOneReservation() =
        runTest {
            val results =
                (1..20)
                    .map { n ->
                        async { phone.begin(phoneClaim.copy(id = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}")) }
                    }.awaitAll()
            assertEquals(1, results.count { it })
        }

    @Test
    fun wrongRoleAndInvalidClaimCannotReserveDay() =
        runTest {
            assertFalse(phone.begin(watchClaim))
            assertFalse(phone.grantPeer(phoneClaim))
            assertFalse(phone.begin(phoneClaim.copy(id = "bad")))
            assertFalse(phone.begin(phoneClaim.copy(context = context.copy(linkId = "bad"))))
            assertTrue(phone.begin(phoneClaim))
        }

    @Test
    fun secondWatchDoesNotGetAnotherPhoneReservation() =
        runTest {
            assertTrue(phone.grantPeer(watchClaim))
            val second =
                phoneClaim.copy(
                    context =
                        context.copy(
                            linkId = LinkWire.encodeKey(ByteArray(32) { 3 }),
                            watchNode = "second-watch",
                        ),
                )
            assertFalse(phone.begin(second))
            assertFalse(phone.grantPeer(second.copy(role = PeerRole.WATCH)))
        }

    @Test
    fun relinkingTheSameAccountDoesNotResetTodaysLimit() =
        runTest {
            val cloud = context.copy(mode = LinkMode.CLOUD, accountId = "account", serverOrigin = "https://api.logdate.app")
            assertTrue(phone.grantPeer(watchClaim.copy(context = cloud)))
            val relinked = cloud.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 4 }), phoneNode = "new-phone", watchNode = "new-watch")
            assertFalse(phone.begin(phoneClaim.copy(context = relinked)))
            assertTrue(phone.begin(phoneClaim.copy(context = relinked, day = LocalDate(2026, 10, 13))))
        }

    @Test
    fun anotherAccountOrServerHasItsOwnBudget() =
        runTest {
            val cloud = context.copy(mode = LinkMode.CLOUD, accountId = "account", serverOrigin = "https://api.logdate.app")
            assertTrue(phone.grantPeer(watchClaim.copy(context = cloud)))
            assertTrue(phone.begin(phoneClaim.copy(context = cloud.copy(accountId = "another"))))
            assertTrue(phone.begin(phoneClaim.copy(context = cloud.copy(serverOrigin = "https://other.example"))))
        }
}
