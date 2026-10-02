package app.logdate.server.database

import app.logdate.server.accountkeys.AccountKeyRepository
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

object AccountKeyVaultTable : Table("account_key_vault") {
    val accountId = javaUUID("account_id").references(AccountsTable.id, onDelete = ReferenceOption.CASCADE)
    val ciphertext = binary("ciphertext")

    override val primaryKey = PrimaryKey(accountId)
}

class PostgreSQLAccountKeyRepository : AccountKeyRepository {
    override suspend fun get(accountId: UUID): ByteArray? =
        transaction {
            AccountKeyVaultTable
                .selectAll()
                .where { AccountKeyVaultTable.accountId eq accountId }
                .singleOrNull()
                ?.get(AccountKeyVaultTable.ciphertext)
        }

    override suspend fun insertIfAbsent(
        accountId: UUID,
        ciphertext: ByteArray,
    ): Boolean =
        transaction {
            AccountKeyVaultTable
                .insertIgnore {
                    it[AccountKeyVaultTable.accountId] = accountId
                    it[AccountKeyVaultTable.ciphertext] = ciphertext
                }.insertedCount == 1
        }
}
