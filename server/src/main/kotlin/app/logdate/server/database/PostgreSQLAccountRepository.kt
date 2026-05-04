package app.logdate.server.database

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLAccountRepository : AccountRepository {
    override suspend fun save(account: Account): Account =
        transaction {
            val existingAccount =
                AccountsTable
                    .selectAll()
                    .where { AccountsTable.id eq account.id.toJavaUUID() }
                    .singleOrNull()

            if (existingAccount != null) {
                // Update existing account
                AccountsTable.update({ AccountsTable.id eq account.id.toJavaUUID() }) {
                    it[username] = account.username
                    it[displayName] = account.displayName
                    it[did] = account.did
                    it[handle] = account.handle
                    it[signingKeyPublic] = account.signingKeyPublic
                    it[plcRecoveryDidKey] = account.plcRecoveryDidKey
                    it[email] = account.email
                    it[emailVerified] = account.emailVerified
                    it[bio] = account.bio
                    it[lastSignInAt] = account.lastSignInAt
                    it[isActive] = account.isActive
                    it[preferences] = account.preferences ?: "{}"
                }
                account
            } else {
                // Insert new account
                AccountsTable.insert {
                    it[id] = account.id.toJavaUUID()
                    it[username] = account.username
                    it[displayName] = account.displayName
                    it[did] = account.did
                    it[handle] = account.handle
                    it[signingKeyPublic] = account.signingKeyPublic
                    it[plcRecoveryDidKey] = account.plcRecoveryDidKey
                    it[email] = account.email
                    it[emailVerified] = account.emailVerified
                    it[bio] = account.bio
                    it[createdAt] = account.createdAt
                    it[lastSignInAt] = account.lastSignInAt
                    it[isActive] = account.isActive
                    it[preferences] = account.preferences ?: "{}"
                }
                account
            }
        }

    override suspend fun findById(id: Uuid): Account? =
        transaction {
            AccountsTable
                .selectAll()
                .where { AccountsTable.id eq id.toJavaUUID() }
                .singleOrNull()
                ?.toAccount()
        }

    override suspend fun findByDid(did: String): Account? =
        transaction {
            AccountsTable
                .selectAll()
                .where { AccountsTable.did eq did }
                .singleOrNull()
                ?.toAccount()
        }

    override suspend fun findByHandle(handle: String): Account? =
        transaction {
            AccountsTable
                .selectAll()
                .where { AccountsTable.handle eq handle }
                .singleOrNull()
                ?.toAccount()
        }

    override suspend fun findByUsername(username: String): Account? =
        transaction {
            AccountsTable
                .selectAll()
                .where { AccountsTable.username eq username }
                .singleOrNull()
                ?.toAccount()
        }

    override suspend fun findByEmail(email: String): Account? =
        transaction {
            AccountsTable
                .selectAll()
                .where { AccountsTable.email eq email }
                .singleOrNull()
                ?.toAccount()
        }

    override suspend fun findByVerifiedEmail(email: String): List<Account> =
        transaction {
            AccountsTable
                .selectAll()
                .where {
                    (AccountsTable.email eq email) and
                        (AccountsTable.emailVerified eq true)
                }.map { it.toAccount() }
        }

    override suspend fun usernameExists(username: String): Boolean =
        transaction {
            AccountsTable
                .selectAll()
                .where { AccountsTable.username eq username }
                .count() > 0
        }

    override suspend fun emailExists(email: String): Boolean =
        transaction {
            AccountsTable
                .selectAll()
                .where { AccountsTable.email eq email }
                .count() > 0
        }

    override suspend fun updateLastSignIn(accountId: Uuid): Boolean =
        transaction {
            val updatedRows =
                AccountsTable.update({ AccountsTable.id eq accountId.toJavaUUID() }) {
                    it[lastSignInAt] = Clock.System.now()
                }
            updatedRows > 0
        }

    override suspend fun deactivateAccount(accountId: Uuid): Boolean =
        transaction {
            val updatedRows =
                AccountsTable.update({ AccountsTable.id eq accountId.toJavaUUID() }) {
                    it[isActive] = false
                }
            updatedRows > 0
        }

    override suspend fun getAllAccounts(): List<Account> =
        transaction {
            AccountsTable
                .selectAll()
                .where { AccountsTable.isActive eq true }
                .orderBy(AccountsTable.createdAt, SortOrder.DESC)
                .map { it.toAccount() }
        }

    override suspend fun getAccountsCreatedAfter(timestamp: Instant): List<Account> =
        transaction {
            AccountsTable
                .selectAll()
                .where {
                    (AccountsTable.createdAt greater timestamp) and
                        (AccountsTable.isActive eq true)
                }.orderBy(AccountsTable.createdAt, SortOrder.DESC)
                .map { it.toAccount() }
        }

    override suspend fun deleteAccount(accountId: Uuid): Boolean =
        transaction {
            val deletedRows = AccountsTable.deleteWhere { id eq accountId.toJavaUUID() }
            deletedRows > 0
        }

    private fun ResultRow.toAccount(): Account =
        Account(
            id = this[AccountsTable.id].toKotlinUuid(),
            username = this[AccountsTable.username],
            displayName = this[AccountsTable.displayName],
            did = this[AccountsTable.did],
            handle = this[AccountsTable.handle],
            signingKeyPublic = this[AccountsTable.signingKeyPublic],
            plcRecoveryDidKey = this[AccountsTable.plcRecoveryDidKey],
            email = this[AccountsTable.email],
            emailVerified = this[AccountsTable.emailVerified],
            bio = this[AccountsTable.bio],
            createdAt = this[AccountsTable.createdAt],
            lastSignInAt = this[AccountsTable.lastSignInAt],
            isActive = this[AccountsTable.isActive],
            preferences = this[AccountsTable.preferences],
        )
}
