package app.logdate.server.entitlements

import app.logdate.server.database.LogDateBackupsTable
import app.logdate.server.database.LogDateMediaRecordsTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * [UsageCalculator] backed by the media + backups tables. Counts non-deleted media bytes plus
 * backup bytes towards storage; counts backup rows for the backup-count dimension.
 *
 * The [database] parameter is explicit rather than relying on Exposed's global default: the rest
 * of the server's Postgres repos use the thread-local default because they were scaffolded before
 * we had a DI story, but new code should take its dependencies by constructor so tests and
 * multi-database setups stay tractable.
 */
class DatabaseUsageCalculator(
    private val database: Database,
) : UsageCalculator {
    override suspend fun storageBytes(accountId: UUID): Long =
        transaction(database) {
            val media =
                LogDateMediaRecordsTable
                    .select(LogDateMediaRecordsTable.sizeBytes.sum())
                    .where {
                        (LogDateMediaRecordsTable.userId eq accountId) and
                            (LogDateMediaRecordsTable.deleted eq false)
                    }.singleOrNull()
                    ?.get(LogDateMediaRecordsTable.sizeBytes.sum()) ?: 0L
            val backups =
                LogDateBackupsTable
                    .select(LogDateBackupsTable.sizeBytes.sum())
                    .where { LogDateBackupsTable.userId eq accountId }
                    .singleOrNull()
                    ?.get(LogDateBackupsTable.sizeBytes.sum()) ?: 0L
            media + backups
        }

    override suspend fun backupCount(accountId: UUID): Int =
        transaction(database) {
            LogDateBackupsTable
                .selectAll()
                .where { LogDateBackupsTable.userId eq accountId }
                .count()
                .toInt()
        }
}
