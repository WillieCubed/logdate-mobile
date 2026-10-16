package app.logdate.server.routes.sync

import app.logdate.server.logdate.LogDateBackup
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateMedia
import app.logdate.server.sync.MediaAccessPolicy
import app.logdate.shared.model.sync.DeviceId
import io.ktor.server.application.ApplicationCall
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The server-URL fallback is for signing failures only. A cancelled request must stay cancelled
 * rather than being answered with a fallback URL.
 */
class DownloadUrlSigningCancellationTest {
    private val policy = MediaAccessPolicy(useSignedUrls = true, signedUrlTtlHours = 1)
    private val userId = UUID.randomUUID()
    private val storage =
        mockk<LogDateBlobStorage> {
            every { getSignedDownloadUrl(any(), any()) } throws CancellationException("request cancelled")
        }

    @Test
    fun `media download url resolution propagates cancellation`() =
        runTest {
            val media =
                LogDateMedia(
                    mediaId = "media-1",
                    contentId = "content-1",
                    userId = userId,
                    fileName = "f.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 3,
                    data = ByteArray(0),
                    storagePath = "users/$userId/media/media-1/f.jpg",
                    createdAt = 1,
                    version = 0,
                    deviceId = DeviceId("device"),
                )

            assertFailsWith<CancellationException> {
                resolveMediaDownloadUrl(mockk<ApplicationCall>(), media, storage, policy)
            }
        }

    @Test
    fun `backup download url resolution propagates cancellation`() =
        runTest {
            val backupId = UUID.randomUUID()
            val backup =
                LogDateBackup(
                    id = backupId,
                    userId = userId,
                    deviceId = "device",
                    manifest = "{}",
                    storagePath = "users/$userId/backups/$backupId.enc",
                    createdAt = 1,
                    sizeBytes = 3,
                )

            assertFailsWith<CancellationException> {
                resolveBackupDownloadUrl(mockk<ApplicationCall>(), backup, storage, policy)
            }
        }
}
