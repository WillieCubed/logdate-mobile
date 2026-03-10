package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.server.logdate.LogDateBackup
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostgreSQLLogDateBackupRepositoryTest {
    @Test
    fun `postgres backup repository stores lists and deletes backups`() {
        withH2Database(LogDateBackupsTable) {
            val repository = PostgreSQLLogDateBackupRepository()
            val userId = UUID.randomUUID()
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

            assertEquals(secondBackup.id, repository.listBackups(userId).first().id)
            assertEquals(firstBackup.id, repository.getBackup(userId, firstBackup.id)?.id)

            repository.deleteBackup(userId, firstBackup.id)

            assertNull(repository.getBackup(userId, firstBackup.id))
            assertEquals(listOf(secondBackup.id), repository.listBackups(userId).map(LogDateBackup::id))
        }
    }
}
