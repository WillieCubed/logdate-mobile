package app.logdate.server.auth

import app.logdate.shared.model.AccountStorageMode
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface AccountRepository {
    /** Atomically creates an account without ever replacing an existing owner. */
    suspend fun create(account: Account): Boolean

    /**
     * Inserts [account], or updates an existing one. An update never changes the stored storage mode,
     * whatever [Account.storageMode] the caller holds: only [updateStorageModeIfUnchanged] does, so a
     * save of an account read before its mode changed can't undo the change, and a mode a newer
     * server wrote is never overwritten.
     */
    suspend fun save(account: Account): Account

    /**
     * Moves an account from [expected] to [new] only if its stored mode is still [expected], and
     * returns whether it did. A stored mode this server doesn't recognize never matches, and [new]
     * must be a mode this server knows.
     */
    suspend fun updateStorageModeIfUnchanged(
        accountId: Uuid,
        expected: AccountStorageMode,
        new: AccountStorageMode,
    ): Boolean

    /**
     * Sets or clears [moderation] for [accountId] and returns whether the account exists. Nothing else
     * changes it: [save] keeps the stored value.
     */
    suspend fun setModeration(
        accountId: Uuid,
        moderation: AccountModeration?,
    ): Boolean

    /**
     * Persists a new account only when its ID has not already been claimed.
     *
     * Unlike [save], this operation must never update an existing account. Signup paths that
     * accept a client-selected canonical owner ID use it to prevent an ID collision from
     * overwriting another account.
     */
    suspend fun createOnly(account: Account): Boolean

    suspend fun findById(id: Uuid): Account?

    suspend fun findByUsername(username: String): Account?

    suspend fun findByHandle(handle: String): Account?

    suspend fun findByDid(did: String): Account?

    suspend fun findByEmail(email: String): Account?

    suspend fun findByVerifiedEmail(email: String): List<Account>

    suspend fun usernameExists(username: String): Boolean

    suspend fun emailExists(email: String): Boolean

    suspend fun updateLastSignIn(accountId: Uuid): Boolean

    suspend fun deactivateAccount(accountId: Uuid): Boolean

    suspend fun getAllAccounts(): List<Account>

    /**
     * Active accounts that still lack an AT Protocol handle, DID, or signing key. Only the operator
     * identity backfill reads this; request handling never scans accounts.
     */
    suspend fun findAccountsMissingIdentity(): List<Account>

    suspend fun getAccountsCreatedAfter(timestamp: Instant): List<Account>

    suspend fun deleteAccount(accountId: Uuid): Boolean

    /**
     * Writes [updated]'s AT Protocol identity (handle, DID, signing key and PLC recovery key) only
     * while the stored account still has [expected]'s identity. Returns the stored account after the
     * write, or null when the account no longer exists or another writer changed its identity
     * first. It never inserts, so it cannot bring back a deleted account, and it leaves every other
     * column alone.
     *
     * This default reads and then saves. The PostgreSQL repository overrides it with one
     * conditional `UPDATE`, which is what keeps two server instances from both assigning a DID.
     */
    suspend fun updateIdentityIfUnchanged(
        expected: Account,
        updated: Account,
    ): Account? {
        val current = findById(expected.id) ?: return null
        if (!current.hasSameIdentityAs(expected)) return null
        return save(current.withIdentityOf(updated))
    }
}

/** True when both accounts store the same handle, DID, signing key and PLC recovery key. */
fun Account.hasSameIdentityAs(other: Account): Boolean =
    handle == other.handle &&
        did == other.did &&
        signingKeyPublic == other.signingKeyPublic &&
        plcRecoveryDidKey == other.plcRecoveryDidKey

/** This account with [source]'s handle, DID, signing key and PLC recovery key. */
@OptIn(ExperimentalUuidApi::class)
fun Account.withIdentityOf(source: Account): Account =
    copy(
        handle = source.handle,
        did = source.did,
        signingKeyPublic = source.signingKeyPublic,
        plcRecoveryDidKey = source.plcRecoveryDidKey,
    )

@OptIn(ExperimentalUuidApi::class)
class InMemoryAccountRepository : AccountRepository {
    // In-memory storage for demo purposes
    // In production, this would be backed by a database
    private val accounts = mutableMapOf<Uuid, Account>()
    private val usernameIndex = mutableMapOf<String, Uuid>() // username -> account ID
    private val emailIndex = mutableMapOf<String, Uuid>() // email -> account ID
    private val didIndex = mutableMapOf<String, Uuid>() // did -> account ID
    private val handleIndex = mutableMapOf<String, Uuid>() // handle -> account ID

    override suspend fun create(account: Account): Boolean =
        synchronized(accounts) {
            if (accounts.containsKey(account.id) || usernameIndex.containsKey(account.username)) {
                false
            } else {
                accounts[account.id] = account
                usernameIndex[account.username] = account.id
                account.email?.let { emailIndex[it] = account.id }
                account.did?.let { didIndex[it] = account.id }
                account.handle?.let { handleIndex[it] = account.id }
                true
            }
        }

    override suspend fun save(account: Account): Account {
        val existingAccount = accounts[account.id]
        val stored = existingAccount?.let { account.copy(storageMode = it.storageMode, moderation = it.moderation) } ?: account
        if (existingAccount != null && existingAccount.username != account.username) {
            usernameIndex.remove(existingAccount.username)
        }
        if (existingAccount != null && existingAccount.email != account.email) {
            existingAccount.email?.let { emailIndex.remove(it) }
        }
        if (existingAccount != null && existingAccount.did != account.did) {
            existingAccount.did?.let { didIndex.remove(it) }
        }
        if (existingAccount != null && existingAccount.handle != account.handle) {
            existingAccount.handle?.let { handleIndex.remove(it) }
        }

        accounts[account.id] = stored
        usernameIndex[account.username] = account.id
        account.email?.let { emailIndex[it] = account.id }
        account.did?.let { didIndex[it] = account.id }
        account.handle?.let { handleIndex[it] = account.id }
        return stored
    }

    override suspend fun updateStorageModeIfUnchanged(
        accountId: Uuid,
        expected: AccountStorageMode,
        new: AccountStorageMode,
    ): Boolean {
        require(new != AccountStorageMode.UNRECOGNIZED) { "An account can't be moved to a storage mode this server doesn't know" }
        if (expected == AccountStorageMode.UNRECOGNIZED) return false
        return synchronized(accounts) {
            val account = accounts[accountId]?.takeIf { it.storageMode == expected } ?: return@synchronized false
            accounts[accountId] = account.copy(storageMode = new)
            true
        }
    }

    override suspend fun setModeration(
        accountId: Uuid,
        moderation: AccountModeration?,
    ): Boolean =
        synchronized(accounts) {
            val account = accounts[accountId] ?: return@synchronized false
            accounts[accountId] = account.copy(moderation = moderation)
            true
        }

    override suspend fun createOnly(account: Account): Boolean =
        synchronized(accounts) {
            if (accounts.containsKey(account.id)) {
                false
            } else {
                accounts[account.id] = account
                usernameIndex[account.username] = account.id
                account.email?.let { emailIndex[it] = account.id }
                account.did?.let { didIndex[it] = account.id }
                account.handle?.let { handleIndex[it] = account.id }
                true
            }
        }

    override suspend fun findById(id: Uuid): Account? = accounts[id]

    override suspend fun findByDid(did: String): Account? {
        val accountId = didIndex[did] ?: return null
        return accounts[accountId]
    }

    override suspend fun findByHandle(handle: String): Account? {
        val accountId = handleIndex[handle] ?: return null
        return accounts[accountId]
    }

    override suspend fun findByUsername(username: String): Account? {
        val accountId = usernameIndex[username] ?: return null
        return accounts[accountId]
    }

    override suspend fun findByEmail(email: String): Account? {
        val accountId = emailIndex[email] ?: return null
        return accounts[accountId]
    }

    override suspend fun findByVerifiedEmail(email: String): List<Account> =
        accounts.values.filter { it.email?.equals(email, ignoreCase = true) == true && it.emailVerified && it.isActive }

    override suspend fun usernameExists(username: String): Boolean = usernameIndex.containsKey(username)

    override suspend fun emailExists(email: String): Boolean = emailIndex.containsKey(email)

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

    override suspend fun getAllAccounts(): List<Account> = accounts.values.filter { it.isActive }

    override suspend fun findAccountsMissingIdentity(): List<Account> =
        accounts.values.filter { it.isActive && (it.handle == null || it.did == null || it.signingKeyPublic == null) }

    override suspend fun getAccountsCreatedAfter(timestamp: Instant): List<Account> =
        accounts.values.filter {
            it.createdAt > timestamp && it.isActive
        }

    override suspend fun deleteAccount(accountId: Uuid): Boolean {
        val account = accounts.remove(accountId)
        account?.let {
            usernameIndex.remove(it.username)
            it.email?.let { email -> emailIndex.remove(email) }
            it.did?.let { did -> didIndex.remove(did) }
            it.handle?.let { handle -> handleIndex.remove(handle) }
        }
        return account != null
    }
}
