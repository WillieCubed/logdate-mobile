package app.logdate.client.sync.moments

import app.logdate.client.repository.moments.MarkState
import app.logdate.client.sync.RemoteApplyFilter
import app.logdate.client.sync.SyncProfile
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.ConditionalCreateResult
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MomentUploaderTest {
    private val watch = SyncProfile.LinkedWatch(RemoteApplyFilter.ALL)

    @Test fun `conditional creation saves the server version and settles its captured operation`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark(0)
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            val result = f.uploader().upload()
            assertTrue(result.success)
            assertEquals(1, result.uploadedItems)
            assertEquals(listOf(mark), f.created)
            assertEquals(
                8L,
                f.download.repository
                    .getMark(mark.id)!!
                    .syncVersion,
            )
            assertTrue(f.metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
        }

    @Test fun `a versioned phone edit uploads without an unconditional create`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark()
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
            assertTrue(f.uploader().upload().success)
            assertEquals(listOf(mark), f.updated)
            assertTrue(f.created.isEmpty())
            assertTrue(f.metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
        }

    @Test fun `an edit made during create remains queued and its newer content survives the receipt`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark(0)
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            f.onCreate = {
                f.download.repository.rows[mark.id] =
                    mark.copy(state = MarkState.FILLED, caption = "New edit", lastUpdated = Instant.fromEpochMilliseconds(101))
                f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
                Result.success(ConditionalCreateResult.Created(8, Instant.fromEpochMilliseconds(1000)))
            }
            assertTrue(f.uploader().upload().success)
            assertEquals(
                "New edit",
                f.download.repository
                    .getMark(mark.id)!!
                    .caption,
            )
            assertEquals(
                8L,
                f.download.repository
                    .getMark(mark.id)!!
                    .syncVersion,
            )
            assertEquals(1, f.metadata.getPendingUploads(EntityType.MOMENT).size)
        }

    @Test fun `an in-flight creation cannot clear a newer queued deletion or restore its body`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark(0)
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            f.onCreate = {
                f.download.repository.rows
                    .remove(mark.id)
                f.download.repository.deleted[mark.id] = Instant.fromEpochMilliseconds(101) to 0L
                f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.DELETE)
                Result.success(ConditionalCreateResult.Created(8, Instant.fromEpochMilliseconds(1000)))
            }
            assertTrue(f.uploader().upload().success)
            assertEquals(null, f.download.repository.getMark(mark.id))
            assertEquals(
                PendingOperation.DELETE,
                f.metadata
                    .getPendingUploads(EntityType.MOMENT)
                    .single()
                    .operation,
            )
        }

    @Test fun `already existing capture merges attachments and phone uploads the union`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark(0).copy(state = MarkState.FILLED, resolvedNoteIds = setOf(Uuid.random()))
            val remote = mark.copy(syncVersion = 7, resolvedNoteIds = setOf(Uuid.random()))
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            f.onCreate = { Result.success(ConditionalCreateResult.AlreadyExists(7)) }
            f.onGet = { Result.success(remote) }
            assertTrue(f.uploader().upload().success)
            assertEquals(mark.resolvedNoteIds + remote.resolvedNoteIds, f.updated.single().resolvedNoteIds)
            assertEquals(7L, f.updated.single().syncVersion)
            assertTrue(f.metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
        }

    @Test fun `linked watch binds an existing create to a held update rather than overwriting the phone`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark(0).copy(state = MarkState.FILLED, resolvedNoteIds = setOf(Uuid.random()))
            val remote = mark.copy(syncVersion = 7, state = MarkState.OPEN, resolvedNoteIds = emptySet())
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            f.onCreate = { Result.success(ConditionalCreateResult.AlreadyExists(7)) }
            f.onGet = { Result.success(remote) }
            assertTrue(f.uploader(watch).upload().success)
            assertTrue(f.updated.isEmpty())
            val pending = f.metadata.getPendingUploads(EntityType.MOMENT).single()
            assertEquals(PendingOperation.UPDATE, pending.operation)
            assertEquals(7L, pending.expectedServerVersion)
        }

    @Test fun `linked watch holds updates and disabled rollout holds every queued operation`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark()
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
            assertTrue(f.uploader(watch).upload().success)
            assertTrue(f.updated.isEmpty())
            f.enabled = false
            assertTrue(f.uploader().upload().success)
            assertTrue(f.updated.isEmpty())
            assertEquals(1, f.metadata.getPendingUploads(EntityType.MOMENT).size)
        }

    @Test fun `version conflict fetches remote state and retries a merged phone edit`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark().copy(state = MarkState.FILLED, resolvedNoteIds = setOf(Uuid.random()))
            val remote = mark.copy(syncVersion = 8, state = MarkState.DISMISSED, resolvedNoteIds = setOf(Uuid.random()))
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
            f.onUpdate =
                {
                    if (it.syncVersion ==
                        7L
                    ) {
                        Result.failure(CloudApiException("CONFLICT", "Changed", 409))
                    } else {
                        Result.success(
                            app.logdate.client.sync.cloud
                                .SyncUploadResult(9, Instant.fromEpochMilliseconds(1000)),
                        )
                    }
                }
            f.onGet = { Result.success(remote) }
            assertTrue(f.uploader().upload().success)
            assertEquals(listOf(7L, 8L), f.updated.map { it.syncVersion })
            assertEquals(mark.resolvedNoteIds + remote.resolvedNoteIds, f.updated.last().resolvedNoteIds)
            assertEquals(
                9L,
                f.download.repository
                    .getMark(mark.id)!!
                    .syncVersion,
            )
        }

    @Test fun `repeated conflicts are bounded and remain queued`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark()
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
            f.onUpdate = { Result.failure(CloudApiException("CONFLICT", "Changed", 409)) }
            f.onGet = { Result.success(mark.copy(syncVersion = 8 + f.gets.toLong())) }
            assertFalse(f.uploader().upload().success)
            assertEquals(3, f.updated.size)
            assertEquals(1, f.metadata.getPendingUploads(EntityType.MOMENT).size)
        }

    @Test fun `successful deletion only settles the captured delete`() =
        runTest {
            val f = MomentUploadFixture()
            val id = Uuid.random()
            f.metadata.enqueuePending(id.toString(), EntityType.MOMENT, PendingOperation.DELETE)
            val result = f.uploader(watch).upload()
            assertTrue(result.success)
            assertEquals(listOf(id), f.deleted)
            assertEquals(1, result.uploadedItems)
            assertTrue(f.metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
        }

    @Test fun `missing local body and deleted create collision are retained without false settlement`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark(0)
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            assertFalse(f.uploader().upload().success)
            assertEquals(1, f.metadata.getPendingUploads(EntityType.MOMENT).size)
            val g = MomentUploadFixture()
            g.download.repository.rows[mark.id] = mark
            g.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            g.onCreate = { Result.success(ConditionalCreateResult.Deleted(Instant.fromEpochMilliseconds(1000))) }
            assertFalse(g.uploader().upload().success)
            assertEquals(1, g.metadata.getPendingUploads(EntityType.MOMENT).size)
        }

    @Test fun `cancellation during upload propagates and preserves queued work`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark(0)
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            f.onCreate = { throw CancellationException("interrupted") }
            assertFailsWith<CancellationException> { f.uploader().upload() }
            assertEquals(1, f.metadata.getPendingUploads(EntityType.MOMENT).size)
        }

    @Test fun `full manager routes marked moment uploads and reports their count`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark(0)
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            val provider =
                object : app.logdate.client.device.identity.DeviceIdProvider {
                    override fun getDeviceId() =
                        kotlinx.coroutines.flow.MutableStateFlow(Uuid.parse("10000000-0000-0000-0000-000000000001"))

                    override suspend fun refreshDeviceId() = Unit
                }
            val manager =
                app.logdate.client.sync.test.testDefaultSyncManager(
                    sessionStorage = f.download.sessions,
                    cloudAccountRepository = f.download.accounts,
                    syncMetadataService = f.metadata,
                    transactionManager = f.download.db,
                    downloadInbox = f.download.inbox,
                    momentSyncConfig = MomentSyncConfig(f.cloud, f.download.repository, { true }),
                    deviceIdProvider = provider,
                    syncScope = backgroundScope,
                )
            val result = manager.uploadPendingChanges()
            assertTrue(result.success)
            assertEquals(1, result.uploadedItems)
            assertTrue(f.metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
        }

    @Test fun `repair update with expected version settles after a successful versioned write`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark()
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueueRepairIfAbsent(mark.id.toString(), EntityType.MOMENT, 7, f.download.scope.origin)
            assertTrue(f.uploader().upload().success)
            assertEquals(7L, f.updated.single().syncVersion)
            assertTrue(f.metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
        }

    @Test fun `account switch during create cannot settle or acknowledge a different account operation`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark(0)
            val session = f.download.sessions.getSession()!!
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            f.onCreate = {
                f.download.sessions.saveSession(session.copy(accountId = "different-owner"))
                Result.success(ConditionalCreateResult.Created(8, Instant.fromEpochMilliseconds(1000)))
            }
            assertFalse(f.uploader().upload().success)
            assertEquals(
                0L,
                f.download.repository
                    .getMark(mark.id)!!
                    .syncVersion,
            )
            f.download.sessions.saveSession(session)
            assertEquals(1, f.metadata.getPendingUploads(EntityType.MOMENT).size)
        }

    @Test fun `an applied downloaded tombstone settles a held create collision even during retry backoff`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark(0)
            f.download.repository.rows[mark.id] = mark
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            f.onCreate = { Result.success(ConditionalCreateResult.Deleted(Instant.fromEpochMilliseconds(1000))) }
            assertFalse(f.uploader().upload().success)
            val inbox = f.download.inbox
            val scope = f.download.scope
            inbox.stage(
                "MOMENT",
                9,
                listOf(
                    app.logdate.client.sync.recovery
                        .WireDownload(mark.id.toString(), 9, true, "retained-deletion"),
                ),
            )
            MomentRemoteApplier(
                f.download.repository,
                f.metadata,
                f.download.db,
                inbox,
            ).delete(mark.id, Instant.fromEpochMilliseconds(1000), 9, scope)
            val result = f.uploader().upload()
            assertTrue(result.success)
            assertEquals(0, result.uploadedItems)
            assertTrue(f.metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
            assertEquals(1, f.created.size)
        }

    @Test fun `unapplied tombstones cannot settle missing-body work and a remote deletion settles held watch updates`() =
        runTest {
            val f = MomentUploadFixture()
            val mark = f.download.mark()
            f.metadata.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
            f.download.inbox.stage(
                "MOMENT",
                9,
                listOf(
                    app.logdate.client.sync.recovery
                        .WireDownload(mark.id.toString(), 9, true, "retained-deletion"),
                ),
            )
            assertFalse(f.uploader().upload().success)
            assertEquals(1, f.metadata.getPendingUploads(EntityType.MOMENT).size)
            MomentRemoteApplier(
                f.download.repository,
                f.metadata,
                f.download.db,
                f.download.inbox,
            ).delete(mark.id, Instant.fromEpochMilliseconds(1000), 9, f.download.scope)
            assertTrue(f.uploader(watch).upload().success)
            assertTrue(f.metadata.getPendingUploads(EntityType.MOMENT).isEmpty())
            assertTrue(f.updated.isEmpty())
            assertTrue(f.created.isEmpty())
        }
}
