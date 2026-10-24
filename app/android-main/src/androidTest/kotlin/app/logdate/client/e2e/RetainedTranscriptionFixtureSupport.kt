package app.logdate.client.e2e

import app.logdate.client.data.restore.scheduleRetainedJournalReconciliation
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.entities.TranscriptionEntity
import app.logdate.client.database.entities.TranscriptionSegmentEntity
import app.logdate.client.database.entities.TranscriptionStatus
import app.logdate.client.device.restore.NativeRestoreDeliveryGate
import app.logdate.client.device.restore.retainedJournalWorkspace
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.media.storage.androidMediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Additional typed rows in the existing real late-key/worker/restart fixture. */
internal object RetainedTranscriptionFixtureSupport {
    private val koin get() = GlobalContext.get()
    private val database get() = koin.get<LogDateDatabase>()
    private val notes get() = koin.get<JournalNotesRepository>()

    suspend fun seed(
        state: JSONObject,
        directory: File,
    ) {
        val now = Instant.fromEpochMilliseconds(state.getLong("createdAt"))
        val journal = Uuid.parse(state.getString("journalId"))
        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val bytes = wave()
        val uri = koin.get<MediaManager>().saveMedia(MediaPayload("retained.wav", "audio/wav", bytes.size.toLong(), bytes))
        val ref = androidMediaFileResolver(context).storedReference(uri)
        state.put("transcriptMedia", ref)
        state.put("transcriptMediaSha256", RetainedJournalMergeFixtureSupport.sha256(bytes))
        for (kind in listOf("full", "conflict", "deleted")) {
            val note = JournalNote.Audio(uid = Uuid.random(), creationTimestamp = now, lastUpdated = now, mediaRef = ref, durationMs = 2)
            koin.get<app.logdate.client.sync.SyncTransactionManager>().withTransaction {
                notes.create(note, journal)
                val document = Json.encodeToString(TranscriptDocument.fromPlainText("source $kind words"))
                val row =
                    TranscriptionEntity(
                        note.uid,
                        "source $kind words",
                        document,
                        "fr-CA",
                        "IMPORTED",
                        "retained-engine",
                        3,
                        ref,
                        2,
                        true,
                        2,
                        TranscriptionStatus.COMPLETED,
                        null,
                        now,
                        now,
                    )
                database.transcriptionDao().insertTranscription(row)
                database.transcriptionDao().replaceSegmentsForNote(note.uid, listOf(segment(note.uid, "source $kind words")))
                state.put("transcript-$kind", note.uid.toString()).put("transcript-$kind-id", row.id.toString())
            }
        }
        val held = File(directory, "deferred-transcript-original")
        FileOutputStream(held).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        state.put("transcriptHeldOriginal", held.absolutePath)
        check(File(checkNotNull(androidMediaFileResolver(context).filePath(ref))).delete()) {
            "Omit only the owned fixture original so its real later delivery exercises media repair"
        }
    }

    fun removeWorkingOriginal(
        state: JSONObject,
        sources: Map<String, java.io.File>,
    ) {
        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val ref = state.getString("transcriptMedia")
        val relative = "files/media/" + ref.removePrefix("logdate-media://library/")
        assertFalse(sources.containsKey(relative), "Initial retained source deliberately has no audio original")
        val working = File(checkNotNull(androidMediaFileResolver(context).filePath(ref)))
        assertFalse(working.exists())
    }

    suspend fun createWorkingConflictAndDelete(state: JSONObject) {
        val now = Instant.fromEpochMilliseconds(state.getLong("createdAt"))
        val ref = state.getString("transcriptMedia")
        val journal = Uuid.parse(state.getString("journalId"))
        for (kind in listOf("conflict", "deleted")) {
            val id = Uuid.parse(state.getString("transcript-$kind"))
            koin.get<app.logdate.client.sync.SyncTransactionManager>().withTransaction {
                notes.create(
                    JournalNote.Audio(uid = id, creationTimestamp = now, lastUpdated = now, mediaRef = ref, durationMs = 2),
                    journal,
                )
                assertTrue(
                    koin.get<TranscriptionRepository>().acceptSyncedTranscript(id, TranscriptDocument.fromPlainText("working $kind words")),
                )
            }
        }
        val deleted = Uuid.parse(state.getString("transcript-deleted"))
        assertTrue(koin.get<TranscriptionRepository>().deleteTranscription(deleted))
        assertNull(database.transcriptionDao().getTranscriptionByNoteId(deleted))
        state.put(
            "workingTranscriptId",
            checkNotNull(
                database.transcriptionDao().getTranscriptionByNoteId(Uuid.parse(state.getString("transcript-conflict"))),
            ).id.toString(),
        )
    }

    suspend fun assertRecovered(state: JSONObject) {
        withTimeout(60_000) {
            while (database.transcriptionDao().getAllTranscriptions().none { it.text == "source full words" } ||
                database.transcriptionDao().getAllTranscriptions().none { it.text == "source conflict words" }
            ) {
                delay(100)
            }
        }
        if (!state.optBoolean("transcriptOriginalDelivered")) deliverOriginalAndAssertMetadata(state)
        val rows = database.transcriptionDao().getAllTranscriptions()
        for (kind in listOf("full", "conflict")) {
            val row = rows.single { it.text == "source $kind words" }
            val source = Uuid.parse(state.getString("transcript-$kind"))
            if (kind == "conflict") assertTrue(row.noteId != source) else assertEquals(source, row.noteId)
            assertEquals(state.getString("transcript-$kind-id"), row.id.toString())
            assertEquals("fr-CA", row.language)
            assertEquals("retained-engine", row.modelId)
            assertEquals(3, row.revision)
            assertEquals(2, row.speakerCount)
            assertTrue(row.isCloudEnhanced)
            assertEquals(TranscriptionStatus.COMPLETED, row.status)
            assertEquals(Instant.fromEpochMilliseconds(state.getLong("createdAt")), row.created)
            val segments = database.transcriptionDao().getSegmentsByNoteId(row.noteId)
            assertEquals(listOf(segment(row.noteId, "source $kind words")), segments)
            val note = assertNotNull(notes.getNoteById(row.noteId) as? JournalNote.Audio)
            val media = koin.get<MediaManager>().readMedia(note.mediaRef)
            assertEquals(state.getString("transcriptMediaSha256"), RetainedJournalMergeFixtureSupport.sha256(media.data))
            assertTrue(notes.getAllJournalNoteLinks().any { it.second == row.noteId })
        }
        val conflict = Uuid.parse(state.getString("transcript-conflict"))
        assertEquals("working conflict words", database.transcriptionDao().getTranscriptionByNoteId(conflict)?.text)
        assertEquals(
            state.getString("workingTranscriptId"),
            database
                .transcriptionDao()
                .getTranscriptionByNoteId(conflict)
                ?.id
                .toString(),
        )
        val deleted = Uuid.parse(state.getString("transcript-deleted"))
        assertNull(database.transcriptionDao().getTranscriptionByNoteId(deleted))
        assertTrue(database.transcriptionDao().getSegmentsByNoteId(deleted).isEmpty())
        assertFalse(rows.any { it.text == "source deleted words" }, "A settled transcript-only deletion must not reappear as a variant")
        assertEquals(1, rows.count { it.text == "source full words" })
        assertEquals(1, rows.count { it.text == "source conflict words" })
        val all = notes.allNotesObserved.first()
        assertTrue(all.any { it.uid == deleted })
        assertEquals(
            4,
            all.filterIsInstance<JournalNote.Audio>().size,
            "Transcript deletion and URI normalization must not create extra audio variants",
        )
    }

    private suspend fun deliverOriginalAndAssertMetadata(state: JSONObject) {
        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val before =
            database.transcriptionDao().getAllTranscriptions().filter {
                it.text in
                    setOf("source full words", "source conflict words")
            }
        assertEquals(2, before.size)
        val segments = before.associate { it.id to database.transcriptionDao().getSegmentsByNoteId(it.noteId) }
        before.forEach { row ->
            assertTrue(
                UnavailableMediaReferences.isUnavailable(assertNotNull(notes.getNoteById(row.noteId) as? JournalNote.Audio).mediaRef),
            )
        }
        val held = File(state.getString("transcriptHeldOriginal"))
        assertEquals(state.getString("transcriptMediaSha256"), RetainedJournalMergeFixtureSupport.sha256(held.readBytes()))
        val workspace = retainedJournalWorkspace(context)
        val delivery = Uuid.random().toString()
        koin.get<NativeRestoreDeliveryGate>().withLock {
            workspace.beginIncomingDelivery(delivery)
            held.inputStream().use { input ->
                workspace.captureIncomingFile(
                    delivery,
                    "files/media/" + state.getString("transcriptMedia").removePrefix("logdate-media://library/"),
                    held.length(),
                    input,
                )
            }
            workspace.finishIncomingDelivery()
        }
        assertTrue(scheduleRetainedJournalReconciliation(context))
        withTimeout(60_000) {
            while (before.any { row ->
                    val note = notes.getNoteById(row.noteId) as? JournalNote.Audio
                    note == null || UnavailableMediaReferences.isUnavailable(note.mediaRef)
                }
            ) {
                delay(100)
            }
        }
        for (original in before) {
            val recovered = assertNotNull(database.transcriptionDao().getTranscriptionByNoteId(original.noteId))
            assertEquals(original.copy(mediaUri = recovered.mediaUri), recovered, "Late media may rebind only the URI")
            assertEquals(segments.getValue(original.id), database.transcriptionDao().getSegmentsByNoteId(original.noteId))
        }
        assertTrue(workspace.coherentIncomingPayloads().containsKey(delivery))
        state.put("transcriptOriginalDelivered", true).put("transcriptOriginalDelivery", delivery)
        check(held.delete())
    }

    private fun segment(
        id: Uuid,
        text: String,
    ) = TranscriptionSegmentEntity(id, "retained-segment", text, 0, 2, "speaker", .7f, "IMPORTED", true, 3)

    private fun wave(): ByteArray {
        val samples = ByteArray(32)
        return ByteBuffer
            .allocate(44 + samples.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".encodeToByteArray())
                putInt(36 + samples.size)
                put("WAVEfmt ".encodeToByteArray())
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(8000)
                putInt(16000)
                putShort(2)
                putShort(16)
                put("data".encodeToByteArray())
                putInt(samples.size)
                put(samples)
            }.array()
    }
}
