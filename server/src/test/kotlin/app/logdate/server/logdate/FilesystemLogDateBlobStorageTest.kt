package app.logdate.server.logdate

import java.nio.file.Files
import java.util.UUID
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [FilesystemLogDateBlobStorage], validating that binary blobs are
 * correctly persisted to and retrieved from the local filesystem.
 *
 * This test suite covers the full lifecycle of a blob—including creation,
 * retrieval, and deletion—as well as environment-based initialization logic.
 */
class FilesystemLogDateBlobStorageTest {
    @Test
    fun `round-trips bytes through disk`() {
        val root = Files.createTempDirectory("logdate-blob-test")
        val storage = FilesystemLogDateBlobStorage(root)
        val owner = UUID.randomUUID()

        val path =
            storage.putBlob(
                LogDateBlobWriteRequest(
                    ownerId = owner,
                    namespace = LogDateBlobNamespace.MEDIA,
                    blobId = "media-1",
                    fileName = "alice.jpg",
                    contentType = "image/jpeg",
                    bytes = "hello".toByteArray(),
                ),
            )

        assertEquals("media/$owner/media-1", path)
        assertEquals("hello", String(storage.getBlob(path)!!))
    }

    @Test
    fun `delete removes the file from disk`() {
        val root = Files.createTempDirectory("logdate-blob-delete-test")
        val storage = FilesystemLogDateBlobStorage(root)
        val owner = UUID.randomUUID()
        val path =
            storage.putBlob(
                LogDateBlobWriteRequest(
                    ownerId = owner,
                    namespace = LogDateBlobNamespace.BACKUP,
                    blobId = "backup-1",
                    contentType = "application/octet-stream",
                    bytes = byteArrayOf(1, 2, 3),
                ),
            )
        assertTrue(root.resolve(path).exists())

        assertTrue(storage.deleteBlob(path))
        assertFalse(root.resolve(path).exists())
        assertNull(storage.getBlob(path))
    }

    @Test
    fun `delete of missing blob returns false without throwing`() {
        val storage = FilesystemLogDateBlobStorage(Files.createTempDirectory("logdate-blob-missing-test"))
        assertFalse(storage.deleteBlob("no/such/blob"))
    }

    @Test
    fun `fromEnvironment returns null when LOGDATE_BLOB_STORAGE_DIR is unset`() {
        assertNull(FilesystemLogDateBlobStorage.fromEnvironment { null })
        assertNull(FilesystemLogDateBlobStorage.fromEnvironment { "" })
        assertNull(FilesystemLogDateBlobStorage.fromEnvironment { "   " })
    }

    @Test
    fun `fromEnvironment creates the root directory when missing`() {
        val root = Files.createTempDirectory("logdate-blob-env-test").resolve("sub/path")
        assertFalse(root.exists())
        val storage = FilesystemLogDateBlobStorage.fromEnvironment { root.toString() }
        assertTrue(storage != null && root.exists())
    }
}
