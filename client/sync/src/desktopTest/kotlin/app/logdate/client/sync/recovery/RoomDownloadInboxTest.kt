package app.logdate.client.sync.recovery

import androidx.room.Room
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.dao.sync.DownloadInboxDao
import app.logdate.client.database.entities.sync.DownloadCheckpointEntity
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.media.ConditionalMediaSaver
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.SyncMediaTransfer
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RoomDownloadInboxTest {
    @Test
    fun `repair insertion preserves scoped and legacy mutations and retry state`() =
        runTest {
            val path = Files.createTempFile("logdate-repair-test", ".db")
            Files.delete(path)

            fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(path.toString()))
            var database = open()
            try {
                val dao = database.syncMetadataDao()
                for (owner in listOf("owner", "")) {
                    val id = "deleted-$owner"
                    val deletion = PendingUploadEntity(owner, "server", "DRAFT", id, "DELETE", 10, 7)
                    dao.insertPending(deletion)
                    dao.insertRepairIfAbsent("owner", "server", "DRAFT", id, 20)
                    dao.insertRepairIfAbsent("owner", "server", "DRAFT", id, 30, operation = "CREATE")
                    assertEquals(deletion, dao.getPending(owner, "server", "DRAFT", id))
                    if (owner.isEmpty()) assertEquals(null, dao.getPending("owner", "server", "DRAFT", id))
                }
                dao.insertPending(PendingUploadEntity("", "other-server", "DRAFT", "recoverable", "DELETE", 10))
                dao.insertRepairIfAbsent("owner", "server", "DRAFT", "recoverable", 20, expectedServerVersion = 2L)
                assertEquals("UPDATE", dao.getPending("owner", "server", "DRAFT", "recoverable")?.operation)
                dao.insertRepairIfAbsent("owner", "server", "DRAFT", "recoverable", 30)
                assertEquals(20L, dao.getPending("owner", "server", "DRAFT", "recoverable")?.createdAt)
                dao.insertRepairIfAbsent("owner", "server", "NOTE", "new-legacy-entry", 40, operation = "CREATE")
                val initial = requireNotNull(dao.getPending("owner", "server", "NOTE", "new-legacy-entry"))
                kotlin.test.assertTrue(dao.bindCreateToServerVersion("owner", "server", "NOTE", initial.entityId, initial.operationId, 42))
                kotlin.test.assertFalse(dao.deletePendingIfCurrent("owner", "server", "NOTE", initial.entityId, initial.operationId))
                database.close()
                database = open()
                assertEquals("UPDATE", database.syncMetadataDao().getPending("owner", "server", "NOTE", "new-legacy-entry")?.operation)
                assertEquals(
                    42L,
                    database.syncMetadataDao().getPending("owner", "server", "NOTE", "new-legacy-entry")?.expectedServerVersion,
                )
                assertEquals("DELETE", database.syncMetadataDao().getPending("owner", "server", "DRAFT", "deleted-owner")?.operation)
                assertEquals(2L, database.syncMetadataDao().getPending("owner", "server", "DRAFT", "recoverable")?.expectedServerVersion)
            } finally {
                database.close()
                Files.deleteIfExists(path)
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
            }
        }

    @Test
    fun `wire page and cursor commit together and survive reopening the database`() =
        runTest {
            val path = Files.createTempFile("logdate-inbox-test", ".db")
            Files.delete(path)

            fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(path.toString()))
            var database = open()
            try {
                var fail = true
                val dao = database.downloadInboxDao()
                val faultingDao =
                    object : DownloadInboxDao by dao {
                        override suspend fun checkpoint(row: DownloadCheckpointEntity) {
                            if (fail) error("simulated interruption before checkpoint")
                            dao.checkpoint(row)
                        }
                    }
                val scope = { DownloadScope("disposable-owner", "disposable-server") }
                val inbox = DownloadInbox(faultingDao, RoomSyncTransactionManager(database), scope, { 1L })
                assertFailsWith<IllegalStateException> {
                    inbox.stage("JOURNAL", 10, listOf(WireDownload("record", 10, false, "encrypted-wire")))
                }
                assertEquals(0, inbox.count())
                assertEquals(0L, inbox.cursor("JOURNAL"))
                fail = false
                inbox.stage("JOURNAL", 10, listOf(WireDownload("record", 10, false, "encrypted-wire")))
                database.close()
                database = open()
                val restarted = DownloadInbox(database.downloadInboxDao(), RoomSyncTransactionManager(database), scope, { 1L })
                assertEquals(10L, restarted.cursor("JOURNAL"))
                assertEquals("encrypted-wire", restarted.pending("JOURNAL").single().payload)
            } finally {
                database.close()
                Files.deleteIfExists(path)
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
            }
        }

    @Test
    fun `entries waiting for an app update are offered again but are not unfinished recovery`() =
        runTest {
            val path = Files.createTempFile("logdate-inbox-update-test", ".db")
            Files.delete(path)
            val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(path.toString()))
            try {
                var now = 1L
                val scope = { DownloadScope("disposable-owner", "disposable-server") }
                val inbox = DownloadInbox(database.downloadInboxDao(), RoomSyncTransactionManager(database), scope, { now })
                inbox.stage(
                    "NOTE",
                    2,
                    listOf(WireDownload("newer-kind", 1, false, "{}"), WireDownload("storage-failure", 2, false, "{}")),
                )
                inbox.failed("NOTE", "newer-kind", DiagnosticReason.UNSUPPORTED_FORMAT.name, 1)
                inbox.failed("NOTE", "storage-failure", DiagnosticReason.LOCAL_STORAGE.name, 2)

                assertEquals(1, inbox.count(), "Only the record this version can still finish counts")
                now += 60 * 60 * 1_000L
                assertEquals(setOf("newer-kind", "storage-failure"), inbox.pending("NOTE").map { it.entityId }.toSet())
            } finally {
                database.close()
                Files.deleteIfExists(path)
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
            }
        }

    @Test
    fun `native media low-storage recovery resumes after reopening the database`() =
        runTest {
            val path = Files.createTempFile("logdate-native-media-storage-retry", ".db")
            Files.delete(path)

            fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(path.toString()))
            var database = open()
            try {
                var now = 1L
                val selected = DownloadScope("owner", "server")
                val scope = { selected }
                val transactions = { RoomSyncTransactionManager(database) }
                val notes = FakeJournalNotesRepository()
                val drafts = FakeJournalRepository()
                val media =
                    object : InMemoryMediaManager(), ConditionalMediaSaver {
                        var lowStorage = true

                        override suspend fun saveMedia(
                            payload: MediaPayload,
                            canCommit: () -> Boolean,
                        ): String {
                            if (lowStorage) error("No space left on device")
                            return super.saveMedia(payload, canCommit)
                        }
                    }
                val local = "logdate-media://library/old-path.jpg"
                val timestamp = Instant.fromEpochMilliseconds(4)
                val note = JournalNote.Image(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = local)
                notes.create(note)
                val storage = InMemoryKeyValueStorage()
                val refs = KeyValueMediaSyncRefStore(storage, { selected.origin }, { selected.owner })
                refs.upsert(MediaSyncRef(note.uid.toString(), local, "https://example.invalid/media/original", "original", 1))
                val downloads = mutableListOf<String>()
                val cloud =
                    object : CloudMediaDataSource {
                        override suspend fun uploadMedia(
                            accessToken: String,
                            contentId: Uuid,
                            media: app.logdate.client.media.MediaFileSource,
                        ): Result<MediaUploadResult> = error("no upload")

                        override suspend fun downloadMedia(
                            accessToken: String,
                            mediaId: String,
                        ): Result<MediaFile> {
                            downloads += mediaId
                            return Result.success(MediaFile(note.uid, "$mediaId.jpg", "image/jpeg", 1, byteArrayOf(1)))
                        }
                    }

                fun recovery(): NativeMediaRecovery {
                    val transactionManager = transactions()
                    return NativeMediaRecovery(
                        DownloadInbox(database.downloadInboxDao(), transactionManager, scope, { now }),
                        notes,
                        drafts,
                        media,
                        SyncMediaTransfer(media, refs, cloud),
                        refs,
                        transactionManager,
                    )
                }

                recovery().recover("token", EntityType.NOTE)

                val failed =
                    requireNotNull(
                        database
                            .downloadInboxDao()
                            .get(selected.owner, selected.origin, "MEDIA_NATIVE_NOTE", note.uid.toString()),
                    )

                assertEquals("FAILED", failed.state)
                assertEquals(1, failed.attempts)
                assertEquals(DiagnosticReason.LOCAL_STORAGE.name, failed.reason)
                database.close()

                database = open()
                now = failed.nextAttemptAt - 1
                assertEquals(
                    emptyList(),
                    DownloadInbox(database.downloadInboxDao(), transactions(), scope, { now }).pending("MEDIA_NATIVE_NOTE"),
                    "A retry must honor its persisted deadline",
                )
                now = failed.nextAttemptAt
                media.lowStorage = false
                recovery().recover("token", EntityType.NOTE)

                val repaired = assertIs<JournalNote.Image>(notes.getNoteById(note.uid))
                assertTrue(media.exists(repaired.mediaRef))
                assertEquals(listOf("original", "original"), downloads)
                assertEquals(
                    "APPLIED",
                    database.downloadInboxDao().get(selected.owner, selected.origin, "MEDIA_NATIVE_NOTE", note.uid.toString())?.state,
                )
            } finally {
                database.close()
                Files.deleteIfExists(path)
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
            }
        }
}
