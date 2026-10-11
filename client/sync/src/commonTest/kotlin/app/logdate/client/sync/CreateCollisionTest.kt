package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudContentDataSource
import app.logdate.client.sync.cloud.ConditionalCreateResult
import app.logdate.client.sync.cloud.ContentState
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeSyncMetadataService
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [CreateCollisionResolver] and the upload pass's handling of a create the server refused because
 * another device of the account, such as a linked watch, uploaded the record first.
 */
class CreateCollisionTest {
    @Test
    fun `a record another device created at this version settles against the server's`() {
        assertEquals(
            CollisionOutcome.Settle(9),
            CreateCollisionResolver.resolve(CreateCollision.CreatedElsewhere(5), localContentVersion = 5, server = ContentState.Present(9)),
        )
    }

    @Test
    fun `an edit made here after the other device's copy goes up as an edit`() {
        assertEquals(
            CollisionOutcome.RebaseAsUpdate(9),
            CreateCollisionResolver.resolve(CreateCollision.CreatedElsewhere(5), localContentVersion = 6, server = ContentState.Present(9)),
        )
    }

    @Test
    fun `a record no other device made stays an ordinary failure`() {
        assertEquals(
            CollisionOutcome.Retry,
            CreateCollisionResolver.resolve(CreateCollision.Unexpected, localContentVersion = 5, server = ContentState.Present(9)),
        )
    }

    @Test
    fun `an absent record retries but a server deletion is respected`() {
        val elsewhere = CreateCollision.CreatedElsewhere(5)

        assertEquals(CollisionOutcome.Retry, CreateCollisionResolver.resolve(elsewhere, 5, ContentState.Absent))
        val deleted = ContentState.Deleted(Instant.fromEpochMilliseconds(1))
        assertEquals(CollisionOutcome.Deleted(deleted.deletedAt), CreateCollisionResolver.resolve(elsewhere, 5, deleted))
    }

    @Test
    fun `a phone upload the watch already made settles without a second version`() =
        runTest {
            val fixture = collidingNote()
            val manager = fixture.manager(CreateCollisionPolicy { _, _ -> CreateCollision.CreatedElsewhere(fixture.version) })

            val result = manager.uploadPendingChanges()

            assertTrue(result.success, result.errors.toString())
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
            assertEquals(1, fixture.api.createContentIfAbsentCalls.size)
            assertTrue(fixture.api.uploadContentCalls.isEmpty())
            assertTrue(fixture.api.updateContentCalls.isEmpty())
            assertEquals(listOf(fixture.note.uid.toString()), fixture.api.contentStateCalls)
            assertEquals(0L, fixture.notes.getNoteById(fixture.note.uid)?.syncVersion)
        }

    @Test
    fun `a phone edit newer than the watch's upload goes up as one edit`() =
        runTest {
            val fixture = collidingNote()
            val manager = fixture.manager(CreateCollisionPolicy { _, _ -> CreateCollision.CreatedElsewhere(fixture.version - 1) })

            val first = manager.uploadPendingChanges()

            assertTrue(first.success, first.errors.toString())
            val rebased = fixture.metadata.getPendingUploads(EntityType.NOTE).single()
            assertEquals(PendingOperation.UPDATE, rebased.operation)
            assertEquals(9L, rebased.expectedServerVersion)

            val second = manager.uploadPendingChanges()

            assertTrue(second.success, second.errors.toString())
            assertEquals(
                9L,
                fixture.api.updateContentCalls
                    .single()
                    .third.syncVersion,
            )
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `without a policy the conflict stays an upload failure`() =
        runTest {
            val fixture = collidingNote()

            val result = fixture.manager(CreateCollisionPolicy.None).uploadPendingChanges()

            assertFalse(result.success)
            assertEquals(
                PendingOperation.CREATE,
                fixture.metadata
                    .getPendingUploads(EntityType.NOTE)
                    .single()
                    .operation,
            )
            assertTrue(fixture.api.contentStateCalls.isEmpty())
        }

    @Test
    fun `when the server's copy cannot be read the create stays queued`() =
        runTest {
            val fixture = collidingNote()
            fixture.api.contentStateResponse = Result.failure(CloudApiException("NETWORK_ERROR", "Failed to read content state"))

            val manager = fixture.manager(CreateCollisionPolicy { _, _ -> CreateCollision.CreatedElsewhere(fixture.version) })

            val result = manager.uploadPendingChanges()

            assertFalse(result.success)
            assertEquals(
                PendingOperation.CREATE,
                fixture.metadata
                    .getPendingUploads(EntityType.NOTE)
                    .single()
                    .operation,
            )
        }

    @Test
    fun `server deletion removes the duplicate phone copy`() =
        runTest {
            val fixture = collidingNote()
            fixture.api.contentStateResponse = Result.success(ContentState.Deleted(Instant.fromEpochMilliseconds(100)))
            val result =
                fixture
                    .manager(
                        CreateCollisionPolicy {
                            _,
                            _,
                            ->
                            CreateCollision.CreatedElsewhere(fixture.version)
                        },
                    ).uploadPendingChanges()
            assertTrue(result.success, result.errors.toString())
            assertNull(fixture.notes.getNoteById(fixture.note.uid))
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `invalid server version never settles an upload`() =
        runTest {
            val fixture = collidingNote()
            fixture.api.contentStateResponse = Result.success(ContentState.Present(0))
            val result =
                fixture
                    .manager(
                        CreateCollisionPolicy {
                            _,
                            _,
                            ->
                            CreateCollision.CreatedElsewhere(fixture.version)
                        },
                    ).uploadPendingChanges()
            assertFalse(result.success)
            assertEquals(
                PendingOperation.CREATE,
                fixture.metadata
                    .getPendingUploads(EntityType.NOTE)
                    .single()
                    .operation,
            )
        }

    @Test
    fun `wrong HTTP status never resolves a content exists error`() =
        runTest {
            val fixture = collidingNote()
            fixture.api.createContentIfAbsentResponse =
                Result.failure(CloudApiException("CONTENT_EXISTS", "unexpected refusal", statusCode = 500))
            val result =
                fixture
                    .manager(
                        CreateCollisionPolicy {
                            _,
                            _,
                            ->
                            CreateCollision.CreatedElsewhere(fixture.version)
                        },
                    ).uploadPendingChanges()
            assertFalse(result.success)
            assertTrue(fixture.api.contentStateCalls.isEmpty())
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isNotEmpty())
        }

    @Test
    fun `a deletion queued during HEAD survives the old create`() =
        runTest {
            val fixture = collidingNote()
            val delegate = DefaultCloudContentDataSource(fixture.api)
            val content =
                object : CloudContentDataSource by delegate {
                    override suspend fun contentState(
                        accessToken: String,
                        noteId: Uuid,
                    ): Result<ContentState> {
                        fixture.metadata.enqueuePending(noteId.toString(), EntityType.NOTE, PendingOperation.DELETE)
                        return Result.success(ContentState.Present(9))
                    }
                }
            val result =
                fixture
                    .manager(
                        CreateCollisionPolicy {
                            _,
                            _,
                            ->
                            CreateCollision.CreatedElsewhere(fixture.version)
                        },
                        content,
                    ).uploadPendingChanges()
            assertFalse(result.success)
            assertEquals(
                PendingOperation.DELETE,
                fixture.metadata
                    .getPendingUploads(EntityType.NOTE)
                    .single()
                    .operation,
            )
        }

    @Test
    fun `HEAD cancellation propagates and retains the capture`() =
        runTest {
            val fixture = collidingNote()
            fixture.api.contentStateResponse = Result.failure(CancellationException("cancelled HEAD"))
            val manager = fixture.manager(CreateCollisionPolicy { _, _ -> CreateCollision.CreatedElsewhere(fixture.version) })
            assertFailsWith<CancellationException> { manager.uploadPendingChanges() }
            assertEquals(
                PendingOperation.CREATE,
                fixture.metadata
                    .getPendingUploads(EntityType.NOTE)
                    .single()
                    .operation,
            )
            assertEquals(
                0,
                fixture.metadata
                    .getPendingUploads(EntityType.NOTE)
                    .single()
                    .retryCount,
            )
        }

    private suspend fun collidingNote(): Fixture {
        val api = fakeCloudApiClient()
        api.uploadContentResponse = Result.failure(CloudApiException("CONTENT_EXISTS", "Request failed (HTTP 412)", statusCode = 412))
        api.contentStateResponse = Result.success(ContentState.Present(9))
        api.createContentIfAbsentResponse = Result.success(ConditionalCreateResult.AlreadyExists(9))
        val notes = fakeJournalNotesRepository("said on the watch")
        val metadata = fakeSyncMetadataService()
        val note = notes.allNotesObserved.first().single()
        metadata.enqueuePending(note.uid.toString(), EntityType.NOTE, PendingOperation.CREATE)
        return Fixture(api, notes, metadata, note)
    }

    private class Fixture(
        val api: FakeCloudApiClient,
        val notes: FakeJournalNotesRepository,
        val metadata: FakeSyncMetadataService,
        val note: JournalNote,
    ) {
        val version: Long = note.lastUpdated.toEpochMilliseconds()

        fun manager(
            policy: CreateCollisionPolicy,
            content: CloudContentDataSource = DefaultCloudContentDataSource(api),
        ) = testDefaultSyncManager(
            cloudContentDataSource = content,
            journalNotesRepository = notes,
            syncMetadataService = metadata,
            createCollisionPolicy = policy,
        )
    }
}
