package app.logdate.server.logdate

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryLogDateBackupRepositoryTest {
    @Test
    fun `backup repository lists most recent backups first and isolates users`() {
        val repository = InMemoryLogDateBackupRepository()
        val userId = UUID.randomUUID()
        val secondUserId = UUID.randomUUID()
        val firstBackup =
            LogDateBackup(
                id = UUID.randomUUID(),
                userId = userId,
                deviceId = "device-a",
                manifest = """{"v":1}""",
                storagePath = "users/$userId/backups/one.enc",
                createdAt = 10L,
                sizeBytes = 100L,
            )
        val secondBackup =
            LogDateBackup(
                id = UUID.randomUUID(),
                userId = userId,
                deviceId = "device-b",
                manifest = """{"v":2}""",
                storagePath = "users/$userId/backups/two.enc",
                createdAt = 20L,
                sizeBytes = 200L,
            )

        repository.createBackup(userId, firstBackup)
        repository.createBackup(userId, secondBackup)
        repository.createBackup(
            secondUserId,
            firstBackup.copy(
                id = UUID.randomUUID(),
                userId = secondUserId,
                storagePath = "users/$secondUserId/backups/other.enc",
            ),
        )

        assertEquals(listOf(secondBackup.id, firstBackup.id), repository.listBackups(userId).map(LogDateBackup::id))
        assertEquals(secondBackup.id, repository.getBackup(userId, secondBackup.id)?.id)

        repository.deleteBackup(userId, secondBackup.id)

        assertNull(repository.getBackup(userId, secondBackup.id))
        assertEquals(1, repository.listBackups(userId).size)
        assertEquals(1, repository.listBackups(secondUserId).size)
    }
}
