package app.logdate.server.database

import app.logdate.server.logdate.LogDateBackup
import app.logdate.server.logdate.LogDateBackupRepository
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

internal class PostgreSQLLogDateBackupRepository : LogDateBackupRepository {
    override fun createBackup(
        userId: UUID,
        backup: LogDateBackup,
    ): LogDateBackup =
        transaction {
            LogDateBackupsTable.insert {
                it[id] = backup.id
                it[LogDateBackupsTable.userId] = userId
                it[deviceId] = backup.deviceId
                it[manifest] = backup.manifest
                it[storagePath] = backup.storagePath
                it[createdAt] = backup.createdAt
                it[sizeBytes] = backup.sizeBytes
            }
            backup.copy(userId = userId)
        }

    override fun getBackup(
        userId: UUID,
        id: UUID,
    ): LogDateBackup? =
        transaction {
            LogDateBackupsTable
                .selectAll()
                .where {
                    (LogDateBackupsTable.userId eq userId) and
                        (LogDateBackupsTable.id eq id)
                }.singleOrNull()
                ?.toLogDateBackup()
        }

    override fun listBackups(userId: UUID): List<LogDateBackup> =
        transaction {
            LogDateBackupsTable
                .selectAll()
                .where { LogDateBackupsTable.userId eq userId }
                .orderBy(LogDateBackupsTable.createdAt to SortOrder.DESC)
                .map { row -> row.toLogDateBackup() }
        }

    override fun deleteBackup(
        userId: UUID,
        id: UUID,
    ) {
        transaction {
            LogDateBackupsTable.deleteWhere {
                (LogDateBackupsTable.userId eq userId) and
                    (LogDateBackupsTable.id eq id)
            }
        }
    }

    private fun ResultRow.toLogDateBackup(): LogDateBackup =
        LogDateBackup(
            id = this[LogDateBackupsTable.id],
            userId = this[LogDateBackupsTable.userId],
            deviceId = this[LogDateBackupsTable.deviceId],
            manifest = this[LogDateBackupsTable.manifest],
            storagePath = this[LogDateBackupsTable.storagePath],
            createdAt = this[LogDateBackupsTable.createdAt],
            sizeBytes = this[LogDateBackupsTable.sizeBytes],
        )
}
