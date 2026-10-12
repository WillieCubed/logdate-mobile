package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.link.LinkWire
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleasedToCloudTest {
    private val peer =
        PeerDeliveryContext(
            LinkWire.encodeKey(ByteArray(32) { 1 }),
            "phone",
            "watch",
            LinkMode.CLOUD,
            "account",
            "https://cloud.test",
        )
    private val pending =
        PendingUpload(
            "note",
            PendingOperation.CREATE,
            scope = UploadScope("account", "https://cloud.test"),
            operationId = "operation",
        )
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val peers = mockk<WatchDeliveryPeer>()
    private val outbox = mockk<SyncMetadataService>()
    private var currentPeer: PeerDeliveryContext? = peer
    private var currentOperation = pending
    private val released = ReleasedToCloud(ledger, peers, outbox)

    init {
        coEvery { peers.isCurrent(any()) } answers { firstArg<PeerDeliveryContext>() == currentPeer }
        coEvery { outbox.isCurrentOperation(any(), any()) } answers {
            firstArg<EntityType>() == EntityType.NOTE && secondArg<PendingUpload>().operationId == currentOperation.operationId
        }
    }

    @Test
    fun `a ledger release by itself never opens the cloud outbox`() =
        runTest {
            markReleased()
            assertFalse(released.isEligible(EntityType.NOTE, pending))
        }

    @Test
    fun `the captured create is eligible only during its release run`() =
        runTest {
            markReleased()
            released.withCaptures(peer, listOf(CloudCapture(pending, 5))) {
                assertTrue(released.isEligible(EntityType.NOTE, pending))
            }
            assertFalse(released.isEligible(EntityType.NOTE, pending))
        }

    @Test
    fun `a changed operation for the same note is never released`() =
        runTest {
            markReleased()
            released.withCaptures(peer, listOf(CloudCapture(pending, 5))) {
                currentOperation = pending.copy(operationId = "new-operation")
                assertFalse(released.isEligible(EntityType.NOTE, currentOperation))
                assertFalse(released.isEligible(EntityType.NOTE, pending))
            }
        }

    @Test
    fun `an edit cannot use direct capture backup`() =
        runTest {
            markReleased()
            val edit = pending.copy(operation = PendingOperation.UPDATE)
            released.withCaptures(peer, listOf(CloudCapture(edit, 5))) {
                assertFalse(released.isEligible(EntityType.NOTE, edit))
            }
        }

    @Test
    fun `a deletion can be released without admitting other entity types`() =
        runTest {
            markReleased("note@operation:remove", 0)
            val deletion = pending.copy(operation = PendingOperation.DELETE)
            released.withCaptures(peer, listOf(CloudCapture(deletion, 0))) {
                assertTrue(released.isEligible(EntityType.NOTE, deletion))
                assertFalse(released.isEligible(EntityType.JOURNAL, deletion))
            }
        }

    @Test
    fun `a receipt that settles the release closes cloud eligibility`() =
        runTest {
            markReleased()
            released.withCaptures(peer, listOf(CloudCapture(pending, 5))) {
                ledger.applyReceipt(
                    peer.ledgerScope,
                    DeliveryDirection.TO_PHONE,
                    EntityType.NOTE,
                    "note@operation",
                    5,
                    DeliveryState.BACKED_UP,
                )
                assertFalse(released.isEligible(EntityType.NOTE, pending))
            }
        }

    @Test
    fun `a newer ledger version is not the captured release`() =
        runTest {
            markReleased()
            released.withCaptures(peer, listOf(CloudCapture(pending, 5))) {
                val newer = ledger.track(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, "note@operation", 6)
                ledger.moveTo(newer, DeliveryState.CLOUD_PENDING)
                assertFalse(released.isEligible(EntityType.NOTE, pending))
            }
        }

    @Test
    fun `renewing the link closes its old release`() =
        runTest {
            markReleased()
            released.withCaptures(peer, listOf(CloudCapture(pending, 5))) {
                currentPeer = peer.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
                assertFalse(released.isEligible(EntityType.NOTE, pending))
            }
        }

    @Test
    fun `another account or backend is never admitted`() =
        runTest {
            markReleased()
            for (scope in listOf(UploadScope("other", "https://cloud.test"), UploadScope("account", "https://other.test"))) {
                val foreign = pending.copy(scope = scope)
                released.withCaptures(peer, listOf(CloudCapture(foreign, 5))) {
                    assertFalse(released.isEligible(EntityType.NOTE, foreign))
                }
            }
        }

    @Test
    fun `an unscoped operation cannot be released`() =
        runTest {
            markReleased()
            for (unbound in listOf(pending.copy(scope = null), pending.copy(operationId = null))) {
                released.withCaptures(peer, listOf(CloudCapture(unbound, 5))) {
                    assertFalse(released.isEligible(EntityType.NOTE, unbound))
                }
            }
        }

    @Test
    fun `cancellation closes the run's release`() =
        runTest {
            markReleased()
            assertFailsWith<CancellationException> {
                released.withCaptures(peer, listOf(CloudCapture(pending, 5))) {
                    assertTrue(released.isEligible(EntityType.NOTE, pending))
                    throw CancellationException("stopped")
                }
            }
            assertFalse(released.isEligible(EntityType.NOTE, pending))
        }

    @Test fun momentReleaseIsTypedAndCannotReleaseANoteWithTheSameOperation() =
        runTest {
            coEvery { outbox.isCurrentOperation(EntityType.MOMENT, pending) } returns true
            val row = ledger.track(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.MOMENT, "note@operation", 5)
            ledger.moveTo(row, DeliveryState.CLOUD_PENDING)
            markReleased()
            released.withCaptures(peer, listOf(CloudCapture(pending, 5, EntityType.MOMENT))) {
                assertTrue(released.isEligible(EntityType.MOMENT, pending))
                assertFalse(released.isEligible(EntityType.NOTE, pending))
            }
            released.withCaptures(peer, listOf(CloudCapture(pending, 5))) {
                assertFalse(released.isEligible(EntityType.MOMENT, pending))
            }
        }

    @Test fun momentDeletionCanBeReleasedButMomentEditsStayHeld() =
        runTest {
            for (operation in listOf(PendingOperation.DELETE, PendingOperation.UPDATE)) {
                val work = pending.copy(operation = operation)
                coEvery { outbox.isCurrentOperation(EntityType.MOMENT, work) } returns true
                val key = if (operation == PendingOperation.DELETE) "note@operation:remove" else "note@operation"
                val version = if (operation == PendingOperation.DELETE) 0L else 5L
                val row = ledger.track(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.MOMENT, key, version)
                ledger.moveTo(row, DeliveryState.CLOUD_PENDING)
                released.withCaptures(peer, listOf(CloudCapture(work, version, EntityType.MOMENT))) {
                    if (operation == PendingOperation.DELETE) {
                        assertTrue(released.isEligible(EntityType.MOMENT, work))
                    } else {
                        assertFalse(released.isEligible(EntityType.MOMENT, work))
                    }
                }
            }
        }

    private suspend fun markReleased(
        key: String = "note@operation",
        version: Long = 5,
    ) {
        val row = ledger.track(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, key, version)
        ledger.moveTo(row, DeliveryState.CLOUD_PENDING)
    }
}
