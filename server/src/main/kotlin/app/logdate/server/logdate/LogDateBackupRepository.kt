package app.logdate.server.logdate

import app.logdate.server.sync.BackupRecord
import app.logdate.server.sync.SyncRepository
import java.util.UUID

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
 * Transitional backup metadata implementation backed by the existing sync repository.
 */
class SyncBackedLogDateBackupRepository(
    private val syncRepository: SyncRepository,
) : LogDateBackupRepository {
    override fun createBackup(
        userId: UUID,
        backup: LogDateBackup,
    ): LogDateBackup = syncRepository.createBackupRecord(userId, backup.toSyncRecord()).toLogDateBackup()

    override fun getBackup(
        userId: UUID,
        id: UUID,
    ): LogDateBackup? = syncRepository.getBackupRecord(userId, id)?.toLogDateBackup()

    override fun listBackups(userId: UUID): List<LogDateBackup> = syncRepository.listBackups(userId).map(BackupRecord::toLogDateBackup)

    override fun deleteBackup(
        userId: UUID,
        id: UUID,
    ) {
        syncRepository.deleteBackup(userId, id)
    }
}

private fun LogDateBackup.toSyncRecord(): BackupRecord =
    BackupRecord(
        id = id,
        userId = userId,
        deviceId = deviceId,
        manifest = manifest,
        storagePath = storagePath,
        createdAt = createdAt,
        sizeBytes = sizeBytes,
    )

private fun BackupRecord.toLogDateBackup(): LogDateBackup =
    LogDateBackup(
        id = id,
        userId = userId,
        deviceId = deviceId,
        manifest = manifest,
        storagePath = storagePath,
        createdAt = createdAt,
        sizeBytes = sizeBytes,
    )
