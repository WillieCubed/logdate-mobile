package app.logdate.client.data.transcription

import androidx.room.Room
import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.client.data.notes.StoredMediaReferenceMigration
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.client.sync.SyncTransactionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Rewriting an audio note's media reference must not detach the transcript that was made for that file. */
@OptIn(ExperimentalCoroutinesApi::class)
class StoredMediaReferenceTranscriptTest {
    private val references =
        StoredMediaReferences { reference -> reference.replace("file:///install/audio_notes/", "logdate-media://recordings/") }

    @Test
    fun `migrating an audio note keeps its transcript bound to the file`() =
        runTest {
            withMigratedAudioNote { database, notes, note, document, rewritten, _ ->
                assertEquals(1, rewritten)
                assertEquals(
                    "logdate-media://recordings/walk.m4a",
                    database.transcriptionDao().getTranscriptionByNoteId(note.uid)?.mediaUri,
                )
                assertEquals(document, (notes.getNoteById(note.uid) as JournalNote.Audio).transcript)
            }
        }

    @Test
    fun `an audio note's reference and its transcript change in one transaction`() =
        runTest {
            withMigratedAudioNote { _, _, _, _, _, rebindsInsideTransaction ->
                // Automatic transcription would otherwise see the note's new reference beside the
                // transcript's old one and transcribe the note again.
                assertEquals(listOf(true), rebindsInsideTransaction)
            }
        }

    private suspend fun TestScope.withMigratedAudioNote(
        verify: suspend (
            database: LogDateDatabase,
            notes: OfflineFirstJournalNotesRepository,
            note: JournalNote.Audio,
            document: TranscriptDocument,
            rewritten: Int,
            rebindsInsideTransaction: List<Boolean>,
        ) -> Unit,
    ) {
        val file = Files.createTempFile("logdate-migration-transcript-", ".db")
        val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))
        try {
            val transactions = RoomSyncTransactionManager(database)
            val transcripts =
                OfflineFirstTranscriptionRepository(
                    database.transcriptionDao(),
                    database.audioNoteDao(),
                    FakeTranscriptionManager(true),
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
            val document = TranscriptDocument.fromPlainText("Words said on the walk")
            val note =
                JournalNote.Audio(
                    "file:///install/audio_notes/walk.m4a",
                    creationTimestamp = now,
                    lastUpdated = now,
                    transcript = document,
                )
            notes.createFromSync(note)
            runCurrent()

            val migrationTransactions = RecordingTransactions(transactions)
            val rebindsInsideTransaction = mutableListOf<Boolean>()
            val recordingTranscripts =
                object : TranscriptionRepository by transcripts {
                    override suspend fun rebindMediaReference(
                        noteId: Uuid,
                        previousUri: String,
                        mediaUri: String,
                    ) {
                        rebindsInsideTransaction += migrationTransactions.depth > 0
                        transcripts.rebindMediaReference(noteId, previousUri, mediaUri)
                    }
                }
            val rewritten =
                StoredMediaReferenceMigration(
                    imageNoteDao = database.imageNoteDao(),
                    audioNoteDao = database.audioNoteDao(),
                    videoNoteDao = database.videoNoteDao(),
                    journalDao = database.journalDao(),
                    mediaReferences = references,
                    transcriptions = recordingTranscripts,
                    transactions = migrationTransactions,
                ).run()
            runCurrent()

            verify(database, notes, note, document, rewritten, rebindsInsideTransaction)
        } finally {
            backgroundScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            database.close()
            Files.deleteIfExists(file)
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-wal"))
            Files.deleteIfExists(file.resolveSibling("${file.fileName}-shm"))
        }
    }

    /** Counts how deep the migration's own transactions are nested, apart from other writers. */
    private class RecordingTransactions(
        private val delegate: SyncTransactionManager,
    ) : SyncTransactionManager {
        @Volatile
        var depth = 0
            private set

        override suspend fun <T> withTransaction(block: suspend () -> T): T {
            depth++
            try {
                return delegate.withTransaction(block)
            } finally {
                depth--
            }
        }
    }
}
