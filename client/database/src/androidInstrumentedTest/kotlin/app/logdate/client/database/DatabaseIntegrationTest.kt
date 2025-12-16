package app.logdate.client.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Android instrumented tests for database functionality with real SQLite instances.
 * 
 * These tests verify data integrity, DAO functionality, and database migrations
 * using actual Room database instances.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class DatabaseIntegrationTest {
    
    private lateinit var database: LogDateDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            LogDateDatabase::class.java
        )
        .allowMainThreadQueries() // Only for testing
        .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun journalDao_insertAndRetrieve_worksCorrectly() = runTest {
        val journalDao = database.journalDao()
        val journal = app.logdate.client.database.entities.JournalEntity(
            id = Uuid.random(),
            title = "Test Journal",
            description = "Test Description",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        // Insert journal
        val insertedId = journalDao.create(journal)
        assertTrue(insertedId > 0)
        
        // Retrieve and verify
        val allJournals = journalDao.getAll()
        assertEquals(1, allJournals.size)
        assertEquals(journal.title, allJournals.first().title)
        assertEquals(journal.id, allJournals.first().id)
    }

    @Test
    fun journalDao_observeJournal_emitsUpdates() = runTest {
        val journalDao = database.journalDao()
        val journal = app.logdate.client.database.entities.JournalEntity(
            id = Uuid.random(),
            title = "Observable Journal",
            description = "Test Description",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        // Insert journal
        journalDao.create(journal)
        
        // Observe journals
        val observedJournals = journalDao.observeAll().first()
        assertEquals(1, observedJournals.size)
        assertEquals("Observable Journal", observedJournals.first().title)
    }

    @Test
    fun textNoteDao_insertAndRetrieve_worksCorrectly() = runTest {
        val textNoteDao = database.textNoteDao()
        val note = app.logdate.client.database.entities.TextNoteEntity(
            uid = Uuid.random(),
            content = "Test note content",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        // Insert note
        textNoteDao.addNote(note)
        
        // Retrieve and verify
        val allNotes = textNoteDao.getAllNotes().first()
        assertEquals(1, allNotes.size)
        assertEquals(note.content, allNotes.first().content)
        assertEquals(note.uid, allNotes.first().uid)
    }

    @Test
    fun imageNoteDao_insertAndRetrieve_worksCorrectly() = runTest {
        val imageNoteDao = database.imageNoteDao()
        val note = app.logdate.client.database.entities.ImageNoteEntity(
            uid = Uuid.random(),
            contentUri = "test_image.jpg",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        // Insert note
        imageNoteDao.addNote(note)
        
        // Retrieve and verify
        val allNotes = imageNoteDao.getAllNotes().first()
        assertEquals(1, allNotes.size)
        assertEquals(note.contentUri, allNotes.first().contentUri)
        assertEquals(note.uid, allNotes.first().uid)
    }

    @Test
    fun journalNotesDao_associationsFunctionCorrectly() = runTest {
        val journalDao = database.journalDao()
        val textNoteDao = database.textNoteDao()
        val journalNotesDao = database.journalNotesDao()
        
        // Create journal and note
        val journal = app.logdate.client.database.entities.JournalEntity(
            id = Uuid.random(),
            title = "Association Test Journal",
            description = "Test Description",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        val note = app.logdate.client.database.entities.TextNoteEntity(
            uid = Uuid.random(),
            content = "Associated note",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        // Insert entities
        journalDao.create(journal)
        textNoteDao.addNote(note)
        
        // Create association
        journalNotesDao.addNoteToJournal(journal.id, note.uid)
        
        // Verify association
        val noteJournals = journalNotesDao.getNotesForJournal(journal.id).first()
        assertEquals(1, noteJournals.size)
        assertEquals(note.uid, noteJournals.first().noteId)
        
        // Verify reverse association
        val associatedJournals = journalNotesDao.journalsForNoteSync(note.uid)
        assertEquals(1, associatedJournals.size)
        assertEquals(journal.title, associatedJournals.first().title)
    }

    @Test
    fun database_transactionRollback_worksCorrectly() = runTest {
        val journalDao = database.journalDao()
        
        try {
            database.runInTransaction {
                // Insert a journal
                val journal = app.logdate.client.database.entities.JournalEntity(
                    id = Uuid.random(),
                    title = "Transaction Test",
                    description = "Test Description",
                    created = Clock.System.now(),
                    lastUpdated = Clock.System.now()
                )
                journalDao.create(journal)
                
                // Force an error to trigger rollback
                throw RuntimeException("Intentional test error")
            }
        } catch (e: RuntimeException) {
            // Expected
        }
        
        // Verify no journals were inserted due to rollback
        val allJournals = journalDao.getAll()
        assertTrue(allJournals.isEmpty())
    }

    @Test
    fun database_multipleOperations_maintainConsistency() = runTest {
        val journalDao = database.journalDao()
        val textNoteDao = database.textNoteDao()
        val imageNoteDao = database.imageNoteDao()
        val journalNotesDao = database.journalNotesDao()
        
        // Create test data
        val journal = app.logdate.client.database.entities.JournalEntity(
            id = Uuid.random(),
            title = "Multi-op Journal",
            description = "Test Description",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        val textNote = app.logdate.client.database.entities.TextNoteEntity(
            uid = Uuid.random(),
            content = "Text note",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        val imageNote = app.logdate.client.database.entities.ImageNoteEntity(
            uid = Uuid.random(),
            contentUri = "image.jpg",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        // Perform multiple operations
        journalDao.create(journal)
        textNoteDao.addNote(textNote)
        imageNoteDao.addNote(imageNote)
        journalNotesDao.addNoteToJournal(journal.id, textNote.uid)
        journalNotesDao.addNoteToJournal(journal.id, imageNote.uid)
        
        // Verify all operations succeeded
        assertEquals(1, journalDao.getAll().size)
        assertEquals(1, textNoteDao.getAllNotes().first().size)
        assertEquals(1, imageNoteDao.getAllNotes().first().size)
        assertEquals(2, journalNotesDao.getNotesForJournal(journal.id).first().size)
        
        // Verify data integrity
        val retrievedJournal = journalDao.observeJournalById(journal.id).first()
        assertEquals(journal.title, retrievedJournal.title)
        
        val retrievedTextNote = textNoteDao.getNoteOneOff(textNote.uid)
        assertEquals(textNote.content, retrievedTextNote.content)
        
        val retrievedImageNote = imageNoteDao.getNoteOneOff(imageNote.uid)
        assertEquals(imageNote.contentUri, retrievedImageNote.contentUri)
    }

    @Test
    fun database_allDaosAccessible() = runTest {
        // Verify all DAOs can be accessed and are functional
        assertNotNull(database.journalDao())
        assertNotNull(database.textNoteDao())
        assertNotNull(database.imageNoteDao())
        assertNotNull(database.journalNotesDao())
        assertNotNull(database.locationHistoryDao())
        assertNotNull(database.userDevicesDao())
        assertNotNull(database.userMediaDao())
        assertNotNull(database.journalContentDao())
        assertNotNull(database.rewindDao())
        
        // Verify basic functionality
        assertTrue(database.journalDao().getAll().isEmpty())
        assertTrue(database.textNoteDao().getAllNotes().first().isEmpty())
        assertTrue(database.imageNoteDao().getAllNotes().first().isEmpty())
    }
}