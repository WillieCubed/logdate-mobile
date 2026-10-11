package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.CreateCollision
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import app.logdate.client.sync.v2.MemoryPeerDeliveryDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests [WatchCaptureCollisionPolicy]: only notes received from a watch may already be on the server. */
class WatchCaptureCollisionPolicyTest {
    private val ledger = PeerDeliveryLedger(MemoryPeerDeliveryDao())
    private val policy = WatchCaptureCollisionPolicy(ledger)

    @Test
    fun `a note received from a watch may have been backed up by it`() =
        runTest {
            ledger.track(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, "a", 5)

            assertEquals(CreateCollision.CreatedElsewhere(5), policy.collisionFor(EntityType.NOTE, "a"))
        }

    @Test
    fun `a note made on the phone was created nowhere else`() =
        runTest {
            assertEquals(CreateCollision.Unexpected, policy.collisionFor(EntityType.NOTE, "b"))
        }

    @Test
    fun `other kinds of record are never a watch's`() =
        runTest {
            ledger.track(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, "a", 5)

            assertEquals(CreateCollision.Unexpected, policy.collisionFor(EntityType.JOURNAL, "a"))
        }
}
