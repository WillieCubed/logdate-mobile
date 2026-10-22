package app.logdate.client.e2e

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.MainActivity
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.EntryEditConflictException
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRecoveryRepository
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.EntryEditBase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Cold-delivery simulation after native enrollment; only normal startup runs recovery. */
@RunWith(AndroidJUnit4::class)
class NativeCurrentDraftRecoveryProbeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val koin get() = GlobalContext.get()
    private val draftId = id(21)
    private val imageId = id(23)
    private val pendingId = id(32)
    private val marker get() = File(context.noBackupFilesDir, "native-current-draft-recovery.json")

    @Test
    fun currentDraftRecovery() =
        runBlocking {
            val stage = InstrumentationRegistry.getArguments().getString("nativeCurrentDraftStage")
            assumeTrue(stage != null)
            require(stage in setOf("prepare", "quarantine", "complete", "reopened", "base-prepare", "base-complete"))
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            require(context.packageName.endsWith(".debug"))
            val credentials = readNativeDeviceEnrollmentCredentials(context)
            require(credentials.sourceDevice != null)
            ensureLogDateApplicationStarted(context.applicationContext as Application)
            ActivityScenario.launch(MainActivity::class.java).use {
                koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
                assertEquals(credentials.origin, koin.get<LogDateConfigRepository>().getCurrentBackendUrl())
                report("configuration_loaded")
                val sessions = koin.get<SessionStorage>()
                withTimeout(30_000) { sessions.getSessionFlow().first { it?.accountId == credentials.owner } }
                report("session_loaded")
                val session = requireNotNull(sessions.getOriginBoundSession())
                assertEquals(credentials.origin, session.origin)
                val scope = UploadScope(credentials.owner, credentials.origin)
                if (stage == "base-prepare" || stage == "base-complete") {
                    recoverEditBase(stage, scope)
                    return@runBlocking
                }
                if (stage == "prepare") {
                    prepare(scope)
                    return@runBlocking
                }
                val expected = Json.decodeFromString<EntryDraft>(marker.readText())
                withTimeout(30_000) {
                    while (PostRestoreDetector(context).pendingMediaQuarantineId() != null) delay(50)
                }
                val media = koin.get<MediaManager>()
                val source = image(expected).mediaRef
                assertTrue(media.exists(source), "The destination provider must still contain the unrelated image")
                if (stage == "quarantine") {
                    val actual = snapshot()
                    assertEquals(source, UnavailableMediaReferences.nativeSource(image(actual).mediaRef))
                    assertFalse(media.exists(image(actual).mediaRef))
                    assertMetadata(expected, actual)
                } else {
                    withTimeout(120_000) {
                        while (true) {
                            val actual = snapshot()
                            val row =
                                koin.get<LogDateDatabase>().downloadInboxDao().get(
                                    scope.ownerId,
                                    scope.serverOrigin,
                                    "MEDIA_NATIVE_ENTRY_DRAFT",
                                    draftId.toString(),
                                )
                            if (row?.state == "APPLIED" && media.exists(image(actual).mediaRef)) break
                            delay(100)
                        }
                    }
                    val actual = snapshot()
                    assertNotEquals(source, image(actual).mediaRef)
                    assertEquals(digest(proof()), digest(media.readMedia(image(actual).mediaRef).data))
                    assertNotEquals(digest(proof()), digest(media.readMedia(source).data))
                    assertMetadata(expected, actual, allowSavedAtChange = stage == "reopened")
                    assertEquals(
                        image(actual).mediaRef,
                        koin.get<StoredMediaReferences>().storedReference(
                            requireNotNull(koin.get<MediaSyncRefStore>().getScoped(imageId, scope)).localUri,
                        ),
                    )
                }
                val recording = koin.get<AudioRecordingManager>()
                assertFalse(recording.isRecording)
                assertFalse(recording.isStartingRecording)
            }
        }

    private suspend fun prepare(scope: UploadScope) {
        require(!marker.exists())
        val drafts = koin.get<EntryDraftRepository>()
        val original = snapshot()
        val refs = koin.get<MediaSyncRefStore>()
        val uploaded = requireNotNull(refs.getScoped(imageId, scope))
        val uri =
            requireNotNull(
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "unrelated-destination-${Uuid.random()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LogDateRecoveryAcceptance")
                    },
                ),
            )
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(proof() + byteArrayOf(42)) }
        refs.upsert(uploaded.copy(noteId = imageId.toString(), localUri = uri.toString(), localAliases = emptyList()))
        val staging = File(context.cacheDir, "recordings/excluded-$pendingId.m4a")
        assertFalse(staging.exists())
        val pending =
            PendingMediaRecord(
                pendingId,
                PendingMediaType.AUDIO,
                original.createdAt,
                filePath = staging.absolutePath,
                timeZoneId = "America/Los_Angeles",
                position = 1,
                caption = "Retained recording caption",
                transcription = "Retained transcription",
            )
        drafts.saveDraftSnapshot(
            original.copy(
                notes = original.notes.map { if (it.uid == imageId) (it as JournalNote.Image).copy(mediaRef = uri.toString()) else it },
                pendingMedia = listOf(pending),
            ),
        )
        writeDurably(marker, Json.encodeToString(snapshot()))
        val quarantine = File(context.noBackupFilesDir, ".logdate_media_quarantine_pending")
        require(quarantine.createNewFile())
        writeDurably(quarantine, Uuid.random().toString())
    }

    private suspend fun recoverEditBase(
        stage: String,
        scope: UploadScope,
    ) {
        val id = id(40)
        val baseMarker = File(context.noBackupFilesDir, "native-current-draft-base.json")
        val notes = koin.get<JournalNotesRepository>()
        val drafts = koin.get<EntryDraftRepository>()
        if (stage == "base-prepare") {
            require(!baseMarker.exists())
            val original = notes.getNoteById(id(4)) as JournalNote.Image
            val stale =
                org.json
                    .JSONObject(
                        File(context.noBackupFilesDir, "native-automatic-media-restart.json").readText(),
                    ).getString("missing")
            require(UnavailableMediaReferences.nativeSource(stale) != null)
            val provenance = requireNotNull(koin.get<MediaSyncRefStore>().getScoped(id(4), scope))
            val source = requireNotNull(UnavailableMediaReferences.nativeSource(stale))
            assertTrue(
                (listOf(provenance.localUri) + provenance.localAliases).any {
                    it == stale || it == source || UnavailableMediaReferences.nativeSource(it) == source
                },
                "The base fixture must retain provenance for its earlier original reference",
            )
            val bytes = proof() + byteArrayOf(99)
            val replacement = koin.get<MediaManager>().saveMedia(MediaPayload("replacement.png", "image/png", bytes.size.toLong(), bytes))
            drafts.saveDraftSnapshot(
                EntryDraft(
                    id,
                    listOf(original.copy(mediaRef = replacement, caption = "Replacement attachment")),
                    original.creationTimestamp,
                    original.lastUpdated,
                    editBase =
                        EntryEditBase(
                            original.copy(mediaRef = stale),
                            notes.getNoteRecoveryScope(),
                        ),
                ),
            )
            writeDurably(baseMarker, Json.encodeToString(drafts.getDraft(id).first().getOrThrow()))
            return
        }
        val expected = Json.decodeFromString<EntryDraft>(baseMarker.readText())
        val actual =
            try {
                withTimeout(120_000) {
                    while (true) {
                        val current = drafts.getDraft(id).first().getOrThrow()
                        val row =
                            koin.get<LogDateDatabase>().downloadInboxDao().get(
                                scope.ownerId,
                                scope.serverOrigin,
                                "MEDIA_NATIVE_ENTRY_DRAFT",
                                id.toString(),
                            )
                        if (row?.state == "APPLIED") return@withTimeout current
                        delay(100)
                    }
                    @Suppress("UNREACHABLE_CODE")
                    error("Draft recovery did not finish")
                }
            } catch (failure: TimeoutCancellationException) {
                runCatching {
                    val row =
                        koin.get<LogDateDatabase>().downloadInboxDao().get(
                            scope.ownerId,
                            scope.serverOrigin,
                            "MEDIA_NATIVE_ENTRY_DRAFT",
                            id.toString(),
                        )
                    report(
                        org.json
                            .JSONObject()
                            .put(
                                "draftRecoveryState",
                                row?.state ?: "absent",
                            ).put("reason", row?.reason ?: "absent")
                            .toString(),
                    )
                }.exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            }
        val base = requireNotNull(actual.editBase)
        val committed = notes.getNoteById(id(4)) as JournalNote.Image
        assertEquals(committed.mediaRef, (base.original as JournalNote.Image).mediaRef)
        assertEquals(expected.editBase, base.copy(original = requireNotNull(expected.editBase).original))
        val priorOriginal = requireNotNull(expected.editBase).original as JournalNote.Image
        assertEquals(priorOriginal.copy(mediaRef = (base.original as JournalNote.Image).mediaRef), base.original)
        assertEquals(expected.notes, actual.notes)
        assertEquals(expected.copy(editBase = base), actual)
        assertNotEquals(committed.mediaRef, (actual.notes.single() as JournalNote.Image).mediaRef)
        val save = koin.get<AddNoteUseCase>()
        save(actual.notes, emptyList(), captureCurrentLocation = false, editBase = base)
        val saved = notes.getNoteById(id(4)) as JournalNote.Image
        val editedReference = (actual.notes.single() as JournalNote.Image).mediaRef
        assertEquals(koin.get<StoredMediaReferences>().storedReference(editedReference), saved.mediaRef)
        assertEquals(
            digest(koin.get<MediaManager>().readMedia(editedReference).data),
            digest(koin.get<MediaManager>().readMedia(saved.mediaRef).data),
        )
        assertEquals("Replacement attachment", saved.caption)
        assertEquals(committed.creationTimestamp, saved.creationTimestamp)
        assertFailsWith<EntryEditConflictException> {
            save(listOf(saved.copy(caption = "Conflicting later change")), emptyList(), captureCurrentLocation = false, editBase = base)
        }
        assertEquals(saved, notes.getNoteById(id(4)))
        assertEquals(actual, drafts.getDraft(id).first().getOrThrow())
    }

    private fun assertMetadata(
        expected: EntryDraft,
        actual: EntryDraft,
        allowSavedAtChange: Boolean = false,
    ) {
        val pending = actual.pendingMedia.single()
        val previous = expected.pendingMedia.single()
        assertEquals(previous.copy(filePath = pending.filePath), pending)
        assertEquals(previous.filePath, UnavailableMediaReferences.nativeSource(requireNotNull(pending.filePath)))
        assertFalse(File(requireNotNull(previous.filePath)).exists())
        val expectedImage = image(expected)
        assertEquals(
            expectedImage.copy(
                mediaRef = image(actual).mediaRef,
                lastUpdated = if (allowSavedAtChange) image(actual).lastUpdated else expectedImage.lastUpdated,
            ),
            image(actual),
        )
        if (!allowSavedAtChange) assertEquals(expected.updatedAt, actual.updatedAt)
        // Opening the editor may update saved-at time, but all retained content must agree.
        assertEquals(
            expected.copy(
                notes = actual.notes,
                pendingMedia = actual.pendingMedia,
                updatedAt = actual.updatedAt,
            ),
            actual,
        )
        assertEquals(expected.notes.map { it.uid }, actual.notes.map { it.uid })
        val expectedOther =
            expected.notes.filterNot { it.uid == imageId }.map { note ->
                if (allowSavedAtChange && note is JournalNote.Text) {
                    note.copy(lastUpdated = actual.notes.single { it.uid == note.uid }.lastUpdated)
                } else {
                    note
                }
            }
        assertEquals(expectedOther, actual.notes.filterNot { it.uid == imageId })
    }

    private suspend fun snapshot() = koin.get<EntryDraftRecoveryRepository>().getDraftSnapshotsForRecovery().single { it.id == draftId }

    private fun image(draft: EntryDraft) = draft.notes.single { it.uid == imageId } as JournalNote.Image

    private fun proof() =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("recovery/proof.png")
            .use { it.readBytes() }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toList()

    private fun id(number: Int) = Uuid.parse("00000000-0000-0000-0000-${number.toString().padStart(12, '0')}")

    private fun report(phase: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("currentDraftPhase", phase) })
    }

    private fun writeDurably(
        file: File,
        text: String,
    ) {
        FileOutputStream(file).use {
            it.write(text.encodeToByteArray())
            it.fd.sync()
        }
    }
}
