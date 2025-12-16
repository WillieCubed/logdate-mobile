package app.logdate.server.database

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLAccountRepository : AccountRepository {
    
    override suspend fun save(account: Account): Account {
        return transaction {
            val existingAccount = AccountsTable.selectAll()
                .where { AccountsTable.id eq account.id.toJavaUUID() }
                .singleOrNull()
            
            if (existingAccount != null) {
                // Update existing account
                AccountsTable.update({ AccountsTable.id eq account.id.toJavaUUID() }) {
                    it[username] = account.username
                    it[displayName] = account.displayName
                    it[email] = account.email
                    it[bio] = account.bio
                    it[lastSignInAt] = account.lastSignInAt
                    it[isActive] = account.isActive
                    it[preferences] = account.preferences?.toString() ?: "{}"
                }
                account
            } else {
                // Insert new account
                AccountsTable.insert {
                    it[id] = account.id.toJavaUUID()
                    it[username] = account.username
                    it[displayName] = account.displayName
                    it[email] = account.email
                    it[bio] = account.bio
                    it[createdAt] = account.createdAt
                    it[lastSignInAt] = account.lastSignInAt
                    it[isActive] = account.isActive
                    it[preferences] = account.preferences?.toString() ?: "{}"
                }
                account
            }
        }
    }
    
    override suspend fun findById(id: Uuid): Account? {
        return transaction {
            AccountsTable.selectAll()
                .where { AccountsTable.id eq id.toJavaUUID() }
                .singleOrNull()
                ?.toAccount()
        }
    }
    
    override suspend fun findByUsername(username: String): Account? {
        return transaction {
            AccountsTable.selectAll()
                .where { AccountsTable.username eq username }
                .singleOrNull()
                ?.toAccount()
        }
    }
    
    override suspend fun findByEmail(email: String): Account? {
        return transaction {
            AccountsTable.selectAll()
                .where { AccountsTable.email eq email }
                .singleOrNull()
                ?.toAccount()
        }
    }
    
    override suspend fun usernameExists(username: String): Boolean {
        return transaction {
            AccountsTable.selectAll()
                .where { AccountsTable.username eq username }
                .count() > 0
        }
    }
    
    override suspend fun emailExists(email: String): Boolean {
        return transaction {
            AccountsTable.selectAll()
                .where { AccountsTable.email eq email }
                .count() > 0
        }
    }
    
    override suspend fun updateLastSignIn(accountId: Uuid): Boolean {
        return transaction {
            val updatedRows = AccountsTable.update({ AccountsTable.id eq accountId.toJavaUUID() }) {
                it[lastSignInAt] = Clock.System.now()
            }
            updatedRows > 0
        }
    }
    
    override suspend fun deactivateAccount(accountId: Uuid): Boolean {
        return transaction {
            val updatedRows = AccountsTable.update({ AccountsTable.id eq accountId.toJavaUUID() }) {
                it[isActive] = false
            }
            updatedRows > 0
        }
    }
    
    override suspend fun getAllAccounts(): List<Account> {
        return transaction {
            AccountsTable.selectAll()
                .orderBy(AccountsTable.createdAt, SortOrder.DESC)
                .map { it.toAccount() }
        }
    }
    
    override suspend fun getAccountsCreatedAfter(timestamp: Instant): List<Account> {
        return transaction {
            AccountsTable.selectAll()
                .where { AccountsTable.createdAt greater timestamp }
                .orderBy(AccountsTable.createdAt, SortOrder.DESC)
                .map { it.toAccount() }
        }
    }
    
    override suspend fun deleteAccount(accountId: Uuid): Boolean {
        return transaction {
            val deletedRows = AccountsTable.deleteWhere { id eq accountId.toJavaUUID() }
            deletedRows > 0
        }
    }
    
    private fun ResultRow.toAccount(): Account {
        return Account(
            id = this[AccountsTable.id].toKotlinUuid(),
            username = this[AccountsTable.username],
            displayName = this[AccountsTable.displayName],
            email = this[AccountsTable.email],
            bio = this[AccountsTable.bio],
            createdAt = this[AccountsTable.createdAt],
            lastSignInAt = this[AccountsTable.lastSignInAt],
            isActive = this[AccountsTable.isActive],
            preferences = this[AccountsTable.preferences]
        )
    }
}