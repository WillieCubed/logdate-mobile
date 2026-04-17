package app.logdate.server.routes.support

import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateBlobWriteRequest
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.ConcurrentHashMap

/**
 * An in-memory [LogDateBlobStorage] that accepts any namespace — unlike
 * [createBackupStorageMock], which enforces BACKUP-only writes via a `check()`. Tests that
 * exercise both the media and backup paths need a mock that doesn't trip on either, so this
 * shared factory lets them skip hand-rolled storage doubles.
 */
fun createPermissiveBlobStorage(): LogDateBlobStorage {
    val blobs = ConcurrentHashMap<String, ByteArray>()
    val storage = mockk<LogDateBlobStorage>()
    every { storage.putBlob(any()) } answers {
        val req = firstArg<LogDateBlobWriteRequest>()
        val path = "ns/${req.namespace.name.lowercase()}/${req.ownerId}/${req.blobId}"
        blobs[path] = req.bytes
        path
    }
    every { storage.getBlob(any()) } answers { blobs[firstArg<String>()] }
    every { storage.deleteBlob(any()) } answers { blobs.remove(firstArg<String>()) != null }
    every { storage.getSignedDownloadUrl(any(), any()) } returns "https://signed-url.example.com"
    return storage
}
