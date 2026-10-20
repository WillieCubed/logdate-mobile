package app.logdate.client.sync.recovery

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.MediaPayload
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRecoveryRepository
import app.logdate.client.repository.journals.EntryEditBase
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.SyncMediaTransfer
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.metadata.KeyValueMediaSyncRefStore
import app.logdate.client.sync.metadata.MediaSyncRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NativeEntryDraftMediaRecoveryTest {
    private val database = DownloadInboxTest.Database()
    private var scope = DownloadScope("owner", "https://fixture.example")
    private var now = 1L
    private val inbox = DownloadInbox(database, database, { scope }, { now })
    private val refs = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { scope.origin }, { scope.owner })
    private var current = draft()
    private var deleted = false
    private var onDownload: () -> Unit = {}
    private var mayDownload = true
    private var downloads = 0
    private var storedReferences = StoredMediaReferences.Unchanged
    private val media =
        object : InMemoryMediaManager() {
            override suspend fun exists(mediaId: String): Boolean = super.exists(localFile(mediaId))

            override suspend fun readMedia(uri: String): MediaPayload = super.readMedia(localFile(uri))

            private fun localFile(reference: String): String = reference.replace("logdate-media://library/", "file:///tmp/")
        }
    private val repository =
        object : EntryDraftRecoveryRepository {
            override suspend fun getDraftSnapshotsForRecovery(): List<EntryDraft> = if (deleted) emptyList() else listOf(current)

            override suspend fun compareAndSetDraftForRecovery(
                expected: EntryDraft,
                replacement: EntryDraft,
                canCommit: () -> Boolean,
            ): Boolean {
                if (deleted || expected != current || !canCommit()) return false
                current = replacement
                return true
            }
        }
    private val cloud =
        object : CloudMediaDataSource {
            override suspend fun uploadMedia(
                accessToken: String,
                contentId: Uuid,
                media: MediaFileSource,
            ): Result<MediaUploadResult> = error("No uploads")

            override suspend fun downloadMedia(
                accessToken: String,
                mediaId: String,
            ): Result<MediaFile> {
                downloads++
                val id = current.notes.single().uid
                onDownload()
                return Result.success(MediaFile(id, "original.png", "image/png", 3, byteArrayOf(1, 2, 3)))
            }
        }

    private fun recovery() =
        NativeEntryDraftMediaRecovery(
            repository,
            inbox,
            SyncMediaTransfer(media, refs, cloud, mediaReferences = storedReferences, mayDownload = {
                mayDownload
            }),
            refs,
            storedReferences,
        )

    @Test
    fun `a recovered edit base uses the canonical reference stored by the journal`() =
        runTest {
            val original = current.notes.single() as JournalNote.Image
            val editedReference = media.saveMedia(MediaPayload("edited.png", "image/png", 3, byteArrayOf(4, 5, 6)))
            current = current.copy(notes = listOf(original.copy(mediaRef = editedReference)), editBase = EntryEditBase(original, null))
            mapping()
            storedReferences = StoredMediaReferences { it.replace("file:///tmp/", "logdate-media://library/") }

            recovery().recover("synthetic")

            val recoveredFile = requireNotNull(refs.get(original.uid)).localUri
            val baseReference = (requireNotNull(current.editBase).original as JournalNote.Image).mediaRef
            assertEquals(storedReferences.storedReference(recoveredFile), baseReference)
            assertContentEquals(byteArrayOf(1, 2, 3), media.readMedia(baseReference).data)
            assertEquals(editedReference, (current.notes.single() as JournalNote.Image).mediaRef)
            assertEquals("APPLIED", database.get(scope.owner, scope.origin, "MEDIA_NATIVE_ENTRY_DRAFT", current.id.toString())?.state)
        }

    private suspend fun mapping() {
        val image = current.notes.single() as JournalNote.Image
        refs.upsert(MediaSyncRef(image.uid.toString(), "content://source/42", "${scope.origin}/media/original", "original", 1))
    }

    @Test
    fun `base-only missing original recovers without changing the readable edited attachment`() =
        runTest {
            val original = current.notes.single() as JournalNote.Image
            val editedReference = media.saveMedia(MediaPayload("edited.png", "image/png", 3, byteArrayOf(4, 5, 6)))
            val copyId = Uuid.random()
            current =
                current.copy(
                    notes = listOf(original.copy(mediaRef = editedReference, caption = "Edited caption")),
                    editBase = EntryEditBase(original, JournalMergeScope(scope.owner, scope.origin), copyId),
                )
            val before = current
            mapping()

            recovery().recover("synthetic")

            val repairedBase = requireNotNull(current.editBase)
            assertEquals(before.notes, current.notes)
            assertEquals(before.createdAt, current.createdAt)
            assertEquals(before.updatedAt, current.updatedAt)
            assertEquals(before.editBase, repairedBase.copy(original = original))
            assertEquals(original.copy(mediaRef = (repairedBase.original as JournalNote.Image).mediaRef), repairedBase.original)
            assertEquals(copyId, repairedBase.copyNoteId)
            assertContentEquals(byteArrayOf(1, 2, 3), media.readMedia(requireNotNull(repairedBase.original.mediaRefOrNull())).data)
            assertEquals("APPLIED", database.get(scope.owner, scope.origin, "MEDIA_NATIVE_ENTRY_DRAFT", current.id.toString())?.state)
        }

    @Test
    fun `a changed edit base wins over recovery without losing the edited attachment`() =
        runTest {
            val original = current.notes.single() as JournalNote.Image
            val editedReference = media.saveMedia(MediaPayload("edited.png", "image/png", 3, byteArrayOf(4, 5, 6)))
            current = current.copy(notes = listOf(original.copy(mediaRef = editedReference)), editBase = EntryEditBase(original, null))
            mapping()
            val laterBase = original.copy(mediaRef = editedReference, caption = "Different baseline")
            onDownload = { current = current.copy(editBase = EntryEditBase(laterBase, null)) }

            recovery().recover("synthetic")

            assertEquals(laterBase, current.editBase?.original)
            assertEquals(editedReference, (current.notes.single() as JournalNote.Image).mediaRef)
            assertEquals(1, downloads)
            assertEquals(0, inbox.count())
        }

    @Test
    fun `current draft original recovers and retains a concurrent caption edit`() =
        runTest {
            mapping()
            val before = current
            onDownload =
                { current = current.copy(notes = listOf((current.notes.single() as JournalNote.Image).copy(caption = "Later caption"))) }
            recovery().recover("synthetic")
            val image = current.notes.single() as JournalNote.Image
            assertEquals("Later caption", image.caption)
            assertEquals(before.createdAt, current.createdAt)
            assertEquals(before.updatedAt, current.updatedAt)
            assertContentEquals(byteArrayOf(1, 2, 3), media.readMedia(image.mediaRef).data)
            assertEquals("APPLIED", database.get(scope.owner, scope.origin, "MEDIA_NATIVE_ENTRY_DRAFT", current.id.toString())?.state)
            recovery().recover("synthetic")
            assertEquals(1, downloads)
        }

    @Test
    fun `network deferral keeps the operation and cannot report complete`() =
        runTest {
            mapping()
            mayDownload = false
            recovery().recover("synthetic")
            val pending = database.get(scope.owner, scope.origin, "MEDIA_NATIVE_ENTRY_DRAFT", current.id.toString())
            assertEquals("NETWORK_POLICY", pending?.reason)
            assertNotEquals("APPLIED", pending?.state)
            assertEquals(1, inbox.count())
            assertEquals(0, downloads)
            mayDownload = true
            inbox.release()
            recovery().recover("synthetic")
            val applied = database.get(scope.owner, scope.origin, "MEDIA_NATIVE_ENTRY_DRAFT", current.id.toString())
            assertEquals(pending?.operationId, applied?.operationId)
            assertEquals("APPLIED", applied?.state)
        }

    @Test
    fun `missing mapping retains unavailable original and pending work`() =
        runTest {
            val original = current
            recovery().recover("synthetic")
            assertEquals(original, current)
            assertEquals(
                "MISSING_MEDIA",
                database.get(scope.owner, scope.origin, "MEDIA_NATIVE_ENTRY_DRAFT", current.id.toString())?.reason,
            )
            assertEquals(1, inbox.count())
        }

    @Test
    fun `deleted draft is not recreated by a completed download`() =
        runTest {
            mapping()
            onDownload = { deleted = true }
            recovery().recover("synthetic")
            assertEquals(emptyList(), repository.getDraftSnapshotsForRecovery())
            assertEquals(0, inbox.count())
        }

    @Test
    fun `account change cannot attach the original or acknowledge previous work`() =
        runTest {
            mapping()
            val original = current
            val captured = scope
            onDownload = { scope = DownloadScope("another", captured.origin) }
            assertFailsWith<CancellationException> { recovery().recover("synthetic") }
            assertEquals(original, current)
            assertNotEquals(
                "APPLIED",
                database.get(captured.owner, captured.origin, "MEDIA_NATIVE_ENTRY_DRAFT", current.id.toString())?.state,
            )
        }

    private fun draft(): EntryDraft {
        val time = Instant.fromEpochMilliseconds(1)
        return EntryDraft(
            Uuid.random(),
            listOf(
                JournalNote.Image(
                    creationTimestamp = time,
                    lastUpdated = time,
                    mediaRef = UnavailableMediaReferences.nativeRestore("content://source/42"),
                    caption = "Original caption",
                ),
            ),
            time,
            time,
        )
    }
}
