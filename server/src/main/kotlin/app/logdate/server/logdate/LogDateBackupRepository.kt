package app.logdate.server.logdate

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * LogDate-owned encrypted backup metadata.
 */
data class LogDateBackup(
    val id: UUID,
    val userId: UUID,
    val deviceId: String,
    val manifest: String,
    val storagePath: String,
    val createdAt: Long,
    val sizeBytes: Long,
)

/**
 * Internal boundary for encrypted backup metadata operations.
 */
interface LogDateBackupRepository {
    fun createBackup(
        userId: UUID,
        backup: LogDateBackup,
    ): LogDateBackup

    fun getBackup(
        userId: UUID,
        id: UUID,
    ): LogDateBackup?

    fun listBackups(userId: UUID): List<LogDateBackup>

    fun deleteBackup(
        userId: UUID,
        id: UUID,
    )
}

/**
 * In-memory backup metadata implementation for tests and non-database server runs.
 */
class InMemoryLogDateBackupRepository : LogDateBackupRepository {
    private val rows = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, LogDateBackup>>()

    override fun createBackup(
        userId: UUID,
        backup: LogDateBackup,
    ): LogDateBackup {
        val stored = backup.copy(userId = userId)
        rowsForUser(userId)[stored.id] = stored
        return stored
    }

    override fun getBackup(
        userId: UUID,
        id: UUID,
    ): LogDateBackup? = rowsForUser(userId)[id]

    override fun listBackups(userId: UUID): List<LogDateBackup> =
        rowsForUser(userId)
            .values
            .sortedByDescending(LogDateBackup::createdAt)

    override fun deleteBackup(
        userId: UUID,
        id: UUID,
    ) {
        rowsForUser(userId).remove(id)
    }

    private fun rowsForUser(userId: UUID): ConcurrentHashMap<UUID, LogDateBackup> = rows.getOrPut(userId) { ConcurrentHashMap() }
}
