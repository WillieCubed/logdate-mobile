package app.logdate.server.routes.sync

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupTempFileTest {
    @Test
    fun `verified plaintext remains available until stream starts`() {
        val path = createPrivateBackupTempFile("logdate-backup-verified-", ".bin")
        val lease = BackupResponseFileLease(path)

        assertTrue(Files.exists(path))

        lease.close()
        assertFalse(Files.exists(path))
    }

    @Test
    fun `response lease close is idempotent`() {
        val path = createPrivateBackupTempFile("logdate-backup-verified-", ".bin")
        val lease = BackupResponseFileLease(path)

        assertTrue(Files.exists(path))
        lease.close()
        lease.close()
        assertFalse(Files.exists(path))
    }

    @Test
    fun `verified plaintext expires if response body never starts`() {
        val path = createPrivateBackupTempFile("logdate-backup-verified-", ".bin")
        val lease = BackupResponseFileLease(path, maxLifetimeMillis = 10)
        try {
            val deadline = System.currentTimeMillis() + 5000
            while (Files.exists(path) && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertFalse(Files.exists(path))
        } finally {
            lease.close()
        }
    }

    @Test
    fun `verified plaintext is removed when response stream completes`() {
        val path = createPrivateBackupTempFile("logdate-backup-verified-", ".bin")
        val lease = BackupResponseFileLease(path)

        lease.close()

        assertFalse(Files.exists(path))
    }

    @Test
    fun `backup staging file is owner readable and writable only`() {
        val path = createPrivateBackupTempFile("logdate-backup-upload-", ".bin")
        try {
            assertTrue(Files.getPosixFilePermissions(path) == PosixFilePermissions.fromString("rw-------"))
        } finally {
            deletePrivateBackupTempFile(path)
        }
    }

    @Test
    fun `sweep removes crash abandoned archive files but preserves active and unrelated files`() {
        val directory = Files.createTempDirectory("backup-temp-sweep-")
        try {
            val old = Files.createTempFile(directory, "logdate-backup-upload-", ".bin")
            val active = Files.createTempFile(directory, "logdate-backup-verified-", ".bin")
            val unrelated = Files.createTempFile(directory, "unrelated-", ".bin")
            val now = System.currentTimeMillis()
            listOf(old, active, unrelated).forEach {
                Files.setLastModifiedTime(it, FileTime.fromMillis(now - 2L * 24 * 60 * 60 * 1000))
            }

            sweepAbandonedBackupTempFiles(directory, now, setOf(active))

            assertFalse(Files.exists(old))
            assertTrue(Files.exists(active))
            assertTrue(Files.exists(unrelated))
        } finally {
            Files.list(directory).use { paths -> paths.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(directory)
        }
    }
}
