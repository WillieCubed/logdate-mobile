package app.logdate.client.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.TextNoteEntity
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RetainedDatabaseMigrationSafetyJvmTest {
    private val directory = Files.createTempDirectory("retained-migration-safety")
    private val file = directory.resolve("journal.db")
    private val journalId = Uuid.parse("10000000-0000-0000-0000-000000000001")
    private val noteId = Uuid.parse("10000000-0000-0000-0000-000000000002")

    @AfterTest fun cleanFixture() {
        directory.toFile().deleteRecursively()
    }

    @Test fun `newer backup cannot be replaced with an empty journal`() =
        runTest {
            seedCurrentJournal(version = 58)
            assertRefusedAndRetained(58)
        }

    @Test fun `unknown older version metadata cannot erase retained records`() =
        runTest {
            seedCurrentJournal(version = -1)
            assertRefusedAndRetained(-1)
        }

    @Test fun `released internal schema migrates with content deletion state and outbox intact`() = runTest { assertSupportedMigration(55) }

    @Test fun `supported previous schema migrates with content deletion state and outbox intact`() =
        runTest {
            assertSupportedMigration(
                56,
            )
        }

    private suspend fun assertSupportedMigration(fromVersion: Int) {
        val helper =
            MigrationTestHelper(
                Path.of("schemas"),
                file,
                BundledSQLiteDriver(),
                LogDateDatabase::class,
                { LogDateDatabaseConstructor.initialize() },
                emptyList(),
            )
        helper.createDatabase(fromVersion).use { connection ->
            connection.execSQL(
                "INSERT INTO journals (id, title, description, created, lastUpdated, syncVersion) " +
                    "VALUES ('$journalId', 'Retained journal', '', 1000, 1000, 7)",
            )
            connection.execSQL(
                "INSERT INTO text_notes (uid, content, created, lastUpdated, syncVersion, deletedAt) " +
                    "VALUES ('$noteId', 'Retained deleted note', 1000, 1000, 8, 2000)",
            )
            connection.execSQL("INSERT INTO journal_notes (id, uid) VALUES ('$journalId', '$noteId')")
        }
        seedOutbox()
        val before = snapshot()
        val database = open()
        try {
            assertEquals(
                "Retained journal",
                database
                    .journalDao()
                    .getAll()
                    .single()
                    .title,
            )
        } finally {
            database.close()
        }
        assertEquals(before, snapshot())
        assertEquals(57L, version())
    }

    @Test fun `only an explicitly disposable builder may recreate unsupported tables`() =
        runTest {
            seedCurrentJournal(version = 58)
            val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()).fallbackToDestructiveMigration(true))
            try {
                assertTrue(database.journalDao().getAll().isEmpty())
            } finally {
                database.close()
            }
            assertEquals(57L, version())
        }

    private fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(file.toString()))

    private suspend fun seedCurrentJournal(version: Int) {
        val database = open()
        try {
            val at = Instant.fromEpochMilliseconds(1000)
            database.journalDao().create(JournalEntity(journalId, "Retained journal", "", at, at, syncVersion = 7))
            database.textNoteDao().addNote(
                TextNoteEntity("Retained deleted note", noteId, at, at, syncVersion = 8, deletedAt = Instant.fromEpochMilliseconds(2000)),
            )
            database.journalNotesDao().addNoteToJournal(journalId, noteId)
        } finally {
            database.close()
        }
        seedOutbox()
        BundledSQLiteDriver().open(file.toString()).use { it.execSQL("PRAGMA user_version = $version") }
    }

    private fun seedOutbox() {
        BundledSQLiteDriver().open(file.toString()).use {
            it.execSQL(
                "INSERT INTO pending_uploads " +
                    "(ownerId, serverOrigin, entityType, entityId, operation, createdAt, retryCount, operationId) " +
                    "VALUES ('owner', 'https://cloud.test', 'NOTE', '$noteId', 'DELETE', 10, 1, 'retained-operation')",
            )
        }
    }

    private suspend fun assertRefusedAndRetained(expectedVersion: Int) {
        val before = snapshot()
        val database = open()
        val error =
            try {
                runCatching { database.journalDao().getAll() }.exceptionOrNull()
            } finally {
                database.close()
            }
        assertNotNull(error, "Missing migrations must refuse opening instead of recreating a journal")
        assertTrue(generateSequence(error) { it.cause }.any { it.message?.contains("migration", ignoreCase = true) == true })
        assertEquals(before, snapshot(), "Refused recovery must retain entries, deletion state, relationships, and queued work")
        assertEquals(expectedVersion.toLong(), version())
    }

    private fun snapshot(): List<List<String>> =
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            listOf(
                "SELECT id, title, syncVersion FROM journals ORDER BY id",
                "SELECT uid, content, deletedAt FROM text_notes ORDER BY uid",
                "SELECT id, uid FROM journal_notes ORDER BY id, uid",
                "SELECT entityId, operation, operationId FROM pending_uploads ORDER BY operationId",
            ).map { query ->
                connection.prepare(query).use { rows ->
                    buildList { while (rows.step()) add((0 until rows.getColumnCount()).joinToString("|") { rows.getText(it) }) }
                }
            }
        }

    private fun version(): Long =
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.prepare("PRAGMA user_version").use {
                it.step()
                it.getLong(0)
            }
        }
}
