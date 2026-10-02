package app.logdate.client.sync.recovery

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.client.sync.SyncMediaTransfer
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.MediaDownloadResponse
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.InMemoryMediaSyncRefStore
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.shared.model.sync.ContentChange
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MediaRecoveryTest {
    @Test
    fun `normal download pass recovers persisted attachments and exposes unresolved recovery`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val inbox = DownloadInbox(db, db, { DownloadScope("owner", "origin") }, { 1L })
            val id = Uuid.random()
            val ref = "https://example.invalid/media/file"
            val notes = FakeJournalNotesRepository()
            notes.createFromSync(
                JournalNote.Image(id, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(1), ref, syncVersion = 4),
            )
            inbox.stage(
                "NOTE",
                4,
                listOf(
                    WireDownload(
                        id.toString(),
                        4,
                        false,
                        Json.encodeToString(
                            ContentChange(id.toString(), "IMAGE", mediaUri = ref, createdAt = 1, lastUpdated = 1, serverVersion = 4),
                        ),
                    ),
                ),
            )
            inbox.applied("NOTE", id.toString(), 4)
            val api = fakeCloudApiClient { downloadMediaResponse = Result.failure(IllegalStateException("offline")) }
            val manager =
                app.logdate.client.sync.test.testDefaultSyncManager(
                    journalNotesRepository = notes,
                    cloudMediaDataSource = DefaultCloudMediaDataSource(api),
                    mediaManager = InMemoryMediaManager(),
                    downloadInbox = inbox,
                    transactionManager = db,
                    syncScope = backgroundScope,
                )
            manager.downloadRemoteChanges()
            assertTrue(manager.getSyncStatus().hasErrors, "Unresolved media must not look like completed recovery")
            api.downloadMediaResponse =
                Result.success(MediaDownloadResponse(id.toString(), "image.bin", "image/png", 3, byteArrayOf(1, 2, 3), ref))
            inbox.release()
            manager.downloadRemoteChanges()
            assertEquals(0, inbox.count())
            assertTrue(
                notes
                    .getNoteById(id)
                    ?.mediaRefOrNull()
                    .orEmpty()
                    .startsWith("file://"),
            )
        }

    @Test
    fun `metadata remains available while media retries durably and becomes local after restart`() =
        runTest {
            val db = DownloadInboxTest.Database()
            var now = 1L
            val scope = { DownloadScope("owner", "origin") }
            val inbox = DownloadInbox(db, db, scope, { now })
            val id = Uuid.random()
            val ref = "https://example.invalid/media/file"
            val note =
                JournalNote.Image(
                    id,
                    Instant.fromEpochMilliseconds(1),
                    Instant.fromEpochMilliseconds(1),
                    ref,
                    "caption",
                    syncVersion = 4,
                )
            val backing = FakeJournalNotesRepository()
            val notes =
                object : app.logdate.client.repository.journals.SyncableJournalNotesRepository by backing {
                    override suspend fun deleteFromSync(noteId: Uuid) {
                        error("Hydration must update the reference without deleting the note or its relationships")
                    }
                }
            notes.createFromSync(note)
            inbox.stage(
                "NOTE",
                4,
                listOf(
                    WireDownload(
                        id.toString(),
                        4,
                        false,
                        Json.encodeToString(
                            ContentChange(id.toString(), "IMAGE", mediaUri = ref, createdAt = 1, lastUpdated = 1, serverVersion = 4),
                        ),
                    ),
                ),
            )
            inbox.applied("NOTE", id.toString(), 4)
            val api = fakeCloudApiClient { downloadMediaResponse = Result.failure(IllegalStateException("offline")) }
            val refs = InMemoryMediaSyncRefStore()
            val media = InMemoryMediaManager()
            val transfer = SyncMediaTransfer(media, refs, DefaultCloudMediaDataSource(api))
            SyncMediaRecovery(inbox, notes, transfer, refs, db).recover("token")
            assertEquals("caption", (notes.getNoteById(id) as JournalNote.Image).caption)
            assertEquals(1, inbox.count())
            now += 10000
            api.downloadMediaResponse =
                Result.success(MediaDownloadResponse(id.toString(), "image.bin", "image/png", 3, byteArrayOf(1, 2, 3), ref))
            val restarted = DownloadInbox(db, db, scope, { now })
            SyncMediaRecovery(restarted, notes, transfer, refs, db).recover("token")
            assertEquals(0, restarted.count())
            val local = notes.getNoteById(id)?.mediaRefOrNull().orEmpty()
            assertTrue(local.startsWith("file://"))
            assertTrue(media.exists(local))
            assertContentEquals(byteArrayOf(1, 2, 3), media.readMedia(local).data)
        }

    @Test
    fun `deletion during a media request cannot resurrect or relink the owner`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val inbox = DownloadInbox(db, db, { DownloadScope("owner", "origin") }, { 1L })
            val id = Uuid.random()
            val ref = "https://example.invalid/media/file"
            val notes = FakeJournalNotesRepository()
            notes.createFromSync(
                JournalNote.Image(id, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(1), ref, syncVersion = 4),
            )
            inbox.stage(
                "NOTE",
                4,
                listOf(
                    WireDownload(
                        id.toString(),
                        4,
                        false,
                        Json.encodeToString(
                            ContentChange(id.toString(), "IMAGE", mediaUri = ref, createdAt = 1, lastUpdated = 1, serverVersion = 4),
                        ),
                    ),
                ),
            )
            inbox.applied("NOTE", id.toString(), 4)
            val source =
                object : app.logdate.client.sync.cloud.CloudMediaDataSource by DefaultCloudMediaDataSource(fakeCloudApiClient()) {
                    override suspend fun downloadMedia(
                        accessToken: String,
                        mediaId: String,
                    ): Result<app.logdate.client.sync.cloud.MediaFile> {
                        notes.deleteFromSync(id)
                        return Result.success(
                            app.logdate.client.sync.cloud
                                .MediaFile(id, "image.bin", "image/png", 3, byteArrayOf(1, 2, 3)),
                        )
                    }
                }
            val refs = InMemoryMediaSyncRefStore()
            SyncMediaRecovery(inbox, notes, SyncMediaTransfer(InMemoryMediaManager(), refs, source), refs, db).recover("token")
            assertEquals(null, notes.getNoteById(id))
            assertEquals(null, refs.get(id))
            assertEquals(0, inbox.count())
        }

    @Test
    fun `a newer staged tombstone prevents attaching bytes before deletion applies`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val inbox = DownloadInbox(db, db, { DownloadScope("owner", "origin") }, { 1L })
            val id = Uuid.random()
            val ref = "https://example.invalid/media/file"
            val notes = FakeJournalNotesRepository()
            notes.createFromSync(
                JournalNote.Image(id, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(1), ref, syncVersion = 4),
            )
            inbox.stage(
                "NOTE",
                4,
                listOf(
                    WireDownload(
                        id.toString(),
                        4,
                        false,
                        Json.encodeToString(
                            ContentChange(id.toString(), "IMAGE", mediaUri = ref, createdAt = 1, lastUpdated = 1, serverVersion = 4),
                        ),
                    ),
                ),
            )
            inbox.applied("NOTE", id.toString(), 4)
            val source =
                object : app.logdate.client.sync.cloud.CloudMediaDataSource by DefaultCloudMediaDataSource(fakeCloudApiClient()) {
                    override suspend fun downloadMedia(
                        accessToken: String,
                        mediaId: String,
                    ): Result<app.logdate.client.sync.cloud.MediaFile> {
                        inbox.stage("NOTE", 5, listOf(WireDownload(id.toString(), 5, true, "tombstone")))
                        return Result.success(
                            app.logdate.client.sync.cloud
                                .MediaFile(id, "image.bin", "image/png", 3, byteArrayOf(1, 2, 3)),
                        )
                    }
                }
            val refs = InMemoryMediaSyncRefStore()
            SyncMediaRecovery(inbox, notes, SyncMediaTransfer(InMemoryMediaManager(), refs, source), refs, db).recover("token")
            assertEquals(ref, notes.getNoteById(id)?.mediaRefOrNull())
            assertEquals(null, refs.get(id))
            assertEquals(1, inbox.count())
        }
}
