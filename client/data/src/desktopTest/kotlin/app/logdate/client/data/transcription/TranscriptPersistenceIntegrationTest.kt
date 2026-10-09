package app.logdate.client.data.transcription

import androidx.room.Room
import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptEngineMetadata
import app.logdate.client.repository.transcription.TranscriptSegment
import app.logdate.client.repository.transcription.TranscriptSource
import app.logdate.client.repository.transcription.TranscriptSpeaker
import app.logdate.client.repository.transcription.TranscriptWord
import app.logdate.client.sync.RoomSyncTransactionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptPersistenceIntegrationTest {
    @Test
    fun `hydrating downloaded audio keeps its final transcript bound to the local file`() =
        runTest {
            val file = Files.createTempFile("logdate-transcript-hydration-", ".db")
            val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
            try {
                val manager = FakeTranscriptionManager(true)
                val transactions = RoomSyncTransactionManager(database)
                val transcripts =
                    OfflineFirstTranscriptionRepository(
                        database.transcriptionDao(),
                        database.audioNoteDao(),
                        manager,
                        transactionManager = transactions,
                    )
                val notes =
                    OfflineFirstJournalNotesRepository(
                        database.textNoteDao(),
                        database.imageNoteDao(),
                        database.audioNoteDao(),
                        database.videoNoteDao(),
                        database.journalContentDao(),
                        FakeJournalRepository(),
                        database.mediaCaptionDao(),
                        transactionManager = transactions,
                        syncMetadataService = FakeSyncMetadataService(),
                        transcriptionRepository = transcripts,
                        syncScope = backgroundScope,
                    )
                val now = Instant.parse("2026-10-08T12:00:00Z")
                val document = TranscriptDocument.fromPlainText("Retained downloaded words")
                val note =
                    JournalNote.Audio(
                        "https://example.test/audio.m4a",
                        creationTimestamp = now,
                        lastUpdated = now,
                        transcript = document,
                    )
                notes.createFromSync(note)
                notes.updateMediaRef(note.uid, "/local/audio.m4a")
                runCurrent()
                assertEquals(document, (notes.getNoteById(note.uid) as JournalNote.Audio).transcript)
                assertEquals("/local/audio.m4a", database.transcriptionDao().getTranscriptionByNoteId(note.uid)?.mediaUri)
                assertEquals(0, manager.enqueueCount)
            } finally {
                backgroundScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
                database.close()
                Files.deleteIfExists(file)
                Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
                Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
            }
        }

    @Test
    fun `accepted transcript survives database reopen with timed search index and metadata`() =
        runTest {
            val file = Files.createTempFile("logdate-transcript-", ".db")
            var database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
            val now = Instant.parse("2026-10-08T12:00:00Z")
            val note = AudioNoteEntity(contentUri = "memo.m4a", created = now, lastUpdated = now, durationMs = 4000)
            val document =
                TranscriptDocument(
                    revision = 17,
                    status = TranscriptDocumentStatus.FINAL,
                    engine = TranscriptEngineMetadata("local", "recognizer", "1"),
                    speakers = listOf(TranscriptSpeaker("speaker-1", "Speaker 1", "primary")),
                    segments =
                        listOf(
                            TranscriptSegment(
                                segmentId = "segment-1",
                                text = "Coastal adventures",
                                startMs = 1200,
                                endMs = 2800,
                                words = listOf(TranscriptWord(text = "Coastal", normalizedText = "coastal", startMs = 1200, endMs = 1800)),
                                speakerId = "speaker-1",
                                confidence = 0.9f,
                                source = TranscriptSource.LOCAL_REFINEMENT,
                                isFinal = true,
                            ),
                        ),
                )
            try {
                database.audioNoteDao().addNote(note)
                assertTrue(repository(database).acceptSyncedTranscript(note.uid, document))
                database.close()
                database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
                assertEquals(document, repository(database).getTranscription(note.uid)?.transcriptDocument)
                val segment = database.transcriptionDao().getSegmentsByNoteId(note.uid).single()
                assertEquals(1200L, segment.startMs)
                assertEquals("speaker-1", segment.speakerId)
                assertEquals(17, segment.revision)
                assertEquals(
                    note.uid.toString(),
                    database
                        .searchDao()
                        .searchRanked("advent*", 10)
                        .first()
                        .uid,
                )
                database.audioNoteDao().removeNote(note.uid)
                assertTrue(database.searchDao().searchRanked("advent*", 10).isEmpty())
                assertTrue(database.transcriptionDao().getSegmentsByNoteId(note.uid).isEmpty())
            } finally {
                database.close()
                Files.deleteIfExists(file)
                Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
                Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
            }
        }

    private fun repository(database: LogDateDatabase) =
        OfflineFirstTranscriptionRepository(
            database.transcriptionDao(),
            database.audioNoteDao(),
            FakeTranscriptionManager(true),
            transactionManager = RoomSyncTransactionManager(database),
        )
}
