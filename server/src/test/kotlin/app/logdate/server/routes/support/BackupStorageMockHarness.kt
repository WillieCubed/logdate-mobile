package app.logdate.server.routes.support

import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.LogDateBlobWriteRequest
import app.logdate.server.sync.GcsMediaStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

/**
 * A test harness for mocking GCS-based backup storage.
 *
 * This class encapsulates a mocked [GcsMediaStorage] and a capturing slot for
 * capturing backup write requests, facilitating assertions on backup payloads
 * and namespaces in synchronization tests.
 */
data class BackupStorageMockHarness(
    val storage: GcsMediaStorage,
    val uploadedRequest: io.mockk.CapturingSlot<LogDateBlobWriteRequest>,
)

fun createBackupStorageMock(
    storagePath: String,
    downloadedPayload: ByteArray = "encrypted-data".toByteArray(),
): BackupStorageMockHarness {
    val storage = mockk<GcsMediaStorage>()
    val uploadedRequest = slot<LogDateBlobWriteRequest>()
    every { storage.putBlob(capture(uploadedRequest)) } answers {
        check(uploadedRequest.captured.namespace == LogDateBlobNamespace.BACKUP)
        storagePath
    }
    every { storage.getBlob(any()) } returns downloadedPayload
    every { storage.getSignedDownloadUrl(any(), any()) } returns "https://signed-url.com"
    return BackupStorageMockHarness(storage, uploadedRequest)
}
