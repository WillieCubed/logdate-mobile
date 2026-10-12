package app.logdate.wear.sync.v2.window

import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.conflict.MarkedMomentConflictResolver
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WindowMemberRef
import app.logdate.client.sync.datalayer.v2.WindowResyncResponse
import app.logdate.client.sync.datalayer.v2.WindowTombstoneDataMap
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.MomentEnvelopeBody
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MomentWindowApplierTest {
    private val f = WindowFixture()
    private val mark =
        MarkedMoment(
            f.note.uid,
            Instant.fromEpochMilliseconds(1),
            "UTC",
            null,
            null,
            MarkState.OPEN,
            "",
            emptySet(),
            "watch",
            Instant.fromEpochMilliseconds(5),
            0,
        )
    private val stored = mutableMapOf<Uuid, MarkedMoment>()
    private var pending: PendingUpload? = null
    private var writes = 0
    private var supported = true
    private var afterApply: () -> Unit = {}
    private val outbox =
        mockk<SyncMetadataService> {
            coEvery { getPendingUploads(EntityType.MOMENT) } answers { listOfNotNull(pending) }
            coEvery { hasPending(EntityType.MOMENT, any()) } answers { pending != null }
            coEvery { enqueuePending(any(), EntityType.MOMENT, any()) } answers {
                writes++
                pending = PendingUpload(firstArg(), thirdArg(), operationId = "updated-$writes")
            }
        }
    private val repository =
        mockk<SyncableMarkedMomentRepository> {
            coEvery { getMark(any()) } answers { stored[firstArg()] }
            coEvery { applyFromSync(any()) } answers {
                val incoming = firstArg<MarkedMoment>()
                val merged = stored[incoming.id]?.let { MarkedMomentConflictResolver().merge(it, incoming) } ?: incoming
                stored[incoming.id] = merged
                afterApply()
                merged
            }
        }
    private val momentMembers = WindowMembershipStore(f.storage, RecordKind.MOMENT)
    private val transactions =
        object : SyncTransactionManager {
            override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
        }
    private val moments = MomentWindowApplier(f.peers, repository, outbox, momentMembers, transactions, { supported })
    private val combined = WindowApplier(f.peers, f.notes, f.outbox, f.members, f.transactions, {}, momentWindow = moments)

    private fun ref(
        value: MarkedMoment = mark,
        revision: Long = 1,
    ) = WindowMemberRef(RecordKind.MOMENT, value.id.toString(), value.lastUpdated.toEpochMilliseconds(), revision)

    private fun response(
        value: MarkedMoment = mark,
        revision: Long = 1,
    ) = WindowResyncResponse(context = f.peer, members = listOf(ref(value, revision)), publishedAt = 9, revision = revision)

    private fun item(
        value: MarkedMoment = mark,
        revision: Long = 1,
    ): WindowItem =
        WindowItem(
            "phone",
            WearPaths.windowItem(RecordKind.MOMENT, value.id, f.peer),
            EnvelopeDataMap.toDataMap(MomentEnvelopeBody.envelope(value, RecordOrigin.PHONE).copy(context = f.peer), 1) +
                (WindowTombstoneDataMap.KEY_REVISION to revision.toString()),
        )

    @Test fun returnedResolutionMergesSensorAndAttachmentsWithoutClearingPendingCapture() =
        runTest {
            stored[mark.id] = mark.copy(heartRateBpm = 72, resolvedNoteIds = setOf(Uuid.random()))
            val original = PendingUpload(mark.id.toString(), PendingOperation.CREATE, operationId = "old")
            pending = original
            val remote =
                mark.copy(
                    heartRateBpm = 72,
                    state = MarkState.FILLED,
                    caption = "resolved",
                    resolvedNoteIds = setOf(Uuid.random()),
                    lastUpdated = Instant.fromEpochMilliseconds(6),
                    syncVersion = 7,
                )
            assertEquals(WindowApplied(1, 0), combined.reconcile(f.peer, response(remote), listOf(item(remote))))
            val merged = stored.getValue(mark.id)
            assertEquals(MarkState.FILLED, merged.state)
            assertEquals("resolved", merged.caption)
            assertEquals(2, merged.resolvedNoteIds.size)
            assertNotNull(pending)
            assertTrue(pending!!.operationId != original.operationId)
            assertEquals(PendingOperation.CREATE, pending!!.operation)
            assertEquals(7, merged.syncVersion)
            assertTrue(
                f.members
                    .read(f.peer)
                    .entries
                    .isEmpty(),
            )
            assertEquals(1, momentMembers.read(f.peer).entries.size)
        }

    @Test fun incompleteMomentCannotAuthorizePruningAnExistingNote() =
        runTest {
            f.seed()
            assertNull(combined.reconcile(f.peer, response(revision = 2), emptyList()))
            assertEquals(f.note, f.stored[f.note.uid])
            assertEquals(1, f.members.read(f.peer).snapshotRevision)
        }

    @Test fun sameIdNoteAndMomentHaveIndependentMembershipAndPersistence() =
        runTest {
            val snapshot = f.response().copy(members = f.response().members + ref())
            assertEquals(WindowApplied(2, 0), combined.reconcile(f.peer, snapshot, listOf(f.item(), item())))
            assertEquals(f.note, f.stored[f.note.uid])
            assertEquals(mark, stored[mark.id])
            assertEquals(
                1,
                f.members
                    .read(f.peer)
                    .entries.size,
            )
            assertEquals(1, momentMembers.read(f.peer).entries.size)
        }

    @Test fun foreignMismatchedUnknownAndUnsupportedMembersNeverAdvanceTheSnapshot() =
        runTest {
            val valid = item()
            val envelope = (EnvelopeDataMap.fromDataMap(valid.data) as Decoded.Supported).value
            val bad =
                listOf(
                    valid.copy(sourceNodeId = "foreign"),
                    valid.copy(path = f.item().path),
                    valid.copy(data = EnvelopeDataMap.toDataMap(envelope.copy(context = f.peer.copy(phoneNode = "foreign")), 1)),
                    valid.copy(
                        data = EnvelopeDataMap.toDataMap(envelope.copy(body = kotlinx.serialization.json.JsonPrimitive("future")), 1),
                    ),
                )
            for (copy in bad) assertNull(combined.reconcile(f.peer, response(), listOf(copy)))
            supported = false
            assertNull(combined.reconcile(f.peer, response(), listOf(valid)))
            assertTrue(stored.isEmpty())
        }

    @Test fun windowRetirementPreservesTheDurableMarkAndPendingWorkAndBlocksOldReplay() =
        runTest {
            assertNotNull(combined.reconcile(f.peer, response(), listOf(item())))
            pending = PendingUpload(mark.id.toString(), PendingOperation.UPDATE, operationId = "sensor")
            val removal =
                WindowItem(
                    "phone",
                    WearPaths.windowTombstone(RecordKind.MOMENT, mark.id, f.peer),
                    WindowTombstoneDataMap.toDataMap(9, f.peer, 2),
                )
            assertTrue(combined.onTombstone(removal))
            assertEquals(mark, stored[mark.id])
            assertNotNull(pending)
            assertFalse(combined.onItem(item()))
            assertTrue(momentMembers.read(f.peer).entries.isEmpty())
        }

    @Test fun capabilityWithdrawalDuringMergeCannotCompleteOrPruneTheSnapshot() =
        runTest {
            f.seed()
            afterApply = { supported = false }
            assertNull(combined.reconcile(f.peer, response(revision = 2), listOf(item(revision = 2))))
            assertEquals(f.note, f.stored[f.note.uid])
            assertNull(momentMembers.read(f.peer).snapshotRevision)
        }

    @Test fun repeatedMergedProjectionKeepsTheFreshPendingIdentityAndLocalContributionsReturnToPhone() =
        runTest {
            stored[mark.id] = mark.copy(state = MarkState.FILLED, caption = "watch resolution", resolvedNoteIds = setOf(Uuid.random()))
            assertNotNull(combined.reconcile(f.peer, response(), listOf(item())))
            assertEquals(PendingOperation.UPDATE, pending?.operation)
            val first = pending
            assertNotNull(combined.reconcile(f.peer, response(), listOf(item())))
            assertEquals(first, pending)
            assertEquals(1, writes)
            assertEquals(MarkState.FILLED, stored[mark.id]?.state)
        }

    @Test fun localDeletionCannotBeRevivedByPhoneResolution() =
        runTest {
            pending = PendingUpload(mark.id.toString(), PendingOperation.DELETE, operationId = "delete")
            assertNotNull(combined.reconcile(f.peer, response(), listOf(item())))
            assertTrue(stored.isEmpty())
            assertEquals("delete", pending?.operationId)
        }
}
