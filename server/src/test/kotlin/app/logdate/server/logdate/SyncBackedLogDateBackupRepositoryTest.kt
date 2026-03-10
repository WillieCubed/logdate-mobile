package app.logdate.server.logdate

import app.logdate.server.sync.InMemorySyncRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SyncBackedLogDateBackupRepositoryTest {
    @Test
    fun `backup metadata round trips through sync-backed repository`() {
        val repository = SyncBackedLogDateBackupRepository(InMemorySyncRepository())
        val userId = UUID.randomUUID()
        val backupId = UUID.randomUUID()

        val created =
            repository.createBackup(
                userId = userId,
                backup =
                    LogDateBackup(
                        id = backupId,
                        userId = userId,
                        deviceId = "device-1",
                        manifest = """{"version":1}""",
                        storagePath = "users/$userId/backups/$backupId.enc",
                        createdAt = 10L,
                        sizeBytes = 128L,
                    ),
            )

        val fetched = repository.getBackup(userId, backupId)
        val listed = repository.listBackups(userId)

        assertNotNull(fetched)
        assertEquals(created, fetched)
        assertEquals(listOf(created), listed)

        repository.deleteBackup(userId, backupId)

        assertNull(repository.getBackup(userId, backupId))
    }
}
