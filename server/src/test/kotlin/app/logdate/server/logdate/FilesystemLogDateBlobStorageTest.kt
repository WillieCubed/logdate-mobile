package app.logdate.server.logdate

import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.UUID
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `backup file streams through storage without a byte array request`() {
        val root = Files.createTempDirectory("logdate-blob-file-test")
        val source = Files.createTempFile("logdate-blob-source-", ".bin")
        val destination = Files.createTempFile("logdate-blob-destination-", ".bin")
        try {
            Files.write(source, byteArrayOf(1, 2, 3, 4))
            val storage = FilesystemLogDateBlobStorage(root)
            val path =
                storage.putBlobFile(
                    LogDateBlobFileWriteRequest(
                        ownerId = UUID.randomUUID(),
                        namespace = LogDateBlobNamespace.BACKUP,
                        blobId = "backup-file",
                        contentType = "application/octet-stream",
                        path = source,
                    ),
                )
            assertTrue(storage.getBlobFile(path, destination))
            kotlin.test.assertContentEquals(Files.readAllBytes(source), Files.readAllBytes(destination))
        } finally {
            Files.deleteIfExists(source)
            Files.deleteIfExists(destination)
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test
    fun `cancelled backup file copy leaves no committed blob or partial download`() {
        val root = Files.createTempDirectory("logdate-blob-cancel-test")
        val source = Files.createTempFile("logdate-blob-cancel-source-", ".bin")
        val destination = Files.createTempFile("logdate-blob-cancel-destination-", ".bin")
        try {
            RandomAccessFile(source.toFile(), "rw").use { it.setLength(1024L * 1024) }
            val storage = FilesystemLogDateBlobStorage(root)
            val request =
                LogDateBlobFileWriteRequest(
                    UUID.randomUUID(),
                    LogDateBlobNamespace.BACKUP,
                    "cancelled",
                    contentType = "application/octet-stream",
                    path = source,
                    checkActive = { throw kotlin.coroutines.cancellation.CancellationException("cancelled") },
                )
            assertFailsWith<kotlin.coroutines.cancellation.CancellationException> { storage.putBlobFile(request) }
            assertTrue(Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.count() == 0L })

            val path = storage.putBlobFile(request.copy(checkActive = {}))
            assertFailsWith<kotlin.coroutines.cancellation.CancellationException> {
                storage.getBlobFile(path, destination) {
                    throw kotlin.coroutines.cancellation.CancellationException("cancelled")
                }
            }
            assertFalse(Files.exists(destination))
        } finally {
            Files.deleteIfExists(source)
            Files.deleteIfExists(destination)
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

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
