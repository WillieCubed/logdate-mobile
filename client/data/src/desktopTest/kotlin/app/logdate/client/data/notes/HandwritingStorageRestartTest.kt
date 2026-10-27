package app.logdate.client.data.notes

import androidx.room.Room
import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.data.notes.drafts.DesktopLocalEntryDraftStore
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.RoomSyncTransactionManager
import app.logdate.shared.model.HandwritingDocument
import app.logdate.shared.model.HandwritingPoint
import app.logdate.shared.model.HandwritingStroke
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class HandwritingStorageRestartTest {
    @Test
    fun `draft and published strokes survive closing and reopening actual storage`() =
        runTest {
            val directory = Files.createTempDirectory("logdate-handwriting-restart-").toFile()
            val file = directory.resolve("notes.db")
            val time = Instant.fromEpochMilliseconds(1725000000000)
            val note =
                JournalNote.Handwriting(
                    uid = Uuid.random(),
                    creationTimestamp = time,
                    lastUpdated = time,
                    timeZoneId = "America/Los_Angeles",
                    document =
                        HandwritingDocument(
                            height = 1920f,
                            strokes =
                                listOf(
                                    HandwritingStroke(
                                        Uuid.random(),
                                        "highlighter",
                                        "#7755AA",
                                        17.5f,
                                        listOf(HandwritingPoint(12.25f, 1500.5f, 0.2f), HandwritingPoint(400.75f, 1650.25f, 0.9f)),
                                    ),
                                ),
                        ),
                )
            val draft = EntryDraft(Uuid.random(), listOf(note), time, time, selectedJournalIds = listOf(Uuid.random()))

            fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.absolutePath))

            fun repository(database: LogDateDatabase) =
                OfflineFirstJournalNotesRepository(
                    textNoteDao = database.textNoteDao(),
                    imageNoteDao = database.imageNoteDao(),
                    audioNoteDao = database.audioNoteDao(),
                    videoNoteDao = database.videoNoteDao(),
                    journalContentDao = database.journalContentDao(),
                    journalRepository = FakeJournalRepository(),
                    mediaCaptionDao = database.mediaCaptionDao(),
                    transactionManager = RoomSyncTransactionManager(database),
                    syncMetadataService = FakeSyncMetadataService(),
                )
            var database = open()
            try {
                DesktopLocalEntryDraftStore(directory).saveDraft(draft)
                assertEquals(draft, DesktopLocalEntryDraftStore(directory).getDraft(draft.id))
                repository(database).create(note)
                kotlin.test.assertTrue(
                    database.searchDao().getIndexedEntries().none {
                        it.uid == note.uid.toString()
                    },
                    "Stroke JSON must not appear as user text in search",
                )
                database.close()
                database = open()
                val reopened = repository(database)
                assertEquals(note, reopened.getNoteById(note.uid))
                assertEquals(listOf(note), reopened.allNotesObserved.first())
                assertEquals(listOf(note), reopened.observeNotesPage(10, 0).first())
                assertEquals(listOf(note), reopened.getNotesBefore(time + kotlin.time.Duration.parse("1s"), 10))
                val unreadableId = Uuid.random()
                val raw = """{"version":2,"futureInk":"keep the original"}"""
                database.textNoteDao().addNote(
                    app.logdate.client.database.entities.TextNoteEntity(
                        uid = unreadableId,
                        content = raw,
                        contentFormat = "handwriting",
                        created = time,
                        lastUpdated = time,
                    ),
                )
                val withUnreadable = reopened.allNotesObserved.first()
                assertEquals(2, withUnreadable.size)
                assertEquals(note, withUnreadable.single { it.uid == note.uid })
                val retained = reopened.getNoteById(unreadableId) as JournalNote.Handwriting
                assertEquals(raw, retained.unreadableContent)
                assertEquals(raw, retained.toEntity().content)
            } finally {
                database.close()
                directory.deleteRecursively()
            }
        }
}
