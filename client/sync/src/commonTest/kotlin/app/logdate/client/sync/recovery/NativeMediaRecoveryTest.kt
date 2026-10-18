package app.logdate.client.sync.recovery

import app.logdate.client.media.ConditionalMediaSaver
import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.MediaPayload
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
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableImageBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NativeMediaRecoveryTest {
    private val db = DownloadInboxTest.Database()
    private val scope = DownloadScope("owner", "origin")
    private var epochMillis = 1L
    private var selectedScope = scope

    private fun newInbox() = DownloadInbox(db, db, { selectedScope }, { epochMillis })

    private val inbox = newInbox()
    private val notes = FakeJournalNotesRepository()
    private val drafts = FakeJournalRepository()
    private val media =
        object : InMemoryMediaManager(), ConditionalMediaSaver {
            var onSave: suspend () -> Unit = {}
            var afterScopeValidation: suspend () -> Unit = {}

            override suspend fun saveMedia(payload: MediaPayload): String {
                onSave()
                return super.saveMedia(payload)
            }

            override suspend fun saveMedia(
                payload: MediaPayload,
                canCommit: () -> Boolean,
            ): String {
                onSave()
                if (!canCommit()) throw CancellationException("Media hydration scope changed")
                afterScopeValidation()
                return super.saveMedia(payload)
            }
        }
    private val refStorage = InMemoryKeyValueStorage()

    private fun newRefs() = KeyValueMediaSyncRefStore(refStorage, { selectedScope.origin }, { selectedScope.owner })

    private val refs = newRefs()
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

    private fun recovery(): NativeMediaRecovery {
        val restoredRefs = newRefs()
        return NativeMediaRecovery(newInbox(), notes, drafts, media, SyncMediaTransfer(media, restoredRefs, cloud), restoredRefs, db)
    }

    @Test
    fun `caption edits during download keep the recovered original without another network attempt`() =
        runTest {
            val note = JournalNote.Image(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = local, caption = "Before")
            notes.create(note)
            owner = note.uid
            refs.upsert(mapping(note.uid))
            onDownload =
                {
                    notes.updateSyncMetadata(
                        note.copy(caption = "Edited during recovery", lastUpdated = Instant.fromEpochMilliseconds(2)),
                        0,
                        timestamp,
                    )
                }
            recovery().recover("token", EntityType.NOTE)
            val repaired = assertIs<JournalNote.Image>(notes.getNoteById(note.uid))
            assertEquals("Edited during recovery", repaired.caption)
            assertTrue(media.exists(repaired.mediaRef))
            unavailable = true
            recovery().recover("token", EntityType.NOTE)
            assertEquals(1, downloads.size)
        }

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
    fun `low storage keeps missing original retryable without replacing its reference`() =
        runTest {
            val note = JournalNote.Image(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = local)
            notes.create(note)
            owner = note.uid
            refs.upsert(mapping(note.uid))
            media.onSave = { error("No space left on device") }

            recovery().recover("token", EntityType.NOTE)

            val failed = requireNotNull(db.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString()))
            assertEquals("FAILED", failed.state)
            assertEquals("LOCAL_STORAGE", failed.reason)
            assertEquals(1, failed.attempts)
            assertEquals(note, notes.getNoteById(note.uid))
            assertFalse(media.exists("file:///tmp/original.jpg"))

            media.onSave = {}
            epochMillis = failed.nextAttemptAt
            recovery().recover("token", EntityType.NOTE)

            val repaired = assertIs<JournalNote.Image>(notes.getNoteById(note.uid))
            assertTrue(media.exists(repaired.mediaRef))
            assertEquals("APPLIED", db.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString())?.state)
            assertEquals(2, downloads.size, "A retry after space becomes available should fetch and save the original")
        }

    @Test
    fun `low storage keeps unfinished draft media retryable without changing the draft`() =
        runTest {
            val block = SerializableImageBlock(Uuid.random(), timestamp, uri = local, caption = "Keep this draft")
            val draft = EditorDraft(Uuid.random(), listOf(block), createdAt = timestamp, lastModifiedAt = timestamp)
            owner = block.id
            drafts.saveDraftFromSync(draft)
            refs.upsertDraftAsset(draft.id, block.id, "image", mapping(block.id))
            media.onSave = { error("No space left on device") }

            recovery().recover("token", EntityType.DRAFT)

            val failed = requireNotNull(db.get(scope.owner, scope.origin, "MEDIA_NATIVE_DRAFT", draft.id.toString()))
            assertEquals("FAILED", failed.state)
            assertEquals("LOCAL_STORAGE", failed.reason)
            assertEquals(draft, drafts.getDraft(draft.id))
            assertFalse(media.exists("file:///tmp/original.jpg"))

            media.onSave = {}
            epochMillis = failed.nextAttemptAt
            recovery().recover("token", EntityType.DRAFT)

            val repaired = requireNotNull(drafts.getDraft(draft.id))
            assertTrue(media.exists(requireNotNull(assertIs<SerializableImageBlock>(repaired.blocks.single()).uri)))
            assertEquals("APPLIED", db.get(scope.owner, scope.origin, "MEDIA_NATIVE_DRAFT", draft.id.toString())?.state)
            assertEquals(2, downloads.size, "A retry after space becomes available should fetch and save the original")
        }

    @Test
    fun `a quarantined content URI retries across recovery instances and scopes without crossing scopes`() =
        runTest {
            val source = "content://media/external/images/media/123"
            val quarantined = UnavailableMediaReferences.nativeRestore(source)
            val note = JournalNote.Image(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = quarantined)
            notes.create(note)
            owner = note.uid
            refs.upsert(mapping(note.uid).copy(localUri = source, localAliases = listOf(quarantined)))
            unavailable = true

            recovery().recover("token", EntityType.NOTE)

            val pending = requireNotNull(db.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString()))
            assertEquals("UNKNOWN", pending.reason)
            assertEquals(quarantined, assertIs<JournalNote.Image>(notes.getNoteById(note.uid)).mediaRef)

            unavailable = false
            selectedScope = DownloadScope("replacement-owner", "replacement-origin")
            epochMillis = pending.nextAttemptAt
            recovery().recover("token", EntityType.NOTE)

            val replacementRow = requireNotNull(db.get(selectedScope.owner, selectedScope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString()))
            assertEquals("MISSING_MEDIA", replacementRow.reason)
            assertEquals(1, downloads.size, "The replacement account must not use the old account's cached media mapping")
            assertEquals("FAILED", db.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString())?.state)

            selectedScope = scope
            epochMillis = pending.nextAttemptAt
            recovery().recover("token", EntityType.NOTE)

            val repaired = assertIs<JournalNote.Image>(notes.getNoteById(note.uid))
            assertTrue(media.exists(repaired.mediaRef), "repaired reference=${repaired.mediaRef}, downloads=$downloads")
            assertEquals("APPLIED", db.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString())?.state)
            assertEquals(2, downloads.size)
        }

    @Test
    fun `an account switch during a media download leaves the quarantined note and retry row untouched`() =
        runTest {
            val source = "content://media/external/images/media/456"
            val quarantined = UnavailableMediaReferences.nativeRestore(source)
            val note = JournalNote.Image(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = quarantined)
            notes.create(note)
            owner = note.uid
            refs.upsert(mapping(note.uid).copy(localUri = source, localAliases = listOf(quarantined)))

            val downloadStarted = CompletableDeferred<Unit>()
            val finishDownload = CompletableDeferred<Unit>()
            onDownload = {
                downloadStarted.complete(Unit)
                finishDownload.await()
            }
            val inFlight = async { recovery().recover("token", EntityType.NOTE) }

            downloadStarted.await()
            selectedScope = DownloadScope("replacement-owner", "replacement-origin")
            finishDownload.complete(Unit)

            assertFailsWith<CancellationException> { inFlight.await() }
            assertEquals(quarantined, assertIs<JournalNote.Image>(notes.getNoteById(note.uid)).mediaRef)
            assertEquals("WAITING", db.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString())?.state)
            assertNull(refs.getScoped(note.uid, UploadScope("replacement-owner", "replacement-origin")))
            assertFalse(media.exists("file:///tmp/original.jpg"), "A late old-scope response must not leave a downloaded original behind")
        }

    @Test
    fun `an account switch during durable media save does not commit old-scope bytes`() =
        runTest {
            val source = "content://media/external/images/media/789"
            val quarantined = UnavailableMediaReferences.nativeRestore(source)
            val note = JournalNote.Image(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = quarantined)
            notes.create(note)
            owner = note.uid
            refs.upsert(mapping(note.uid).copy(localUri = source, localAliases = listOf(quarantined)))

            val saveStarted = CompletableDeferred<Unit>()
            val finishSave = CompletableDeferred<Unit>()
            media.onSave = {
                saveStarted.complete(Unit)
                finishSave.await()
            }
            val inFlight = async { recovery().recover("token", EntityType.NOTE) }

            saveStarted.await()
            selectedScope = DownloadScope("replacement-owner", "replacement-origin")
            finishSave.complete(Unit)

            assertFailsWith<CancellationException> { inFlight.await() }
            assertFalse(media.exists("file:///tmp/original.jpg"), "Old-scope media must not become durable after an account switch")
            assertEquals(quarantined, assertIs<JournalNote.Image>(notes.getNoteById(note.uid)).mediaRef)
            assertEquals("WAITING", db.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString())?.state)
        }

    @Test
    fun `an account switch during draft media save does not commit old-scope bytes`() =
        runTest {
            val source = "content://media/external/images/media/890"
            val quarantined = UnavailableMediaReferences.nativeRestore(source)
            val block = SerializableImageBlock(Uuid.random(), timestamp, uri = quarantined, caption = "Retained")
            val draft = EditorDraft(Uuid.random(), listOf(block), createdAt = timestamp, lastModifiedAt = timestamp)
            owner = block.id
            drafts.saveDraftFromSync(draft)
            refs.upsertDraftAsset(
                draft.id,
                block.id,
                "image",
                mapping(block.id).copy(localUri = source, localAliases = listOf(quarantined)),
            )

            val saveStarted = CompletableDeferred<Unit>()
            val finishSave = CompletableDeferred<Unit>()
            media.onSave = {
                saveStarted.complete(Unit)
                finishSave.await()
            }
            val inFlight = async { recovery().recover("token", EntityType.DRAFT) }

            saveStarted.await()
            selectedScope = DownloadScope("replacement-owner", "replacement-origin")
            finishSave.complete(Unit)

            assertFailsWith<CancellationException> { inFlight.await() }
            assertFalse(media.exists("file:///tmp/original.jpg"), "Old-scope media must not become durable after an account switch")
            assertEquals(draft, drafts.getDraft(draft.id))
            assertEquals("WAITING", db.get(scope.owner, scope.origin, "MEDIA_NATIVE_DRAFT", draft.id.toString())?.state)
            assertNull(refs.getDraftAssetScoped(draft.id, block.id, "image", UploadScope("replacement-owner", "replacement-origin")))
        }

    @Test
    fun `a scope switch after media commit finishes recovery under the captured scope`() =
        runTest {
            val source = "content://media/external/images/media/101"
            val quarantined = UnavailableMediaReferences.nativeRestore(source)
            val note = JournalNote.Image(creationTimestamp = timestamp, lastUpdated = timestamp, mediaRef = quarantined)
            notes.create(note)
            owner = note.uid
            refs.upsert(mapping(note.uid).copy(localUri = source, localAliases = listOf(quarantined)))

            val commitValidated = CompletableDeferred<Unit>()
            val finishCommit = CompletableDeferred<Unit>()
            media.afterScopeValidation = {
                commitValidated.complete(Unit)
                finishCommit.await()
            }
            val inFlight = async { recovery().recover("token", EntityType.NOTE) }

            commitValidated.await()
            selectedScope = DownloadScope("replacement-owner", "replacement-origin")
            finishCommit.complete(Unit)

            inFlight.await()
            val repaired = assertIs<JournalNote.Image>(notes.getNoteById(note.uid))
            assertTrue(media.exists(repaired.mediaRef))
            assertEquals("APPLIED", db.get(scope.owner, scope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString())?.state)
            assertNull(db.get(selectedScope.owner, selectedScope.origin, "MEDIA_NATIVE_NOTE", note.uid.toString()))
            assertNull(refs.getScoped(note.uid, UploadScope("replacement-owner", "replacement-origin")))
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
