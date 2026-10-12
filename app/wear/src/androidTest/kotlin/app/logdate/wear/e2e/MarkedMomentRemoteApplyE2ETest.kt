package app.logdate.wear.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.data.moments.OfflineFirstMarkedMomentRepository
import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.di.WatchCloud
import app.logdate.wear.v2.mark.MarkedMomentStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class MarkedMomentRemoteApplyE2ETest {
    private val koin = KoinJavaComponent.getKoin()
    private val at = Instant.fromEpochMilliseconds(5)

    private fun remote() =
        MarkedMoment(
            Uuid.random(),
            at,
            "UTC",
            null,
            null,
            MarkState.OPEN,
            "remote",
            emptySet(),
            "isolated-test-watch",
            at,
            8,
        )

    @Test fun installedRepositoryPreservesCompletionAttachmentsAndPendingLocalWork() =
        runBlocking {
            val localNote = Uuid.random()
            val remoteNote = Uuid.random()
            val incoming = remote().copy(resolvedNoteIds = setOf(remoteNote), lastUpdated = Instant.fromEpochMilliseconds(9))
            koin.get<MarkedMomentStore>().create(
                MarkedMomentEntity(
                    id = incoming.id,
                    capturedAt = at,
                    timeZoneId = "UTC",
                    state = MarkedMomentState.FILLED,
                    caption = "local",
                    resolvedNoteIds = localNote.toString(),
                    originDeviceId = incoming.originDeviceId,
                    lastUpdated = at,
                ),
            )
            val repository = koin.get<SyncableMarkedMomentRepository>()
            assertSame(koin.get<MarkedMomentRepository>(), repository)
            val expected = incoming.copy(state = MarkState.FILLED, resolvedNoteIds = setOf(localNote, remoteNote))
            assertEquals(expected, repository.applyFromSync(incoming))
            assertEquals(expected, repository.getMark(incoming.id))
            assertTrue(koin.get<SyncMetadataService>().hasPending(EntityType.MOMENT, incoming.id.toString()))
            assertEquals(expected, repository.applyFromSync(incoming))
        }

    @Test fun aNewRemoteProjectionDoesNotCreateLocalUploadWork() =
        runBlocking {
            val incoming = remote()
            val repository = koin.get<SyncableMarkedMomentRepository>()
            assertEquals(incoming, repository.applyFromSync(incoming))
            assertEquals(incoming, repository.getMark(incoming.id))
            assertFalse(koin.get<SyncMetadataService>().hasPending(EntityType.MOMENT, incoming.id.toString()))
        }

    @Test fun cancellationRollsBackTheActualRoomInsert() =
        runBlocking {
            val dao = koin.get<MarkedMomentDao>()
            val actual = koin.get<SyncTransactionManager>(WatchCloud)
            val interrupted =
                object : SyncTransactionManager {
                    override suspend fun <T> withTransaction(block: suspend () -> T): T =
                        actual.withTransaction {
                            block()
                            throw CancellationException("exchange changed")
                        }
                }
            val repository = OfflineFirstMarkedMomentRepository(dao, interrupted, koin.get())
            val incoming = remote()
            assertFailsWith<CancellationException> { repository.applyFromSync(incoming) }
            assertNull(dao.getById(incoming.id))
        }

    @Test fun installedLocalResolutionAndLocationQueueWithoutReopeningAndDeletionRetainsAnOutbox() =
        runBlocking {
            val incoming = remote()
            val repository = koin.get<SyncableMarkedMomentRepository>()
            val metadata = koin.get<SyncMetadataService>()
            repository.applyFromSync(incoming)
            val note = Uuid.random()
            assertTrue(repository.fill(incoming.id, "Filled caption", setOf(note), at))
            assertTrue(repository.fillLocationIfMissing(incoming.id, MarkLocation(36.17, -115.14, 5f), at))
            val filled = repository.getMark(incoming.id)!!
            assertEquals(MarkState.FILLED, filled.state)
            assertEquals(setOf(note), filled.resolvedNoteIds)
            assertTrue(filled.lastUpdated > incoming.lastUpdated)
            val pending = metadata.getPendingUploads(EntityType.MOMENT).single { it.entityId == incoming.id.toString() }
            assertEquals(PendingOperation.UPDATE, pending.operation)
            assertFalse(repository.dismiss(incoming.id, at))
            assertEquals(pending, metadata.getPendingUploads(EntityType.MOMENT).single { it.entityId == incoming.id.toString() })
            assertTrue(repository.delete(incoming.id, at))
            assertNull(repository.getMark(incoming.id))
            assertEquals(
                PendingOperation.DELETE,
                metadata
                    .getPendingUploads(EntityType.MOMENT)
                    .single {
                        it.entityId == incoming.id.toString()
                    }.operation,
            )
            assertNull(repository.applyFromSync(incoming))
        }

    @Test fun deletingAnUnuploadedCaptureKeepsDeletionForAnInflightCreate() =
        runBlocking {
            val incoming = remote()
            koin.get<MarkedMomentStore>().create(
                MarkedMomentEntity(
                    id = incoming.id,
                    capturedAt = at,
                    timeZoneId = "UTC",
                    state = MarkedMomentState.OPEN,
                    originDeviceId = incoming.originDeviceId,
                    lastUpdated = at,
                ),
            )
            val repository = koin.get<SyncableMarkedMomentRepository>()
            assertTrue(repository.delete(incoming.id, at))
            assertEquals(
                PendingOperation.DELETE,
                koin
                    .get<SyncMetadataService>()
                    .getPendingUploads(EntityType.MOMENT)
                    .single { it.entityId == incoming.id.toString() }
                    .operation,
            )
        }

    @Test fun failedOutboxWriteRollsBackTheActualMarkAndPendingOperation() =
        runBlocking {
            val incoming = remote()
            val dao = koin.get<MarkedMomentDao>()
            koin.get<SyncableMarkedMomentRepository>().applyFromSync(incoming)
            val metadata = koin.get<SyncMetadataService>()
            val interrupted =
                object : SyncMetadataService by metadata {
                    override suspend fun enqueuePending(
                        entityId: String,
                        entityType: EntityType,
                        operation: PendingOperation,
                    ) {
                        metadata.enqueuePending(entityId, entityType, operation)
                        throw CancellationException("Simulated failure after outbox write")
                    }
                }
            val repository = OfflineFirstMarkedMomentRepository(dao, koin.get<SyncTransactionManager>(WatchCloud), interrupted)
            assertFailsWith<CancellationException> { repository.fill(incoming.id, "Never committed", setOf(Uuid.random()), at) }
            assertEquals(incoming, repository.getMark(incoming.id))
            assertFalse(metadata.hasPending(EntityType.MOMENT, incoming.id.toString()))
        }

    @Test fun deletionBeforeCaptureArrivalSurvivesActualRoomReadsAndReplay() =
        runBlocking {
            val incoming = remote()
            val repository = koin.get<SyncableMarkedMomentRepository>()
            repository.applyDeletionFromSync(incoming.id, at, 10)
            assertNull(repository.applyFromSync(incoming))
            assertNull(repository.getMark(incoming.id))
            val row = koin.get<MarkedMomentDao>().getById(incoming.id)!!
            assertEquals(10, row.syncVersion)
            assertEquals(at, row.deletedAt)
            assertFalse(koin.get<SyncMetadataService>().hasPending(EntityType.MOMENT, incoming.id.toString()))
        }
}
