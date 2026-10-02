package app.logdate.client.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.logdate.client.database.migrations.MIGRATION_48_49
import app.logdate.client.database.migrations.MIGRATION_49_50
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PendingOperationMigrationJvmTest {
    private val databaseFile = Files.createTempFile("logdate-outbox-migration", ".db")
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
    fun cleanup() {
        Files.deleteIfExists(databaseFile)
    }

    @Test
    fun `legacy outbox rows gain distinct random operation identities`() {
        helper.createDatabase(48).use { connection ->
            connection.execSQL(
                """INSERT INTO pending_uploads
                (ownerId, serverOrigin, entityType, entityId, operation, createdAt, retryCount)
                VALUES ('owner', 'first', 'DRAFT', 'draft', 'DELETE', 10, 7),
                       ('owner', 'second', 'DRAFT', 'draft', 'UPDATE', 11, 3)""",
            )
        }
        helper.runMigrationsAndValidate(50, listOf(MIGRATION_48_49, MIGRATION_49_50)).use { connection ->
            connection
                .prepare(
                    "SELECT serverOrigin, operation, retryCount, expectedServerVersion, operationId FROM pending_uploads ORDER BY serverOrigin",
                ).use { row ->
                    assertTrue(row.step())
                    assertEquals("first", row.getText(0))
                    assertEquals("DELETE", row.getText(1))
                    assertEquals(7L, row.getLong(2))
                    assertTrue(row.isNull(3))
                    val first = row.getText(4)
                    assertRandom(first)
                    assertTrue(row.step())
                    assertEquals("UPDATE", row.getText(1))
                    assertEquals(3L, row.getLong(2))
                    assertTrue(row.isNull(3))
                    val second = row.getText(4)
                    assertRandom(second)
                    assertTrue(first != second)
                }
        }
    }

    private fun assertRandom(value: String) {
        assertEquals(value, Uuid.parse(value).toString())
        assertEquals('4', value[14])
        assertTrue(value[19] in "89ab")
    }
}
