package app.logdate.server.database

import io.github.aakira.napier.Napier
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/**
 * Running totals of the bytes each account stores, one column for each kind of data counted against
 * its storage quota. Every write, delete and purge of that data changes its column in the same
 * transaction, holding the row's lock, so reading an account's usage reads one row and concurrent
 * writes never lose an update. Database triggers keep [mediaBytes] and [backupBytes] current, so
 * every revision of the server counts the same way; the space store keeps [spaceBytes] current
 * itself, because it checks the quota under the same lock.
 */
object AccountStorageUsageTable : Table("account_storage_usage") {
    val accountId = javaUUID("account_id").references(AccountsTable.id, onDelete = ReferenceOption.CASCADE)
    val spaceBytes = long("space_bytes").default(0L)
    val mediaBytes = long("media_bytes").default(0L)
    val backupBytes = long("backup_bytes").default(0L)
    override val primaryKey = PrimaryKey(accountId)
}

/** One kind of data counted against the storage quota, and the running total that counts it. */
enum class StorageKind(
    val column: Column<Long>,
) {
    SPACE(AccountStorageUsageTable.spaceBytes),
    MEDIA(AccountStorageUsageTable.mediaBytes),
    BACKUP(AccountStorageUsageTable.backupBytes),
}

/** An account's running totals as locked by [AccountStorageTotals.lock]. */
data class StorageTotals(
    val spaceBytes: Long,
    val mediaBytes: Long,
    val backupBytes: Long,
)

/** Reads and changes [AccountStorageUsageTable] inside the caller's transaction. */
internal object AccountStorageTotals {
    /**
     * Creates [accountId]'s row if needed, locks it until the transaction ends, and returns its totals.
     * Returns null for an account that doesn't exist, whose data nothing counts.
     */
    fun lock(accountId: UUID): StorageTotals? {
        val exists =
            AccountsTable
                .select(AccountsTable.id)
                .where { AccountsTable.id eq accountId }
                .any()
        if (!exists) return null
        AccountStorageUsageTable.insertIgnore { it[AccountStorageUsageTable.accountId] = accountId }
        return AccountStorageUsageTable
            .select(AccountStorageUsageTable.spaceBytes, AccountStorageUsageTable.mediaBytes, AccountStorageUsageTable.backupBytes)
            .where { AccountStorageUsageTable.accountId eq accountId }
            .forUpdate()
            .single()
            .let {
                StorageTotals(
                    spaceBytes = it[AccountStorageUsageTable.spaceBytes],
                    mediaBytes = it[AccountStorageUsageTable.mediaBytes],
                    backupBytes = it[AccountStorageUsageTable.backupBytes],
                )
            }
    }

    /**
     * Writes [current] plus [delta] as [accountId]'s [kind] total; the row must already be locked. A
     * total can't be negative, so one that would be is stored as zero and logged as an error: the
     * totals have drifted from the rows they count.
     */
    fun set(
        accountId: UUID,
        kind: StorageKind,
        current: Long,
        delta: Long,
    ) {
        if (delta == 0L) return
        AccountStorageUsageTable.update({ AccountStorageUsageTable.accountId eq accountId }) {
            it[kind.column] = nonNegativeTotal(accountId, kind, current, delta)
        }
    }
}

/**
 * [current] plus [delta], or zero when that would be negative. Going below zero means a total no
 * longer matches what it counts, so it's logged as an error with the account and the numbers.
 */
internal fun nonNegativeTotal(
    accountId: UUID,
    kind: StorageKind,
    current: Long,
    delta: Long,
): Long {
    val next = current + delta
    if (next >= 0) return next
    Napier.e("Storage total would go below zero: account=$accountId kind=${kind.name} current=$current delta=$delta; stored 0")
    return 0L
}
