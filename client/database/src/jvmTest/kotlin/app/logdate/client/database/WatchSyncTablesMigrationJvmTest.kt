package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_52_53
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Validates migration 53, which adds marked moments and the watch delivery ledger. */
class WatchSyncTablesMigrationJvmTest {
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
    fun `room adds the marked moment and watch delivery tables and keeps version 52 data`() {
        helper.createDatabase(52).use { connection ->
            connection.execSQL(
                "INSERT INTO pending_uploads " +
                    "(ownerId, serverOrigin, entityType, entityId, operation, createdAt, retryCount, operationId) " +
                    "VALUES ('owner', 'server', 'NOTE', 'note-1', 'CREATE', 10, 2, 'operation-1')",
            )
            connection.execSQL(
                "INSERT INTO sync_cursors (ownerId, serverOrigin, entityType, lastSyncTimestamp) VALUES ('owner', 'server', 'NOTE', 500)",
            )
        }

        helper.runMigrationsAndValidate(53, listOf(MIGRATION_52_53)).use { connection ->
            connection.prepare("SELECT operation, retryCount, operationId FROM pending_uploads WHERE entityId = 'note-1'").use { row ->
                assertTrue(row.step(), "the queued upload is gone after the migration")
                assertEquals("CREATE", row.getText(0))
                assertEquals(2L, row.getLong(1))
                assertEquals("operation-1", row.getText(2))
            }
            connection.prepare("SELECT lastSyncTimestamp FROM sync_cursors WHERE entityType = 'NOTE'").use { row ->
                assertTrue(row.step(), "the download cursor is gone after the migration")
                assertEquals(500L, row.getLong(0))
            }
            listOf("marked_moments", "peer_delivery").forEach { table ->
                connection.prepare("SELECT COUNT(*) FROM $table").use { row ->
                    assertTrue(row.step())
                    assertEquals(0L, row.getLong(0), table)
                }
            }
        }
    }

    @Test
    fun `every table and row from version 52 is unchanged and only two tables are added`() {
        val before =
            helper.createDatabase(52).use { connection ->
                connection.execSQL(
                    "INSERT INTO text_notes (uid, content, lastUpdated, created, syncVersion) " +
                        "VALUES ('note-1', 'Written before marked moments', 1710000000000, 1710000000000, 3)",
                )
                connection.execSQL(
                    "INSERT INTO journals (id, title, description, created, lastUpdated, syncVersion) " +
                        "VALUES ('journal-1', 'Daily', 'Every day', 1710000000000, 1710000000000, 4)",
                )
                connection.execSQL(
                    "INSERT INTO sync_download_checkpoints (ownerId, serverOrigin, entityType, cursor, fetchFailure) " +
                        "VALUES ('owner', 'server', 'NOTE', 77, '')",
                )
                connection.snapshot()
            }

        val after = helper.runMigrationsAndValidate(53, listOf(MIGRATION_52_53)).use { it.snapshot() }

        assertEquals(setOf("marked_moments", "peer_delivery"), after.tables.keys - before.tables.keys)
        assertEquals(emptySet(), before.tables.keys - after.tables.keys, "a table from version 52 is gone")
        before.tables.forEach { (table, definition) ->
            assertEquals(definition, after.tables[table], "the definition of $table changed")
            assertEquals(before.rows[table], after.rows[table], "the rows of $table changed")
        }
        assertEquals(before.indexes, after.indexes, "an index changed")
    }

    @Test
    fun `the new tables take the rows their entities describe`() {
        helper.createDatabase(52).close()

        helper.runMigrationsAndValidate(53, listOf(MIGRATION_52_53)).use { connection ->
            connection.execSQL(
                "INSERT INTO marked_moments " +
                    "(id, capturedAt, timeZoneId, state, caption, resolvedNoteIds, originDeviceId, lastUpdated, syncVersion) " +
                    "VALUES ('550e8400-e29b-41d4-a716-446655440000', 1, 'Europe/Paris', 'OPEN', '', '', 'watch', 1, 0)",
            )
            connection.execSQL(
                "INSERT INTO peer_delivery (peerScope, direction, entityType, entityId, contentVersion, state, attempts, stateChangedAt) " +
                    "VALUES ('phone', 'TO_PHONE', 'NOTE', 'note-1', 5, 'PENDING', 0, 1)",
            )
            listOf("marked_moments", "peer_delivery").forEach { table ->
                connection.prepare("SELECT COUNT(*) FROM $table").use { row ->
                    assertTrue(row.step())
                    assertEquals(1L, row.getLong(0), table)
                }
            }
        }
    }

    /**
     * Everything the migration must leave alone: each table's SQL, its rows, and the indexes. Room's own
     * bookkeeping table is left out because its stored identity hash changes with the version.
     */
    private class SchemaSnapshot(
        val tables: Map<String, String>,
        val rows: Map<String, List<List<String?>>>,
        val indexes: Set<String>,
    )

    private fun SQLiteConnection.snapshot(): SchemaSnapshot {
        val tables = linkedMapOf<String, String>()
        val indexes = mutableSetOf<String>()
        val schemaQuery =
            "SELECT type, name, tbl_name, sql FROM sqlite_master " +
                "WHERE name NOT LIKE 'sqlite_%' AND name <> 'room_master_table' ORDER BY name"
        prepare(schemaQuery).use { row ->
            while (row.step()) {
                val sql = if (row.isNull(3)) "" else row.getText(3)
                if (row.getText(0) == "table") {
                    tables[row.getText(1)] = sql
                } else {
                    indexes += "${row.getText(2)}|${row.getText(1)}|$sql"
                }
            }
        }
        val rows =
            tables.keys.associateWith { table ->
                val dumped = mutableListOf<List<String?>>()
                prepare("SELECT * FROM `$table`").use { row ->
                    while (row.step()) {
                        dumped +=
                            (0 until row.getColumnCount()).map { column ->
                                if (row.isNull(column)) null else row.getText(column)
                            }
                    }
                }
                dumped.sortedBy { it.toString() }
            }
        return SchemaSnapshot(tables, rows, indexes)
    }
}
