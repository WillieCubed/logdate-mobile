package app.logdate.server.auth

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface AccountRepository {
    suspend fun save(account: Account): Account
    suspend fun findById(id: Uuid): Account?
    suspend fun findByUsername(username: String): Account?
    suspend fun findByEmail(email: String): Account?
    suspend fun usernameExists(username: String): Boolean
    suspend fun emailExists(email: String): Boolean
    suspend fun updateLastSignIn(accountId: Uuid): Boolean
    suspend fun deactivateAccount(accountId: Uuid): Boolean
    suspend fun getAllAccounts(): List<Account>
    suspend fun getAccountsCreatedAfter(timestamp: Instant): List<Account>
    suspend fun deleteAccount(accountId: Uuid): Boolean
}

@OptIn(ExperimentalUuidApi::class)
class InMemoryAccountRepository : AccountRepository {
    // In-memory storage for demo purposes
    // In production, this would be backed by a database
    private val accounts = mutableMapOf<Uuid, Account>()
    private val usernameIndex = mutableMapOf<String, Uuid>() // username -> account ID
    private val emailIndex = mutableMapOf<String, Uuid>() // email -> account ID
    
    override suspend fun save(account: Account): Account {
        accounts[account.id] = account
        usernameIndex[account.username] = account.id
        account.email?.let { emailIndex[it] = account.id }
        return account
    }
    
    override suspend fun findById(id: Uuid): Account? {
        return accounts[id]
    }
    
    override suspend fun findByUsername(username: String): Account? {
        val accountId = usernameIndex[username] ?: return null
        return accounts[accountId]
    }
    
    override suspend fun findByEmail(email: String): Account? {
        val accountId = emailIndex[email] ?: return null
        return accounts[accountId]
    }
    
    override suspend fun usernameExists(username: String): Boolean {
        return usernameIndex.containsKey(username)
    }
    
    override suspend fun emailExists(email: String): Boolean {
        return emailIndex.containsKey(email)
    }
    
    override suspend fun updateLastSignIn(accountId: Uuid): Boolean {
        val account = accounts[accountId] ?: return false
        val updatedAccount = account.copy(lastSignInAt = Clock.System.now())
        accounts[accountId] = updatedAccount
        return true
    }
    
    override suspend fun deactivateAccount(accountId: Uuid): Boolean {
        val account = accounts[accountId] ?: return false
        accounts[accountId] = account.copy(isActive = false)
        return true
    }
    
    override suspend fun getAllAccounts(): List<Account> {
        return accounts.values.filter { it.isActive }
    }
    
    override suspend fun getAccountsCreatedAfter(timestamp: Instant): List<Account> {
        return accounts.values.filter { it.createdAt > timestamp && it.isActive }
    }
    
    override suspend fun deleteAccount(accountId: Uuid): Boolean {
        val account = accounts.remove(accountId)
        account?.let {
            usernameIndex.remove(it.username)
            it.email?.let { email -> emailIndex.remove(email) }
        }
        return account != null
    }
}