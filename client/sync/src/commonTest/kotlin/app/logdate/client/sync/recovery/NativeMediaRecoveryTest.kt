package app.logdate.client.sync.recovery

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
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
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableImageBlock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NativeMediaRecoveryTest {
    private val db = DownloadInboxTest.Database()
    private val scope = DownloadScope("owner", "origin")
    private val inbox = DownloadInbox(db, db, { scope }, { 1L })
    private val notes = FakeJournalNotesRepository()
    private val drafts = FakeJournalRepository()
    private val media = InMemoryMediaManager()
    private val refs = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { "origin" }, { "owner" })
    private val timestamp = Instant.fromEpochMilliseconds(1)
    private val local = "logdate-media://library/source.jpg"
    private val downloads = mutableListOf<String>()
    private var owner = Uuid.random()
    private var unavailable = false
    private var onDownload: suspend () -> Unit = {}
    private val cloud =
        object : CloudMediaDataSource {
            override suspend fun uploadMedia(
                accessToken: String,
                contentId: Uuid,
                media: MediaFileSource,
            ): Result<MediaUploadResult> = error("no upload")

            override suspend fun downloadMedia(
                accessToken: String,
                mediaId: String,
            ): Result<MediaFile> {
                downloads += mediaId
                onDownload()
                return if (unavailable) {
                    Result.failure(
                        IllegalStateException("offline"),
                    )
                } else {
                    Result.success(
                        MediaFile(owner, "$mediaId.jpg", "image/jpeg", 1, byteArrayOf(1)),
                    )
                }
            }
        }

    private fun recovery() = NativeMediaRecovery(inbox, notes, drafts, media, SyncMediaTransfer(media, refs, cloud), refs, db)

    @Test
    fun `a native journal with settled download rows recovers missing originals`() =
        runTest {
            val note =
                JournalNote.Image(
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                    mediaRef = local,
                    caption = "Retained caption",
                )
            owner = note.uid
            notes.create(note)
            refs.upsert(mapping(note.uid))
            inbox.stage("NOTE", 1, listOf(WireDownload(note.uid.toString(), 1, false, "")))
            inbox.applied("NOTE", note.uid.toString(), 1)
            inbox.stage("MEDIA_NOTE", 1, listOf(WireDownload(note.uid.toString(), 1, false, "")))
            inbox.applied("MEDIA_NOTE", note.uid.toString(), 1)
            recovery().recover("token", EntityType.NOTE)
            val repaired = assertIs<JournalNote.Image>(notes.getNoteById(note.uid))
            assertTrue(media.exists(repaired.mediaRef))
            assertEquals("Retained caption", repaired.caption)
            assertEquals(listOf("original"), downloads)
            recovery().recover("token", EntityType.NOTE)
            assertEquals(1, downloads.size)
        }

    @Test
    fun `a native draft with no download row recovers without changing its modification time`() =
        runTest {
            val block = SerializableImageBlock(Uuid.random(), timestamp, uri = local, caption = "Retained")
            val draft = EditorDraft(Uuid.random(), listOf(block), createdAt = timestamp, lastModifiedAt = timestamp)
            owner = block.id
            drafts.saveDraftFromSync(draft)
            refs.upsertDraftAsset(draft.id, block.id, "image", mapping(block.id))
            recovery().recover("token", EntityType.DRAFT)
            val repaired = requireNotNull(drafts.getDraft(draft.id))
            assertTrue(media.exists(requireNotNull(assertIs<SerializableImageBlock>(repaired.blocks.single()).uri)))
            assertEquals(timestamp, repaired.lastModifiedAt)
        }

    @Test
    fun `missing originals remain durable pending work across recovery instances`() =
        runTest {
            val note = JournalNote.Image(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = local)
            notes.create(note)
            recovery().recover("token", EntityType.NOTE)
            assertEquals(note, notes.getNoteById(note.uid))
            val row = requireNotNull(db.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString()))
            assertEquals("MISSING_MEDIA", row.reason)
            assertTrue(row.state != "APPLIED")
            recovery().recover("token", EntityType.NOTE)
            assertEquals(row.attempts, db.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString())?.attempts)
        }

    @Test
    fun `an editor caption change during a legacy media download remains recoverable on retry`() =
        runTest {
            val legacy = "file:///old-install/audio_notes/source.jpg"
            val block = SerializableImageBlock(Uuid.random(), timestamp, uri = legacy, caption = "Original")
            val original = EditorDraft(Uuid.random(), listOf(block), createdAt = timestamp, lastModifiedAt = timestamp)
            val edited =
                original.copy(
                    blocks = listOf(block.copy(caption = "Edited during download")),
                    lastModifiedAt = Instant.fromEpochMilliseconds(2),
                )
            owner = block.id
            drafts.saveDraftFromSync(original)
            refs.upsertDraftAsset(
                original.id,
                block.id,
                "image",
                mapping(block.id).copy(
                    localUri = "file:///previously-downloaded.jpg",
                    localAliases =
                        listOf(legacy) + (1..7).map { "file:///older-$it.jpg" },
                ),
            )
            onDownload = {
                drafts.saveDraftFromSync(edited)
                onDownload = {}
            }
            recovery().recover("token", EntityType.DRAFT)
            assertEquals(edited, drafts.getDraft(original.id))
            recovery().recover("token", EntityType.DRAFT)
            val repaired = requireNotNull(drafts.getDraft(original.id))
            val repairedBlock = assertIs<SerializableImageBlock>(repaired.blocks.single())
            assertTrue(media.exists(requireNotNull(repairedBlock.uri)))
            assertEquals("Edited during download", repairedBlock.caption)
            assertEquals(edited.lastModifiedAt, repaired.lastModifiedAt)
            assertEquals(1, downloads.size)
        }

    private fun mapping(id: Uuid) = MediaSyncRef(id.toString(), local, "https://example.invalid/media/original", "original", 1)
}
