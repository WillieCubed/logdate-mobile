package app.logdate.client.database

import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PortableCloudBackupKeyTest {
    @Test
    fun `a fresh installation without a database may back up preferences`() {
        val root = createTempDirectory("cloud-key-fresh").toFile()
        try {
            requirePortableCloudBackupKey(File(root, "logdate"), File(root, "passphrase_backup")) { _, _ ->
                error("No database should be opened")
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a malformed orphan key cannot poison a fresh cloud restore`() {
        val root = createTempDirectory("cloud-key-orphan").toFile()
        try {
            val key = File(root, "passphrase_backup").apply { writeText("not a key") }
            assertFailsWith<IllegalStateException> {
                requirePortableCloudBackupKey(File(root, "logdate"), key) { _, _ ->
                    error("No database should be opened")
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a retained database without committed portable key cannot replace a cloud backup`() {
        val root = createTempDirectory("cloud-key-missing").toFile()
        try {
            val database = File(root, "logdate").apply { writeText("existing encrypted journal") }
            val key = File(root, "passphrase_backup")
            assertFailsWith<IllegalStateException> {
                requirePortableCloudBackupKey(database, key) { _, _ -> true }
            }
            assertEquals("existing encrypted journal", database.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `malformed portable key cannot replace a cloud backup`() {
        val root = createTempDirectory("cloud-key-malformed").toFile()
        try {
            val database = File(root, "logdate").apply { writeText("existing encrypted journal") }
            val key = File(root, "passphrase_backup").apply { writeText("not a key") }
            assertFailsWith<IllegalStateException> {
                requirePortableCloudBackupKey(database, key) { _, _ -> true }
            }
            assertEquals("not a key", key.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a mismatched portable key cannot replace a cloud backup`() {
        val root = createTempDirectory("cloud-key-mismatch").toFile()
        try {
            val database = File(root, "logdate").apply { writeText("existing encrypted journal") }
            val key = File(root, "passphrase_backup").apply { writeText(Base64.encode(ByteArray(32) { 5 })) }
            assertFailsWith<IllegalStateException> {
                requirePortableCloudBackupKey(database, key) { _, _ -> false }
            }
            assertEquals("existing encrypted journal", database.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a validated key allows encrypted journal export without modifying either source`() {
        val root = createTempDirectory("cloud-key-valid").toFile()
        try {
            val database = File(root, "logdate").apply { writeText("existing encrypted journal") }
            val material = ByteArray(32) { 7 }
            val key = File(root, "passphrase_backup").apply { writeText(Base64.encode(material)) }
            requirePortableCloudBackupKey(database, key) { seenDatabase, seenKey ->
                assertEquals(database, seenDatabase)
                assertEquals(material.toList(), seenKey.toList())
                true
            }
            assertEquals("existing encrypted journal", database.readText())
            assertEquals(Base64.encode(material), key.readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
