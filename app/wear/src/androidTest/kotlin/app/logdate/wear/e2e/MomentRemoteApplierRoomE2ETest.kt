package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.dao.sync.DownloadInboxDao
import app.logdate.client.database.entities.sync.DownloadInboxEntity
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.repository.moments.atMillisecondPrecision
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.moments.MomentRemoteApplier
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.recovery.WireDownload
import app.logdate.wear.di.WatchCloud
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class MomentRemoteApplierRoomE2ETest {
    private val koin = KoinJavaComponent.getKoin()
    private val database = koin.get<LogDateDatabase>()
    private val transactions = koin.get<SyncTransactionManager>(WatchCloud)
    private val repository = koin.get<SyncableMarkedMomentRepository>()
    private val metadata = koin.get<SyncMetadataService>()
    private val selected = DownloadScope("moment-apply-test-${Uuid.random()}", "isolated-server")
    private val at = Instant.fromEpochMilliseconds(100)

    private fun mark() = MarkedMoment(Uuid.random(), at, "UTC", null, 72, MarkState.OPEN, "Capture", emptySet(), "test-watch", at, 7)

    private fun inbox(
        dao: DownloadInboxDao = database.downloadInboxDao(),
        scope: () -> DownloadScope = { selected },
    ) = DownloadInbox(dao, transactions, scope, { 1000 })

    private suspend fun stage(
        inbox: DownloadInbox,
        mark: MarkedMoment,
    ) = inbox.stage("MOMENT", mark.syncVersion, listOf(WireDownload(mark.id.toString(), mark.syncVersion, false, "LDSE2:retained-private")))

    @Test fun installedRoomMergeRetainsQueuedResolutionAndAcknowledgesItsExactVersion() =
        runBlocking {
            val incoming = mark()
            repository.applyFromSync(incoming.copy(syncVersion = 6))
            val localNote = Uuid.random()
            val remoteNote = Uuid.random()
            repository.fill(incoming.id, "Filled", setOf(localNote), at)
            val pending = metadata.getPendingUploads(EntityType.MOMENT).single { it.entityId == incoming.id.toString() }
            val remote = incoming.copy(state = MarkState.FILLED, resolvedNoteIds = setOf(remoteNote))
            val inbox = inbox()
            stage(inbox, remote)
            assertTrue(MomentRemoteApplier(repository, metadata, transactions, inbox).apply(remote, selected))
            assertEquals(setOf(localNote, remoteNote), repository.getMark(remote.id)!!.resolvedNoteIds)
            assertEquals(pending, metadata.getPendingUploads(EntityType.MOMENT).single { it.entityId == remote.id.toString() })
            assertTrue(inbox.isAppliedVersion("MOMENT", remote.id.toString(), 7))
        }

    @Test fun failedReceiptRollsBackTheMergedRowAndNewReturnUploadTogether() =
        runBlocking {
            val incoming = mark()
            val local = incoming.copy(state = MarkState.FILLED, resolvedNoteIds = setOf(Uuid.random()), syncVersion = 6)
            repository.applyFromSync(local)
            assertFalse(metadata.hasPending(EntityType.MOMENT, incoming.id.toString()))
            val dao = database.downloadInboxDao()
            val interrupted =
                object : DownloadInboxDao by dao {
                    override suspend fun put(row: DownloadInboxEntity) {
                        dao.put(row)
                        if (row.state == "APPLIED") throw CancellationException("interrupted after receipt write")
                    }
                }
            val inbox = inbox(interrupted)
            stage(inbox, incoming)
            assertFailsWith<CancellationException> {
                MomentRemoteApplier(
                    repository,
                    metadata,
                    transactions,
                    inbox,
                ).apply(incoming, selected)
            }
            assertEquals(local, repository.getMark(incoming.id))
            assertFalse(metadata.hasPending(EntityType.MOMENT, incoming.id.toString()))
            val retained = dao.get(selected.owner, selected.origin, "MOMENT", incoming.id.toString())!!
            assertEquals("WAITING", retained.state)
            assertEquals("LDSE2:retained-private", retained.payload)
            assertEquals(7L, inbox.cursor("MOMENT"))
        }

    @Test fun scopeChangeDuringApplyRollsBackTheRowWithoutAcknowledgingTheOldAccount() =
        runBlocking {
            val incoming = mark()
            var current = selected
            val switching =
                object : SyncableMarkedMomentRepository by repository {
                    override suspend fun applyFromSync(moment: MarkedMoment): MarkedMoment? {
                        val result = repository.applyFromSync(moment)
                        current = DownloadScope("different-owner", selected.origin)
                        return result
                    }
                }
            val inbox = inbox(scope = { current })
            stage(inbox, incoming)
            assertFailsWith<IllegalStateException> {
                MomentRemoteApplier(
                    switching,
                    metadata,
                    transactions,
                    inbox,
                ).apply(incoming, selected)
            }
            assertEquals(null, repository.getMark(incoming.id))
            assertEquals(
                "WAITING",
                database.downloadInboxDao().get(selected.owner, selected.origin, "MOMENT", incoming.id.toString())!!.state,
            )
            assertFalse(metadata.hasPending(EntityType.MOMENT, incoming.id.toString()))
        }

    @Test fun remoteTombstoneBeforeCaptureSurvivesAndLeavesPendingLocalWorkIntact() =
        runBlocking {
            val incoming = mark()
            metadata.enqueuePending(incoming.id.toString(), EntityType.MOMENT, PendingOperation.DELETE)
            val pending = metadata.getPendingUploads(EntityType.MOMENT).single { it.entityId == incoming.id.toString() }
            val inbox = inbox()
            inbox.stage("MOMENT", 9, listOf(WireDownload(incoming.id.toString(), 9, true, "retained-deletion")))
            assertTrue(MomentRemoteApplier(repository, metadata, transactions, inbox).delete(incoming.id, at, 9, selected))
            val tombstone = database.markedMomentDao().getById(incoming.id)!!
            assertEquals(at, tombstone.deletedAt)
            assertEquals(9L, tombstone.syncVersion)
            assertEquals(null, repository.applyFromSync(incoming))
            assertEquals(pending, metadata.getPendingUploads(EntityType.MOMENT).single { it.entityId == incoming.id.toString() })
            assertEquals(
                "APPLIED",
                database.downloadInboxDao().get(selected.owner, selected.origin, "MOMENT", incoming.id.toString())!!.state,
            )
        }

    @Test fun failedDeletionReceiptRollsBackTheTombstoneAndRetainsTheEncryptedInbox() =
        runBlocking {
            val incoming = mark()
            repository.applyFromSync(incoming)
            val dao = database.downloadInboxDao()
            val interrupted =
                object : DownloadInboxDao by dao {
                    override suspend fun put(row: DownloadInboxEntity) {
                        dao.put(row)
                        if (row.state == "APPLIED") throw CancellationException("interrupted deletion receipt")
                    }
                }
            val inbox = inbox(interrupted)
            inbox.stage("MOMENT", 9, listOf(WireDownload(incoming.id.toString(), 9, true, "retained-deletion")))
            assertFailsWith<CancellationException> {
                MomentRemoteApplier(repository, metadata, transactions, inbox).delete(incoming.id, at, 9, selected)
            }
            assertEquals(incoming, repository.getMark(incoming.id))
            assertEquals("WAITING", dao.get(selected.owner, selected.origin, "MOMENT", incoming.id.toString())!!.state)
            assertFalse(metadata.hasPending(EntityType.MOMENT, incoming.id.toString()))
        }

    @Test fun exactStoredPrecisionReplayAcknowledgesWithoutCreatingAnEcho() =
        runBlocking {
            val incoming =
                mark().copy(
                    capturedAt = Instant.parse("1970-01-01T00:00:00.100123456Z"),
                    lastUpdated = Instant.parse("1970-01-01T00:00:00.100987654Z"),
                )
            repository.applyFromSync(incoming)
            val inbox = inbox()
            stage(inbox, incoming)
            assertTrue(MomentRemoteApplier(repository, metadata, transactions, inbox).apply(incoming, selected))
            assertEquals(incoming.atMillisecondPrecision(), repository.getMark(incoming.id))
            assertFalse(metadata.hasPending(EntityType.MOMENT, incoming.id.toString()))
            assertTrue(inbox.isAppliedVersion("MOMENT", incoming.id.toString(), 7))
        }
}
