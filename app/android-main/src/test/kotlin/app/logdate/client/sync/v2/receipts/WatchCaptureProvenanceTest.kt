package app.logdate.client.sync.v2.receipts

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.CreateCollision
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.v2.MemoryPeerDeliveryDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class WatchCaptureProvenanceTest {
    private val dao = MemoryPeerDeliveryDao()
    private val provenance = WatchCaptureProvenance(dao)
    private val policy = WatchCaptureCollisionPolicy(PeerDeliveryLedger(dao))
    private val note =
        JournalNote.Text(
            content = "watch",
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
        )

    @Test
    fun `capture provenance permits a strict create collision without a delivery receipt`() =
        runTest {
            provenance.remember(note)
            assertEquals(CreateCollision.CreatedElsewhere(5), policy.collisionFor(EntityType.NOTE, note.uid.toString()))
        }

    @Test
    fun `an older capture cannot lower the known version`() =
        runTest {
            provenance.remember(note.copy(lastUpdated = Instant.fromEpochMilliseconds(9)))
            provenance.remember(note)
            assertEquals(CreateCollision.CreatedElsewhere(9), policy.collisionFor(EntityType.NOTE, note.uid.toString()))
        }

    @Test
    fun `capture provenance does not authorize another note or record kind`() =
        runTest {
            provenance.remember(note)
            assertEquals(CreateCollision.Unexpected, policy.collisionFor(EntityType.NOTE, "another-note"))
            assertEquals(CreateCollision.Unexpected, policy.collisionFor(EntityType.JOURNAL, note.uid.toString()))
        }
}
