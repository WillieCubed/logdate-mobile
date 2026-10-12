package app.logdate.client.sync.moments

import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.repository.moments.atMillisecondPrecision
import app.logdate.client.sync.conflict.MarkedMomentConflictResolver
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.WireDownload
import app.logdate.client.sync.test.FakeSyncMetadataService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MomentRemoteApplierTest {
    private val db = DownloadInboxTest.Database()
    private val originalScope = DownloadScope("owner", "server")
    private var scope = originalScope
    private val inbox = DownloadInbox(db, db, { scope }, { 1000 })
    private val metadata = FakeSyncMetadataService(trackOperationIdentity = true)
    private val marks = Marks()
    private val applier = MomentRemoteApplier(marks, metadata, db, inbox)
    private val id = Uuid.parse("10000000-0000-0000-0000-000000000001")
    private val noteA = Uuid.parse("10000000-0000-0000-0000-000000000002")
    private val noteB = Uuid.parse("10000000-0000-0000-0000-000000000003")
    private val at = Instant.fromEpochMilliseconds(100)

    private fun mark(
        state: MarkState = MarkState.OPEN,
        notes: Set<Uuid> = emptySet(),
    ) = MarkedMoment(id, at, "UTC", null, 72, state, "Capture", notes, "watch", at, 7)

    private suspend fun stage(remote: MarkedMoment) =
        inbox.stage("MOMENT", remote.syncVersion, listOf(WireDownload(id.toString(), remote.syncVersion, false, "LDSE2:retained")))

    @Test
    fun `merge retains the queued operation and combines resolved note attachments`() =
        runTest {
            marks.current = mark(MarkState.FILLED, setOf(noteA)).copy(syncVersion = 6)
            metadata.enqueuePending(id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
            val pending = metadata.getPendingUploads(EntityType.MOMENT).single()
            val remote = mark(MarkState.FILLED, setOf(noteB))
            stage(remote)
            assertTrue(applier.apply(remote, scope))
            assertEquals(setOf(noteA, noteB), marks.current!!.resolvedNoteIds)
            assertEquals(MarkState.FILLED, marks.current!!.state)
            assertEquals(pending, metadata.getPendingUploads(EntityType.MOMENT).single())
            assertTrue(inbox.isAppliedVersion("MOMENT", id.toString(), 7))
        }

    @Test
    fun `accepted remote snapshot does not queue an echo upload`() =
        runTest {
            val remote = mark(MarkState.FILLED, setOf(noteB))
            stage(remote)
            assertTrue(applier.apply(remote, scope))
            assertEquals(remote, marks.current)
            assertTrue(metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
            assertTrue(inbox.pending("MOMENT").isEmpty())
        }

    @Test
    fun `surviving local resolution queues a return upload when no operation exists`() =
        runTest {
            marks.current = mark(MarkState.FILLED, setOf(noteA)).copy(syncVersion = 6)
            val remote = mark(MarkState.DISMISSED)
            stage(remote)
            assertTrue(applier.apply(remote, scope))
            assertEquals(MarkState.FILLED, marks.current!!.state)
            assertEquals(PendingOperation.UPDATE, metadata.getPendingUploads(EntityType.MOMENT).single().operation)
        }

    @Test
    fun `wire nanoseconds do not invent a local contribution or conflict with Room capture identity`() =
        runTest {
            val precise =
                mark().copy(
                    capturedAt = Instant.parse("1970-01-01T00:00:00.100123456Z"),
                    lastUpdated = Instant.parse("1970-01-01T00:00:00.100987654Z"),
                )
            marks.current = precise.atMillisecondPrecision()
            stage(precise)
            assertTrue(applier.apply(precise, scope))
            assertEquals(precise.atMillisecondPrecision(), marks.current)
            assertTrue(metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
        }

    @Test
    fun `newer retained version prevents applying or acknowledging an older page`() =
        runTest {
            val remote = mark()
            stage(remote.copy(syncVersion = 8))
            assertFalse(applier.apply(remote, scope))
            assertEquals(null, marks.current)
            assertEquals(8L, inbox.pending("MOMENT").single().version)
        }

    @Test
    fun `rejected capture stays retained and does not clear local deletion`() =
        runTest {
            marks.reject = true
            metadata.enqueuePending(id.toString(), EntityType.MOMENT, PendingOperation.DELETE)
            val pending = metadata.getPendingUploads(EntityType.MOMENT).single()
            val remote = mark()
            stage(remote)
            assertFalse(applier.apply(remote, scope))
            assertEquals(pending, metadata.getPendingUploads(EntityType.MOMENT).single())
            assertFalse(inbox.isAppliedVersion("MOMENT", id.toString(), 7))
            assertEquals("LDSE2:retained", db.get(scope.owner, scope.origin, "MOMENT", id.toString())!!.payload)
        }

    @Test
    fun `foreign account cannot apply a previously fetched mark`() =
        runTest {
            val remote = mark()
            stage(remote)
            scope = DownloadScope("other-owner", "server")
            assertFailsWith<IllegalStateException> { applier.apply(remote, originalScope) }
            assertEquals(null, marks.current)
            assertFalse(inbox.isAppliedVersion("MOMENT", id.toString(), 7, originalScope))
        }

    @Test
    fun `cancellation during repository apply leaves the encrypted record unacknowledged`() =
        runTest {
            val remote = mark()
            stage(remote)
            marks.beforeApply = { throw CancellationException("interrupted") }
            assertFailsWith<CancellationException> { applier.apply(remote, scope) }
            assertEquals("WAITING", inbox.pending("MOMENT").single().state)
            assertTrue(metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
        }

    @Test
    fun `remote deletion before capture writes a tombstone and acknowledges without queuing an upload`() =
        runTest {
            inbox.stage("MOMENT", 9, listOf(WireDownload(id.toString(), 9, true, "retained-deletion")))
            assertTrue(applier.delete(id, at, 9, scope))
            assertEquals(Triple(id, at, 9L), marks.deletion)
            assertTrue(inbox.pending("MOMENT").isEmpty())
            assertTrue(metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
        }

    @Test
    fun `remote deletion preserves the queued local operation`() =
        runTest {
            metadata.enqueuePending(id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
            val pending = metadata.getPendingUploads(EntityType.MOMENT).single()
            inbox.stage("MOMENT", 9, listOf(WireDownload(id.toString(), 9, true, "retained-deletion")))
            assertTrue(applier.delete(id, at, 9, scope))
            assertEquals(pending, metadata.getPendingUploads(EntityType.MOMENT).single())
        }

    @Test
    fun `a live retained row cannot be acknowledged as deletion with the same version`() =
        runTest {
            stage(mark())
            assertFalse(applier.delete(id, at, 7, scope))
            assertEquals(null, marks.deletion)
            assertEquals(1, inbox.pending("MOMENT").size)
        }

    @Test
    fun `a replaced deletion or foreign scope cannot erase a mark`() =
        runTest {
            inbox.stage("MOMENT", 9, listOf(WireDownload(id.toString(), 9, true, "retained-deletion")))
            assertFalse(applier.delete(id, at, 8, scope))
            scope = DownloadScope("other-owner", "server")
            assertFailsWith<IllegalStateException> { applier.delete(id, at, 9, originalScope) }
            assertEquals(null, marks.deletion)
        }

    private class Marks : SyncableMarkedMomentRepository {
        var current: MarkedMoment? = null
        var reject = false
        var deletion: Triple<Uuid, Instant, Long>? = null
        var beforeApply: suspend () -> Unit = {}

        override fun observeOpenMarks() = flowOf(listOfNotNull(current).filter { it.state == MarkState.OPEN })

        override suspend fun getMark(id: Uuid) = current?.takeIf { it.id == id }

        override suspend fun applyFromSync(moment: MarkedMoment): MarkedMoment? {
            beforeApply()
            if (reject) return null
            val merged = current?.let { MarkedMomentConflictResolver().merge(it, moment) } ?: moment
            current = merged.atMillisecondPrecision()
            return current
        }

        override suspend fun applyDeletionFromSync(
            id: Uuid,
            deletedAt: Instant,
            serverVersion: Long,
        ) {
            deletion = Triple(id, deletedAt, serverVersion)
            current = null
        }

        override suspend fun dismiss(
            id: Uuid,
            updatedAt: Instant,
        ): Boolean = error("Unused")

        override suspend fun fill(
            id: Uuid,
            caption: String,
            noteIds: Set<Uuid>,
            updatedAt: Instant,
        ): Boolean = error("Unused")

        override suspend fun delete(
            id: Uuid,
            deletedAt: Instant,
        ): Boolean = error("Unused")

        override suspend fun fillLocationIfMissing(
            id: Uuid,
            location: MarkLocation,
            updatedAt: Instant,
        ): Boolean = error("Unused")
    }
}
