package app.logdate.client.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.entities.sync.SpaceRecordStateEntity
import app.logdate.client.database.migrations.MIGRATION_55_56
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Validates migration 56, which adds the personal-space record table and changes nothing else. */
class SpaceRecordStateMigrationJvmTest {
    private val databaseFile = Files.createTempFile("logdate-migration", ".db")

    private val helper =
        MigrationTestHelper(
            Path.of("schemas"),
            databaseFile,
            BundledSQLiteDriver(),
            LogDateDatabase::class,
            { LogDateDatabaseConstructor.initialize() },
            emptyList(),
        )

    @AfterTest
    fun tearDown() {
        Files.deleteIfExists(databaseFile)
        Files.deleteIfExists(databaseFile.resolveSibling("${databaseFile.fileName}-wal"))
        Files.deleteIfExists(databaseFile.resolveSibling("${databaseFile.fileName}-shm"))
    }

    @Test
    fun `room adds an empty space record table and keeps version 55 data`() {
        helper.createDatabase(55).use { connection ->
            connection.execSQL(
                "INSERT INTO journals (id, title, description, created, lastUpdated, syncVersion) " +
                    "VALUES ('journal-1', 'Daily', 'Every day', 1710000000000, 1710000000000, 4)",
            )
            connection.execSQL(
                "INSERT INTO pending_uploads " +
                    "(ownerId, serverOrigin, entityType, entityId, operation, createdAt, retryCount, operationId) " +
                    "VALUES ('owner', 'server', 'JOURNAL', 'journal-1', 'UPDATE', 10, 1, 'operation-1')",
            )
        }

        helper.runMigrationsAndValidate(56, listOf(MIGRATION_55_56)).use { connection ->
            connection.prepare("SELECT title, syncVersion FROM journals WHERE id = 'journal-1'").use { row ->
                assertTrue(row.step(), "the journal is gone after the migration")
                assertEquals("Daily", row.getText(0))
                assertEquals(4L, row.getLong(1))
            }
            connection.prepare("SELECT operation FROM pending_uploads WHERE entityId = 'journal-1'").use { row ->
                assertTrue(row.step(), "the queued upload is gone after the migration")
                assertEquals("UPDATE", row.getText(0))
            }
            connection.prepare("SELECT COUNT(*) FROM sync_space_records").use { row ->
                assertTrue(row.step())
                assertEquals(0L, row.getLong(0))
            }
        }
    }

    @Test
    fun `a sealed key names one record per account and server`() =
        runTest {
            val database = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(databaseFile.toString()))
            try {
                val dao = database.spaceRecordStateDao()
                val journal =
                    SpaceRecordStateEntity(
                        ownerId = "owner",
                        serverOrigin = "https://cloud.test",
                        collection = "app.logdate.journal.journal",
                        innerRkey = "j1",
                        sealedRkey = "SEALED1",
                        cid = "bafy1",
                        seq = 3,
                    )
                dao.put(journal)
                dao.put(journal.copy(ownerId = "other"))

                assertEquals(journal, dao.bySealedRkey("owner", "https://cloud.test", "SEALED1"))
                assertEquals(journal, dao.get("owner", "https://cloud.test", "app.logdate.journal.journal", "j1"))
                assertEquals(1, dao.all("owner", "https://cloud.test").size)

                // A newer write of the same record replaces the row it had.
                dao.put(journal.copy(cid = "bafy2", seq = 9))
                assertEquals(9L, dao.bySealedRkey("owner", "https://cloud.test", "SEALED1")?.seq)

                dao.delete("owner", "https://cloud.test", "app.logdate.journal.journal", "j1")
                assertNull(dao.bySealedRkey("owner", "https://cloud.test", "SEALED1"))
                assertEquals("bafy1", dao.bySealedRkey("other", "https://cloud.test", "SEALED1")?.cid)
            } finally {
                database.close()
            }
        }
}
