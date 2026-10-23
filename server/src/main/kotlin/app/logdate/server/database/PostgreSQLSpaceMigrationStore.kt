package app.logdate.server.database

import app.logdate.server.migration.SpaceMigration
import app.logdate.server.migration.SpaceMigrationStore
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.sync.SpaceMigrationPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant

/** Mirrors V50: one row per account moving into its personal space. */
object SpaceMigrationsTable : Table("space_migrations") {
    val accountId = javaUUID("account_id")
    val migrationId = varchar("migration_id", 64)
    val phase = varchar("phase", 16)
    val version = long("version")
    val startedAt = timestamp("started_at")
    val updatedAt = timestamp("updated_at")
    val holdStartedAt = timestamp("hold_started_at").nullable()
    val switchedAt = timestamp("switched_at").nullable()
    val switchedAtSeq = long("switched_at_seq").nullable()
    val revertedAt = timestamp("reverted_at").nullable()
    val cleanedAt = timestamp("cleaned_at").nullable()
    override val primaryKey = PrimaryKey(accountId)
}

/** A compare-and-set that found the row or the account changed; rolls the transaction back. */
private class SpaceMigrationRaceException : RuntimeException()

/**
 * Stores each account's move in `space_migrations`. A change applies only while the row still has the
 * version it was read with, and a switch updates the account's storage mode in the same transaction.
 */
class PostgreSQLSpaceMigrationStore : SpaceMigrationStore {
    override suspend fun get(accountId: java.util.UUID): SpaceMigration? =
        withContext(Dispatchers.IO) {
            transaction {
                SpaceMigrationsTable
                    .selectAll()
                    .where { SpaceMigrationsTable.accountId eq accountId }
                    .singleOrNull()
                    ?.toMigration()
            }
        }

    override suspend fun replace(
        expected: SpaceMigration?,
        updated: SpaceMigration,
    ): Boolean = withContext(Dispatchers.IO) { transaction { write(expected, updated) } }

    override suspend fun replaceAndSwitch(
        expected: SpaceMigration,
        updated: SpaceMigration,
        from: AccountStorageMode,
        to: AccountStorageMode,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                transaction {
                    val moved =
                        AccountsTable.update({
                            (AccountsTable.id eq updated.accountId) and (AccountsTable.storageMode eq from.wireValue)
                        }) { it[storageMode] = to.wireValue }
                    if (moved != 1 || !write(expected, updated)) throw SpaceMigrationRaceException()
                    true
                }
            } catch (_: SpaceMigrationRaceException) {
                false
            }
        }

    override suspend fun cleanupDue(
        cutoff: Instant,
        limit: Int,
    ): List<SpaceMigration> =
        withContext(Dispatchers.IO) {
            transaction {
                val switched =
                    (SpaceMigrationsTable.phase eq SpaceMigrationPhase.SWITCHED.wireValue) and (SpaceMigrationsTable.switchedAt less cutoff)
                val unfinished =
                    (SpaceMigrationsTable.phase eq SpaceMigrationPhase.CLEANED.wireValue) and SpaceMigrationsTable.cleanedAt.isNull()
                SpaceMigrationsTable
                    .selectAll()
                    .where { switched or unfinished }
                    .orderBy(SpaceMigrationsTable.switchedAt to SortOrder.ASC)
                    .limit(limit)
                    .map { it.toMigration() }
            }
        }

    /** Inserts [updated] when [expected] is null, otherwise updates the row still at [expected]'s version. */
    private fun write(
        expected: SpaceMigration?,
        updated: SpaceMigration,
    ): Boolean {
        if (expected == null) {
            return SpaceMigrationsTable.insertIgnore { it.fill(updated) }.insertedCount == 1
        }
        return SpaceMigrationsTable.update({
            (SpaceMigrationsTable.accountId eq updated.accountId) and (SpaceMigrationsTable.version eq expected.version)
        }) { it.fill(updated) } == 1
    }

    private fun UpdateBuilder<*>.fill(migration: SpaceMigration) {
        this[SpaceMigrationsTable.accountId] = migration.accountId
        this[SpaceMigrationsTable.migrationId] = migration.migrationId
        this[SpaceMigrationsTable.phase] = migration.phase.wireValue
        this[SpaceMigrationsTable.version] = migration.version
        this[SpaceMigrationsTable.startedAt] = migration.startedAt
        this[SpaceMigrationsTable.updatedAt] = migration.updatedAt
        this[SpaceMigrationsTable.holdStartedAt] = migration.holdStartedAt
        this[SpaceMigrationsTable.switchedAt] = migration.switchedAt
        this[SpaceMigrationsTable.switchedAtSeq] = migration.switchedAtSeq
        this[SpaceMigrationsTable.revertedAt] = migration.revertedAt
        this[SpaceMigrationsTable.cleanedAt] = migration.cleanedAt
    }

    private fun ResultRow.toMigration() =
        SpaceMigration(
            accountId = this[SpaceMigrationsTable.accountId],
            migrationId = this[SpaceMigrationsTable.migrationId],
            phase = SpaceMigrationPhase.fromWireValue(this[SpaceMigrationsTable.phase]),
            startedAt = this[SpaceMigrationsTable.startedAt],
            updatedAt = this[SpaceMigrationsTable.updatedAt],
            version = this[SpaceMigrationsTable.version],
            holdStartedAt = this[SpaceMigrationsTable.holdStartedAt],
            switchedAt = this[SpaceMigrationsTable.switchedAt],
            switchedAtSeq = this[SpaceMigrationsTable.switchedAtSeq],
            revertedAt = this[SpaceMigrationsTable.revertedAt],
            cleanedAt = this[SpaceMigrationsTable.cleanedAt],
        )
}
