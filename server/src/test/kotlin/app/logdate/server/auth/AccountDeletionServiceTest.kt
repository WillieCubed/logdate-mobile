package app.logdate.server.auth

import app.logdate.server.logdate.InMemoryLogDateBackupRepository
import app.logdate.server.logdate.InMemoryLogDateBlobStorage
import app.logdate.server.logdate.InMemoryLogDateMediaRepository
import app.logdate.server.logdate.LogDateBackup
import app.logdate.server.logdate.LogDateMedia
import app.logdate.server.logdate.asLogDateMediaBlobRepository
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class AccountDeletionServiceTest {
    @Test
    fun `deletes external media and backup bytes before removing the account`() =
        runTest {
            val accountId = Uuid.random()
            val legacyAccountId = accountId.toJavaUuid()
            val accountRepository = InMemoryAccountRepository()
            val mediaRepository = InMemoryLogDateMediaRepository()
            val backupRepository = InMemoryLogDateBackupRepository()
            val blobStorage = InMemoryLogDateBlobStorage()
            accountRepository.create(testAccount(accountId))

            val mediaId = UUID.randomUUID()
            val mediaPath =
                blobStorage.uploadMedia(
                    userId = legacyAccountId,
                    mediaId = mediaId,
                    fileName = "voice-note.m4a",
                    mimeType = "audio/mp4",
                    data = byteArrayOf(1, 2, 3),
                )
            mediaRepository.upsertMedia(
                legacyAccountId,
                LogDateMedia(
                    mediaId = mediaId.toString(),
                    contentId = "entry-1",
                    userId = legacyAccountId,
                    fileName = "voice-note.m4a",
                    mimeType = "audio/mp4",
                    sizeBytes = 3,
                    data = byteArrayOf(),
                    storagePath = mediaPath,
                    createdAt = 1,
                    version = 1,
                    deviceId = DeviceId("device-1"),
                ),
            )

            val backupId = UUID.randomUUID()
            val backupPath = blobStorage.uploadBackup(legacyAccountId, backupId, byteArrayOf(4, 5, 6))
            backupRepository.createBackup(
                legacyAccountId,
                LogDateBackup(
                    id = backupId,
                    userId = legacyAccountId,
                    deviceId = "device-1",
                    manifest = "{}",
                    storagePath = backupPath,
                    createdAt = 2,
                    sizeBytes = 3,
                ),
            )

            assertNotNull(blobStorage.getBlob(mediaPath))
            assertNotNull(blobStorage.getBlob(backupPath))

            val summary =
                AccountDeletionService(
                    accountRepository = accountRepository,
                    mediaBlobRepository = mediaRepository.asLogDateMediaBlobRepository(),
                    backupRepository = backupRepository,
                    blobStorage = blobStorage,
                ).deleteAccount(accountId)

            assertEquals(AccountDeletionService.Summary(1, 1, true), summary)
            assertNull(blobStorage.getBlob(mediaPath))
            assertNull(blobStorage.getBlob(backupPath))
            assertNull(accountRepository.findById(accountId))
        }

    @Test
    fun `deletes the account when external blob storage is disabled`() =
        runTest {
            val accountId = Uuid.random()
            val accountRepository = InMemoryAccountRepository()
            accountRepository.create(testAccount(accountId))

            val summary =
                AccountDeletionService(
                    accountRepository = accountRepository,
                    mediaBlobRepository = InMemoryLogDateMediaRepository().asLogDateMediaBlobRepository(),
                    backupRepository = InMemoryLogDateBackupRepository(),
                    blobStorage = null,
                ).deleteAccount(accountId)

            assertEquals(AccountDeletionService.Summary(0, 0, true), summary)
            assertNull(accountRepository.findById(accountId))
        }

    private fun testAccount(id: Uuid): Account =
        Account(
            id = id,
            username = "delete-me",
            displayName = "Delete Me",
            createdAt = Clock.System.now(),
        )
}
