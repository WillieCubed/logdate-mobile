package app.logdate.server.database

import app.logdate.server.logdate.JournalMergeOperation
import app.logdate.server.logdate.JournalMergeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/** Session locks cover repo writes as well as merge state, across server instances. */
class PostgreSQLJournalMergeStore(
    dataSource: DataSource? = null,
) : JournalMergeStore {
    private val source: DataSource by lazy {
        dataSource ?: checkNotNull(DatabaseConfig.initializedDataSource) { "Journal merge database has not been initialized" }
    }
    private val locks = ConcurrentHashMap<UUID, Mutex>()

    // Each active account holds one lock connection and may use a second for a repo write.
    private val activeAccounts = Semaphore(DatabaseConfig.CLOUD_RUN_REQUEST_CONCURRENCY / 2)

    override suspend fun <T> withAccountLock(
        userId: UUID,
        action: suspend () -> T,
    ): T =
        locks.getOrPut(userId) { Mutex() }.withLock {
            activeAccounts.withPermit {
                withContext(Dispatchers.IO) {
                    source.connection.use { connection ->
                        connection.autoCommit = true
                        val lockKey = userId.mostSignificantBits xor userId.leastSignificantBits
                        connection.prepareStatement("SELECT pg_advisory_lock(?)").use { statement ->
                            statement.setLong(1, lockKey)
                            statement.execute()
                        }
                        try {
                            action()
                        } finally {
                            withContext(NonCancellable + Dispatchers.IO) {
                                connection.prepareStatement("SELECT pg_advisory_unlock(?)").use { statement ->
                                    statement.setLong(1, lockKey)
                                    statement.execute()
                                }
                            }
                        }
                    }
                }
            }
        }

    override suspend fun operations(userId: UUID): List<JournalMergeOperation> =
        withContext(Dispatchers.IO) {
            source.connection.use { connection ->
                connection
                    .prepareStatement(
                        "SELECT operation_json, tombstone_version FROM journal_merge_operations WHERE account_id = ?",
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeQuery().use { rows ->
                            buildList {
                                while (rows.next()) {
                                    val version = rows.getLong("tombstone_version").let { if (rows.wasNull()) null else it }
                                    add(
                                        Json
                                            .decodeFromString<JournalMergeOperation>(
                                                rows.getString("operation_json"),
                                            ).copy(tombstoneVersion = version),
                                    )
                                }
                            }
                        }
                    }
            }
        }

    override suspend fun removeUnstarted(
        userId: UUID,
        operationId: String,
    ) {
        withContext(Dispatchers.IO) {
            source.connection.use { connection ->
                connection
                    .prepareStatement(
                        "DELETE FROM journal_merge_operations WHERE account_id = ? AND operation_id = ? AND started = FALSE",
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.setString(2, operationId)
                        statement.executeUpdate()
                    }
                connection.commit()
            }
        }
    }

    override suspend fun replaceIncomplete(
        userId: UUID,
        previousOperationId: String,
        replacement: JournalMergeOperation,
    ) {
        withContext(Dispatchers.IO) {
            source.connection.use { connection ->
                connection
                    .prepareStatement(
                        "DELETE FROM journal_merge_operations WHERE account_id = ? AND operation_id = ? AND completed = FALSE",
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.setString(2, previousOperationId)
                        check(statement.executeUpdate() == 1)
                    }
                connection
                    .prepareStatement(
                        "INSERT INTO journal_merge_operations (account_id, operation_id, source_id, operation_json, started, completed) VALUES (?, ?, ?, ?, ?, ?)",
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.setString(2, replacement.operationId)
                        statement.setString(3, replacement.sourceId)
                        statement.setString(4, Json.encodeToString(replacement))
                        statement.setBoolean(5, replacement.started)
                        statement.setBoolean(6, replacement.completed)
                        statement.executeUpdate()
                    }
                connection.commit()
            }
        }
    }

    override suspend fun save(
        userId: UUID,
        operation: JournalMergeOperation,
    ) {
        withContext(Dispatchers.IO) {
            source.connection.use { connection ->
                connection
                    .prepareStatement(
                        """
                        INSERT INTO journal_merge_operations
                            (account_id, operation_id, source_id, operation_json, started, completed, deleted_at, tombstone_version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (account_id, operation_id) DO UPDATE SET
                            operation_json = EXCLUDED.operation_json,
                            started = EXCLUDED.started,
                            completed = EXCLUDED.completed,
                            deleted_at = EXCLUDED.deleted_at,
                            tombstone_version = EXCLUDED.tombstone_version
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.setString(2, operation.operationId)
                        statement.setString(3, operation.sourceId)
                        statement.setString(4, Json.encodeToString(operation))
                        statement.setBoolean(5, operation.started)
                        statement.setBoolean(6, operation.completed)
                        statement.setObject(7, operation.deletedAt)
                        statement.setObject(8, operation.tombstoneVersion)
                        statement.executeUpdate()
                    }
                connection.commit()
            }
        }
    }

    override suspend fun purgeTombstones(
        userId: UUID?,
        olderThan: Long,
    ) {
        withContext(Dispatchers.IO) {
            source.connection.use { connection ->
                val userClause = if (userId == null) "" else " AND account_id = ?"
                connection
                    .prepareStatement(
                        "UPDATE journal_merge_operations SET tombstone_version = NULL WHERE completed = TRUE AND deleted_at < ?$userClause",
                    ).use { statement ->
                        statement.setLong(1, olderThan)
                        if (userId != null) statement.setObject(2, userId)
                        statement.executeUpdate()
                    }
                connection.commit()
            }
        }
    }
}
