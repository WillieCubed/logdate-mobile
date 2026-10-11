package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.CreateCollision
import app.logdate.client.sync.CreateCollisionPolicy
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes

/**
 * A note this phone received from a watch may already be on the server, because a linked watch backs
 * up a capture itself when the phone has not within 15 minutes. Nothing else this phone uploads was
 * created elsewhere.
 */
class WatchCaptureCollisionPolicy(
    private val ledger: PeerDeliveryLedger,
) : CreateCollisionPolicy {
    override suspend fun collisionFor(
        entityType: EntityType,
        entityId: String,
    ): CreateCollision {
        if (entityType != EntityType.NOTE) return CreateCollision.Unexpected
        val received =
            ledger.get(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, entityId) ?: return CreateCollision.Unexpected
        return CreateCollision.CreatedElsewhere(received.contentVersion)
    }
}
