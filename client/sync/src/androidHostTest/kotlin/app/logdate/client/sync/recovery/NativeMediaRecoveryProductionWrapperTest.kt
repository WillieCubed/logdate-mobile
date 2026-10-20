package app.logdate.client.sync.recovery

import android.content.Context
import app.logdate.client.media.AndroidPhotoLibraryMediaManager
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.MediaManager
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.SyncMediaTransfer
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NativeMediaRecoveryProductionWrapperTest {
    @Test
    fun `native media recovery persists through the production photo library wrapper`() =
        runTest {
            val database = DownloadInboxTest.Database()
            val scope = DownloadScope("owner", "https://fixture.example")
            val inbox = DownloadInbox(database, database, { scope }, { 1L })
            val notes = FakeJournalNotesRepository()
            val missing = "file:///missing-original.png"
            val quarantined = UnavailableMediaReferences.nativeRestore(missing)
            val original =
                JournalNote.Image(
                    creationTimestamp = kotlin.time.Instant.fromEpochMilliseconds(1),
                    lastUpdated = kotlin.time.Instant.fromEpochMilliseconds(1),
                    mediaRef = quarantined,
                    caption = "Retain this caption",
                )
            notes.create(original)
            val refs = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { scope.origin }, { scope.owner })
            refs.upsert(MediaSyncRef(original.uid.toString(), missing, "${scope.origin}/media/original", "original", 1))
            val bytes = byteArrayOf(1, 2, 3)
            var downloads = 0
            val cloud =
                object : CloudMediaDataSource {
                    override suspend fun uploadMedia(
                        accessToken: String,
                        contentId: kotlin.uuid.Uuid,
                        media: MediaFileSource,
                    ): Result<MediaUploadResult> = error("No uploads")

                    override suspend fun downloadMedia(
                        accessToken: String,
                        mediaId: String,
                    ): Result<MediaFile> {
                        downloads++
                        return Result.success(MediaFile(original.uid, "original.png", "image/png", 3, bytes))
                    }
                }
            val media: MediaManager = AndroidPhotoLibraryMediaManager(InMemoryMediaManager(), mockk<Context>(relaxed = true))
            val recovery =
                NativeMediaRecovery(inbox, notes, FakeJournalRepository(), media, SyncMediaTransfer(media, refs, cloud), refs, database)

            recovery.recover("synthetic", EntityType.NOTE)

            val repaired = assertIs<JournalNote.Image>(notes.getNoteById(original.uid))
            assertEquals("APPLIED", database.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", original.uid.toString())?.state)
            assertContentEquals(bytes, media.readMedia(repaired.mediaRef).data)
            assertEquals(original.caption, repaired.caption)
            assertEquals(original.creationTimestamp, repaired.creationTimestamp)
            assertEquals(original.lastUpdated, repaired.lastUpdated)
            assertEquals("APPLIED", database.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", original.uid.toString())?.state)
            recovery.recover("synthetic", EntityType.NOTE)
            assertEquals(1, downloads)
        }
}
