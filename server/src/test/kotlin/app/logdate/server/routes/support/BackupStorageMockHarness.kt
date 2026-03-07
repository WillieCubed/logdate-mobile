package app.logdate.server.routes.support

import app.logdate.server.sync.GcsMediaStorage
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

data class BackupStorageMockHarness(
    val storage: GcsMediaStorage,
    val uploadedPayload: CapturingSlot<ByteArray>,
)

fun createBackupStorageMock(
    storagePath: String,
    downloadedPayload: ByteArray = "encrypted-data".toByteArray(),
): BackupStorageMockHarness {
    val storage = mockk<GcsMediaStorage>()
    val uploadedPayload = slot<ByteArray>()
    every { storage.uploadBackup(any(), any(), capture(uploadedPayload)) } returns storagePath
    every { storage.downloadMedia(any()) } returns downloadedPayload
    every { storage.getSignedDownloadUrl(any(), any()) } returns "https://signed-url.com"
    return BackupStorageMockHarness(storage, uploadedPayload)
}
