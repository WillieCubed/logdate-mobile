package app.logdate.client.database

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetainedDatabaseKeyRequirementTest {
    private val folder = createTempDirectory("retained-database-key").toFile()

    @AfterTest
    fun cleanup() {
        folder.deleteRecursively()
    }

    @Test
    fun `positively identified legacy plaintext permits creation of its first encryption key`() {
        val file = File(folder, DATABASE_NAME).apply { writeBytes("SQLite format 3\u0000".encodeToByteArray() + ByteArray(4080)) }
        assertFalse(retainedDatabaseNeedsExistingKey(file))
    }

    @Test
    fun `encrypted unknown and truncated retained files require an existing key`() {
        for (bytes in listOf(ByteArray(4096) { 8 }, "SQLite format".encodeToByteArray(), byteArrayOf(8))) {
            val file = File(folder, DATABASE_NAME).apply { writeBytes(bytes) }
            assertTrue(retainedDatabaseNeedsExistingKey(file))
        }
    }

    @Test
    fun `missing and empty files permit the first encryption key`() {
        val file = File(folder, DATABASE_NAME)
        assertFalse(retainedDatabaseNeedsExistingKey(file))
        file.createNewFile()
        assertFalse(retainedDatabaseNeedsExistingKey(file))
    }
}
