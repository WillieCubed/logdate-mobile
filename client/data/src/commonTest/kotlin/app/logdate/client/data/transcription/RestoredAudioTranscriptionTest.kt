package app.logdate.client.data.transcription

import app.logdate.client.data.fakes.FakeAudioNoteDao
import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.entities.TranscriptionEntity
import app.logdate.client.database.entities.TranscriptionStatus
import app.logdate.client.media.audio.transcription.TranscriptionPriority
import app.logdate.client.media.storage.UnavailableMediaReferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class RestoredAudioTranscriptionTest {
    @Test
    fun `transcription waits for the original without changing a retained transcript`() =
        runTest {
            for (reference in listOf(
                UnavailableMediaReferences.nativeRestore("content://media/external/audio/media/123"),
                UnavailableMediaReferences.archive("original"),
            )) {
                val now = Instant.fromEpochMilliseconds(1)
                val note = AudioNoteEntity(contentUri = reference, created = now, lastUpdated = now)
                val notes = FakeAudioNoteDao().apply { addNote(note) }
                val retained =
                    TranscriptionEntity(
                        noteId = note.uid,
                        text = "Retained words",
                        documentJson = "{}",
                        status = TranscriptionStatus.COMPLETED,
                        mediaUri = reference,
                        created = now,
                        lastUpdated = now,
                    )
                val transcriptions = FakeTranscriptionDao().apply { insertTranscription(retained) }
                val manager = FakeTranscriptionManager(true)
                val repository = OfflineFirstTranscriptionRepository(transcriptions, notes, manager)
                repository.startAutomaticTranscriptions(backgroundScope)
                runCurrent()
                assertEquals(0, manager.enqueueCount)
                assertFalse(repository.requestTranscription(note.uid))
                assertNull(repository.beginTranscription(note.uid, reference))
                assertEquals(retained, transcriptions.getTranscriptionByNoteId(note.uid))
                notes.addNote(note.copy(contentUri = "logdate-media://recordings/${Uuid.random()}.m4a"))
                runCurrent()
                assertEquals(1, manager.enqueueCount)
            }
        }

    @Test
    fun `failed and active transcripts resume once after atomic original rebinding`() =
        runTest {
            for (status in listOf(TranscriptionStatus.FAILED, TranscriptionStatus.IN_PROGRESS)) {
                val now = Instant.fromEpochMilliseconds(1)
                val missing = UnavailableMediaReferences.nativeRestore("content://media/external/audio/media/123")
                val note = AudioNoteEntity(contentUri = missing, created = now, lastUpdated = now)
                val notes = FakeAudioNoteDao().apply { addNote(note) }
                val transcriptions =
                    FakeTranscriptionDao().apply {
                        insertTranscription(
                            TranscriptionEntity(
                                noteId = note.uid,
                                text = "Retained words",
                                status = status,
                                mediaUri = missing,
                                created = now,
                                lastUpdated = now,
                            ),
                        )
                    }
                val manager = FakeTranscriptionManager(true)
                val repository = OfflineFirstTranscriptionRepository(transcriptions, notes, manager, now = { now })
                repository.startAutomaticTranscriptions(backgroundScope)
                runCurrent()
                assertEquals(0, manager.enqueueCount)
                val recovered = "logdate-media://recordings/${Uuid.random()}.m4a"
                notes.addNote(note.copy(contentUri = recovered))
                repository.rebindMediaReference(note.uid, missing, recovered)
                runCurrent()
                assertEquals(1, manager.enqueueCount, "Recovered $status recording must resume")
                assertEquals(listOf(TranscriptionPriority.RECOVERY), manager.priorities)
                runCurrent()
                assertEquals(1, manager.enqueueCount)
            }
        }

    @Test
    fun `completed transcript is retained when its original becomes available`() =
        runTest {
            val now = Instant.fromEpochMilliseconds(1)
            val missing = UnavailableMediaReferences.archive("recording")
            val note = AudioNoteEntity(contentUri = missing, created = now, lastUpdated = now)
            val notes = FakeAudioNoteDao().apply { addNote(note) }
            val transcriptions = FakeTranscriptionDao()
            val manager = FakeTranscriptionManager(true)
            val repository = OfflineFirstTranscriptionRepository(transcriptions, notes, manager, now = { now })
            repository.acceptSyncedTranscript(
                note.uid,
                app.logdate.client.repository.transcription.TranscriptDocument
                    .fromPlainText("Retained words"),
            )
            val retained = transcriptions.getTranscriptionByNoteId(note.uid)
            repository.startAutomaticTranscriptions(backgroundScope)
            runCurrent()
            val recovered = "logdate-media://recordings/${Uuid.random()}.m4a"
            notes.addNote(note.copy(contentUri = recovered))
            repository.rebindMediaReference(note.uid, missing, recovered)
            runCurrent()
            assertEquals(0, manager.enqueueCount)
            assertEquals(retained?.copy(mediaUri = recovered), transcriptions.getTranscriptionByNoteId(note.uid))
        }
}
