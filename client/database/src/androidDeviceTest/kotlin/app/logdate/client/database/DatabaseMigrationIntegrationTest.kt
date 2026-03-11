package app.logdate.client.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.migrations.AppDatabaseMigrations
import app.logdate.client.database.migrations.MIGRATION_25_26
import app.logdate.client.database.migrations.MIGRATION_26_27
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationIntegrationTest {
    private val testDatabaseName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            LogDateDatabase::class.java,
            listOf(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun testMigrationFrom5To6() {
        var db =
            helper.createDatabase(testDatabaseName, 5).apply {
                // Insert test data in version 5 schema
                execSQL(
                    """
                INSERT INTO journals (id, title, description, icon, created_at, updated_at, is_default, color)
                VALUES ('test-journal-id', 'Test Journal', 'Test Description', 'book', 1640995200000, 1640995200000, 1, '#FF5722')
            """,
                )
                close()
            }

        db = helper.runMigrationsAndValidate(testDatabaseName, 6, true, AppDatabaseMigrations.MIGRATION_5_6)

        // Verify data integrity after migration
        val cursor = db.query("SELECT * FROM journals WHERE id = 'test-journal-id'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Test Journal", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals("Test Description", cursor.getString(cursor.getColumnIndexOrThrow("description")))
        cursor.close()
    }

    @Test
    fun testMigrationFrom6To7() {
        var db =
            helper.createDatabase(testDatabaseName, 6).apply {
                // Insert test data in version 6 schema
                execSQL(
                    """
                INSERT INTO journals (id, title, description, icon, created_at, updated_at, is_default, color)
                VALUES ('test-journal-id', 'Test Journal', 'Test Description', 'book', 1640995200000, 1640995200000, 1, '#FF5722')
            """,
                )
                close()
            }

        db = helper.runMigrationsAndValidate(testDatabaseName, 7, true, AppDatabaseMigrations.MIGRATION_6_7)

        // Verify data integrity after migration
        val cursor = db.query("SELECT * FROM journals WHERE id = 'test-journal-id'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Test Journal", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        cursor.close()
    }

    @Test
    fun testMigrationFrom7To8() {
        var db =
            helper.createDatabase(testDatabaseName, 7).apply {
                // Insert test data in version 7 schema
                execSQL(
                    """
                INSERT INTO journals (id, title, description, icon, created_at, updated_at, is_default, color)
                VALUES ('test-journal-id', 'Test Journal', 'Test Description', 'book', 1640995200000, 1640995200000, 1, '#FF5722')
            """,
                )
                close()
            }

        db = helper.runMigrationsAndValidate(testDatabaseName, 8, true, AppDatabaseMigrations.MIGRATION_7_8)

        // Verify data integrity after migration
        val cursor = db.query("SELECT * FROM journals WHERE id = 'test-journal-id'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Test Journal", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        cursor.close()
    }

    @Test
    fun testMigrationFrom8To9() {
        var db =
            helper.createDatabase(testDatabaseName, 8).apply {
                // Insert test data in version 8 schema
                execSQL(
                    """
                INSERT INTO journals (id, title, description, icon, created_at, updated_at, is_default, color)
                VALUES ('test-journal-id', 'Test Journal', 'Test Description', 'book', 1640995200000, 1640995200000, 1, '#FF5722')
            """,
                )
                execSQL(
                    """
                INSERT INTO text_notes (id, content, created_at, updated_at, journal_id, order_index)
                VALUES ('test-note-id', 'Test content', 1640995200000, 1640995200000, 'test-journal-id', 0)
            """,
                )
                close()
            }

        db = helper.runMigrationsAndValidate(testDatabaseName, 9, true, AppDatabaseMigrations.MIGRATION_8_9)

        // Verify data integrity after migration
        val journalCursor = db.query("SELECT * FROM journals WHERE id = 'test-journal-id'")
        assertTrue(journalCursor.moveToFirst())
        assertEquals("Test Journal", journalCursor.getString(journalCursor.getColumnIndexOrThrow("title")))
        journalCursor.close()

        val noteCursor = db.query("SELECT * FROM text_notes WHERE id = 'test-note-id'")
        assertTrue(noteCursor.moveToFirst())
        assertEquals("Test content", noteCursor.getString(noteCursor.getColumnIndexOrThrow("content")))
        noteCursor.close()
    }

    @Test
    fun testMigrationFrom9To10() {
        var db =
            helper.createDatabase(testDatabaseName, 9).apply {
                // Insert test data in version 9 schema
                execSQL(
                    """
                INSERT INTO journals (id, title, description, icon, created_at, updated_at, is_default, color)
                VALUES ('test-journal-id', 'Test Journal', 'Test Description', 'book', 1640995200000, 1640995200000, 1, '#FF5722')
            """,
                )
                execSQL(
                    """
                INSERT INTO text_notes (id, content, created_at, updated_at, journal_id, order_index)
                VALUES ('test-note-id', 'Test content', 1640995200000, 1640995200000, 'test-journal-id', 0)
            """,
                )
                close()
            }

        db = helper.runMigrationsAndValidate(testDatabaseName, 10, true, AppDatabaseMigrations.MIGRATION_9_10)

        // Verify data integrity after migration
        val journalCursor = db.query("SELECT * FROM journals WHERE id = 'test-journal-id'")
        assertTrue(journalCursor.moveToFirst())
        assertEquals("Test Journal", journalCursor.getString(journalCursor.getColumnIndexOrThrow("title")))
        journalCursor.close()

        val noteCursor = db.query("SELECT * FROM text_notes WHERE id = 'test-note-id'")
        assertTrue(noteCursor.moveToFirst())
        assertEquals("Test content", noteCursor.getString(noteCursor.getColumnIndexOrThrow("content")))
        noteCursor.close()
    }

    @Test
    fun testCompleteChainMigration5To10() {
        var db =
            helper.createDatabase(testDatabaseName, 5).apply {
                // Insert comprehensive test data in version 5 schema
                execSQL(
                    """
                INSERT INTO journals (id, title, description, icon, created_at, updated_at, is_default, color)
                VALUES ('test-journal-1', 'Journal One', 'First journal', 'book', 1640995200000, 1640995200000, 1, '#FF5722')
            """,
                )
                execSQL(
                    """
                INSERT INTO journals (id, title, description, icon, created_at, updated_at, is_default, color)
                VALUES ('test-journal-2', 'Journal Two', 'Second journal', 'note', 1640995300000, 1640995300000, 0, '#2196F3')
            """,
                )
                close()
            }

        // Run all migrations in sequence
        db =
            helper.runMigrationsAndValidate(
                testDatabaseName,
                10,
                true,
                AppDatabaseMigrations.MIGRATION_5_6,
                AppDatabaseMigrations.MIGRATION_6_7,
                AppDatabaseMigrations.MIGRATION_7_8,
                AppDatabaseMigrations.MIGRATION_8_9,
                AppDatabaseMigrations.MIGRATION_9_10,
            )

        // Verify all data survived the complete migration chain
        val cursor = db.query("SELECT * FROM journals ORDER BY created_at")
        assertTrue(cursor.moveToFirst())

        // First journal
        assertEquals("test-journal-1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
        assertEquals("Journal One", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals("First journal", cursor.getString(cursor.getColumnIndexOrThrow("description")))
        assertEquals("book", cursor.getString(cursor.getColumnIndexOrThrow("icon")))
        assertEquals("#FF5722", cursor.getString(cursor.getColumnIndexOrThrow("color")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("is_default")))

        // Second journal
        assertTrue(cursor.moveToNext())
        assertEquals("test-journal-2", cursor.getString(cursor.getColumnIndexOrThrow("id")))
        assertEquals("Journal Two", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals("Second journal", cursor.getString(cursor.getColumnIndexOrThrow("description")))
        assertEquals("note", cursor.getString(cursor.getColumnIndexOrThrow("icon")))
        assertEquals("#2196F3", cursor.getString(cursor.getColumnIndexOrThrow("color")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("is_default")))

        cursor.close()
    }

    @Test
    fun testDataIntegrityWithFullDatabaseOperations() {
        // Create database with latest schema and perform full CRUD operations
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database =
            Room
                .inMemoryDatabaseBuilder(context, LogDateDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        try {
            val journalDao = database.journalDao()
            val textNoteDao = database.textNoteDao()
            val currentTime = Clock.System.now()

            // Create test journal
            val journal =
                JournalEntity(
                    id = Uuid.random(),
                    title = "Integration Test Journal",
                    description = "Testing data integrity",
                    icon = "test",
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    isDefault = false,
                    color = "#4CAF50",
                )
            journalDao.insert(journal)

            // Create test notes
            val note1 =
                TextNoteEntity(
                    id = Uuid.random(),
                    content = "First test note content",
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    journalId = journal.id,
                    orderIndex = 0,
                )
            val note2 =
                TextNoteEntity(
                    id = Uuid.random(),
                    content = "Second test note content",
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    journalId = journal.id,
                    orderIndex = 1,
                )
            textNoteDao.insert(note1)
            textNoteDao.insert(note2)

            // Verify journal retrieval
            val retrievedJournal = journalDao.getJournal(journal.id)
            assertNotNull(retrievedJournal)
            assertEquals(journal.title, retrievedJournal.title)
            assertEquals(journal.description, retrievedJournal.description)

            // Verify notes retrieval
            val retrievedNotes = textNoteDao.getNotesForJournal(journal.id)
            assertEquals(2, retrievedNotes.size)
            assertEquals("First test note content", retrievedNotes.find { it.orderIndex == 0 }?.content)
            assertEquals("Second test note content", retrievedNotes.find { it.orderIndex == 1 }?.content)

            // Test journal with notes query
            val journalWithNotes = journalDao.getJournalWithNotes(journal.id)
            assertNotNull(journalWithNotes)
            assertEquals(journal.title, journalWithNotes.journal.title)
            assertEquals(2, journalWithNotes.textNotes.size)

            // Test update operations
            val updatedJournal = journal.copy(title = "Updated Title")
            journalDao.update(updatedJournal)

            val reRetrievedJournal = journalDao.getJournal(journal.id)
            assertNotNull(reRetrievedJournal)
            assertEquals("Updated Title", reRetrievedJournal.title)

            // Test delete operations
            textNoteDao.delete(note1)
            val remainingNotes = textNoteDao.getNotesForJournal(journal.id)
            assertEquals(1, remainingNotes.size)
            assertEquals(note2.content, remainingNotes.first().content)
        } finally {
            database.close()
        }
    }

    @Test
    fun testMigrationFrom25To26BackfillsLocationSampleMetadata() {
        var db =
            helper.createDatabase(testDatabaseName, 25).apply {
                execSQL(
                    """
                    INSERT INTO location_logs (
                        user_id,
                        device_id,
                        timestamp,
                        latitude,
                        longitude,
                        altitude,
                        confidence,
                        is_genuine
                    ) VALUES (
                        'user-1',
                        'device-1',
                        1710000000000,
                        37.7749,
                        -122.4194,
                        12.0,
                        0.9,
                        1
                    )
                    """.trimIndent(),
                )
                close()
            }

        db = helper.runMigrationsAndValidate(testDatabaseName, 26, true, MIGRATION_25_26)

        val cursor =
            db.query(
                """
                SELECT sample_id, logged_at, capture_pipeline, capture_source, is_mock
                FROM location_logs
                WHERE user_id = 'user-1' AND device_id = 'device-1'
                """.trimIndent(),
            )

        assertTrue(cursor.moveToFirst())
        assertEquals("user-1:device-1:1710000000000", cursor.getString(cursor.getColumnIndexOrThrow("sample_id")))
        assertEquals(1710000000000, cursor.getLong(cursor.getColumnIndexOrThrow("logged_at")))
        assertEquals("LEGACY", cursor.getString(cursor.getColumnIndexOrThrow("capture_pipeline")))
        assertEquals("BACKGROUND_PERIODIC", cursor.getString(cursor.getColumnIndexOrThrow("capture_source")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("is_mock")))
        cursor.close()

        val indexCursor =
            db.query(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'index' AND name = 'index_location_logs_timestamp'
                """.trimIndent(),
            )

        assertFalse(indexCursor.moveToFirst())
        indexCursor.close()
    }

    @Test
    fun testMigrationFrom26To27RemovesUnexpectedLocationTimestampIndex() {
        var db =
            helper.createDatabase(testDatabaseName, 26).apply {
                execSQL("CREATE INDEX IF NOT EXISTS index_location_logs_timestamp ON location_logs(timestamp)")
                close()
            }

        db = helper.runMigrationsAndValidate(testDatabaseName, 27, true, MIGRATION_26_27)

        val cursor =
            db.query(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'index' AND name = 'index_location_logs_timestamp'
                """.trimIndent(),
            )

        assertFalse(cursor.moveToFirst())
        cursor.close()
    }
}
