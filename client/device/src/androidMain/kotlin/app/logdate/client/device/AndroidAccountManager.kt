package app.logdate.client.device

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android implementation for managing LogDate Cloud accounts in the system AccountManager
 */
class AndroidAccountManager(
    context: Context,
    private val accountManager: AccountManager = AccountManager.get(context),
) : GuardedPlatformAccountManager {
    companion object {
        private val mutationMutex = Mutex()
        const val TOKEN_TYPE_ACCESS = "access_token"
        const val TOKEN_TYPE_REFRESH = "refresh_token"

        // UserData keys
        const val KEY_USERNAME = "username"
        const val KEY_USER_ID = "user_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_BIO = "bio"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_CREATED_AT = "created_at"
        const val KEY_UPDATED_AT = "updated_at"
    }

    /**
     * The account type LogDate owns in the platform AccountManager. Derived from the installed
     * package so it cannot drift from the `logdate_account_type` resource the authenticator
     * declares, which the app module generates from the same `applicationId`.
     */
    private val accountType = "${context.packageName}.account"

    override suspend fun addAccount(
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
        backendUrl: String,
    ): Result<Unit> = addAccountIfCurrent(account, accessToken, refreshToken, backendUrl) { true }

    override suspend fun addAccountIfCurrent(
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
        backendUrl: String,
        isCurrent: suspend () -> Boolean,
    ): Result<Unit> =
        serialized {
            if (!isCurrent()) return@serialized Result.failure(PlatformAccountException("Authentication changed"))
            try {
                val backendAwareAccount = Account(accountKey(account.username, backendUrl), accountType)

                // The OS AccountManager is an implementation detail, not an account chooser.
                // Remove stale LogDate entries before recording the sole canonical identity.
                accountManager.getAccountsByType(accountType).forEach { existing ->
                    accountManager.removeAccountExplicitly(existing)
                }

                // Prepare user data
                val userData =
                    Bundle().apply {
                        putString(KEY_USERNAME, account.username)
                        putString(KEY_USER_ID, account.id.toString())
                        putString(KEY_DISPLAY_NAME, account.displayName)
                        putString(KEY_BIO, account.bio)
                        putString(KEY_BACKEND_URL, backendUrl)
                        putString(KEY_CREATED_AT, account.createdAt.toString())
                        putString(KEY_UPDATED_AT, account.updatedAt.toString())
                    }

                // Add account to system
                val success = accountManager.addAccountExplicitly(backendAwareAccount, null, userData)

                if (success) {
                    // Store tokens securely
                    accountManager.setAuthToken(backendAwareAccount, TOKEN_TYPE_ACCESS, accessToken)
                    accountManager.setAuthToken(backendAwareAccount, TOKEN_TYPE_REFRESH, refreshToken)

                    Napier.i("Successfully added LogDate account to Android AccountManager: ${account.username}")
                    Result.success(Unit)
                } else {
                    Napier.w("Failed to add account to Android AccountManager")
                    Result.failure(Exception("Failed to add account to system"))
                }
            } catch (e: Exception) {
                Napier.e("Error adding account to Android AccountManager", e)
                Result.failure(e)
            }
        }

    override suspend fun updateAccount(
        account: LogDateAccount,
        backendUrl: String,
    ): Result<Unit> =
        serialized {
            try {
                val systemAccount =
                    findSystemAccount(account.username, backendUrl)
                        ?: return@serialized Result.failure(Exception("Account not found"))

                // Update user data
                accountManager.setUserData(systemAccount, KEY_DISPLAY_NAME, account.displayName)
                accountManager.setUserData(systemAccount, KEY_BIO, account.bio)
                accountManager.setUserData(systemAccount, KEY_BACKEND_URL, backendUrl)
                accountManager.setUserData(systemAccount, KEY_UPDATED_AT, account.updatedAt.toString())

                Napier.i("Successfully updated LogDate account in Android AccountManager: ${account.username}")
                Result.success(Unit)
            } catch (e: Exception) {
                Napier.e("Error updating account in Android AccountManager", e)
                Result.failure(e)
            }
        }

    override suspend fun updateTokens(
        username: String,
        backendUrl: String,
        accessToken: String,
        refreshToken: String,
    ): Result<Unit> = updateTokensIfCurrent(username, backendUrl, accessToken, refreshToken) { true }

    override suspend fun updateTokensIfCurrent(
        username: String,
        backendUrl: String,
        accessToken: String,
        refreshToken: String,
        isCurrent: suspend () -> Boolean,
    ): Result<Unit> =
        serialized {
            if (!isCurrent()) return@serialized Result.failure(PlatformAccountException("Authentication changed"))
            try {
                val systemAccount =
                    findSystemAccount(username, backendUrl)
                        ?: return@serialized Result.failure(Exception("Account not found"))

                // Update tokens
                accountManager.setAuthToken(systemAccount, TOKEN_TYPE_ACCESS, accessToken)
                accountManager.setAuthToken(systemAccount, TOKEN_TYPE_REFRESH, refreshToken)

                Napier.i("Successfully updated tokens for account: $username")
                Result.success(Unit)
            } catch (e: Exception) {
                Napier.e("Error updating tokens in Android AccountManager", e)
                Result.failure(e)
            }
        }

    override suspend fun removeAccount(
        username: String,
        backendUrl: String,
    ): Result<Unit> =
        serialized {
            try {
                val systemAccount =
                    findSystemAccount(username, backendUrl)
                        ?: return@serialized Result.failure(Exception("Account not found"))

                // Remove account from system
                @Suppress("DEPRECATION")
                val future = accountManager.removeAccount(systemAccount, null, null)
                val result = future.result

                val success = result
                if (success) {
                    Napier.i("Successfully removed LogDate account from Android AccountManager: $username")
                    Result.success(Unit)
                } else {
                    Napier.w("Failed to remove account from Android AccountManager")
                    Result.failure(Exception("Failed to remove account"))
                }
            } catch (e: Exception) {
                Napier.e("Error removing account from Android AccountManager", e)
                Result.failure(e)
            }
        }

    override suspend fun removeAccountIfMatching(
        username: String,
        backendUrl: String,
        refreshToken: String,
    ): Result<Unit> =
        serialized {
            try {
                val account = findSystemAccount(username, backendUrl) ?: return@serialized Result.success(Unit)
                if (accountManager.peekAuthToken(account, TOKEN_TYPE_REFRESH) != refreshToken) return@serialized Result.success(Unit)
                if (accountManager.removeAccountExplicitly(account)) {
                    Result.success(Unit)
                } else {
                    Result.failure(PlatformAccountException("Account removal will retry"))
                }
            } catch (error: Exception) {
                Napier.w("Could not remove the retained Android account", error)
                Result.failure(error)
            }
        }

    override suspend fun removeAccountIfSignedOut(
        username: String,
        backendUrl: String,
        isSignedOut: suspend () -> Boolean,
    ): Result<Unit> =
        serialized {
            if (!isSignedOut()) return@serialized Result.success(Unit)
            try {
                val account = findSystemAccount(username, backendUrl) ?: return@serialized Result.success(Unit)
                if (accountManager.removeAccountExplicitly(account)) {
                    Result.success(Unit)
                } else {
                    Result.failure(PlatformAccountException("Account removal will retry"))
                }
            } catch (error: Exception) {
                Napier.w("Could not remove the signed-out Android account", error)
                Result.failure(error)
            }
        }

    private suspend fun <T> serialized(block: suspend () -> T): T = withContext(Dispatchers.IO) { mutationMutex.withLock { block() } }

    override suspend fun getStoredAccounts(): Result<List<PlatformAccountInfo>> =
        serialized {
            try {
                val accounts = accountManager.getAccountsByType(accountType)

                val accountInfos =
                    accounts.map { account ->
                        PlatformAccountInfo(
                            username = accountManager.getUserData(account, KEY_USERNAME) ?: account.name,
                            displayName = accountManager.getUserData(account, KEY_DISPLAY_NAME) ?: account.name,
                            userId = accountManager.getUserData(account, KEY_USER_ID),
                            backendUrl = accountManager.getUserData(account, KEY_BACKEND_URL),
                        )
                    }

                Napier.i("Retrieved ${accountInfos.size} LogDate accounts from Android AccountManager")
                Result.success(accountInfos)
            } catch (e: Exception) {
                Napier.e("Error retrieving accounts from Android AccountManager", e)
                Result.failure(e)
            }
        }

    override suspend fun getTokens(
        username: String,
        backendUrl: String,
    ): Result<TokenPair?> =
        serialized {
            try {
                val systemAccount =
                    findSystemAccount(username, backendUrl)
                        ?: return@serialized Result.success(null)

                val accessToken = accountManager.peekAuthToken(systemAccount, TOKEN_TYPE_ACCESS)
                val refreshToken = accountManager.peekAuthToken(systemAccount, TOKEN_TYPE_REFRESH)

                if (accessToken != null && refreshToken != null) {
                    Result.success(TokenPair(accessToken, refreshToken))
                } else {
                    Result.success(null)
                }
            } catch (e: Exception) {
                Napier.e("Error retrieving tokens from Android AccountManager", e)
                Result.failure(e)
            }
        }

    override suspend fun clearAllTokens(): Result<Unit> =
        serialized {
            try {
                val accounts = accountManager.getAccountsByType(accountType)

                accounts.forEach { account ->
                    accountManager.invalidateAuthToken(
                        accountType,
                        accountManager.peekAuthToken(account, TOKEN_TYPE_ACCESS),
                    )
                    accountManager.invalidateAuthToken(
                        accountType,
                        accountManager.peekAuthToken(account, TOKEN_TYPE_REFRESH),
                    )
                }

                Napier.i("Successfully cleared all tokens from Android AccountManager")
                Result.success(Unit)
            } catch (e: Exception) {
                Napier.e("Error clearing tokens from Android AccountManager", e)
                Result.failure(e)
            }
        }

    private fun findSystemAccount(
        username: String,
        backendUrl: String,
    ): Account? =
        accountManager
            .getAccountsByType(accountType)
            .find { account ->
                accountManager.getUserData(account, KEY_BACKEND_URL) == backendUrl &&
                    accountManager.getUserData(account, KEY_USERNAME) == username
            }

    private fun accountKey(
        username: String,
        backendUrl: String,
    ): String = "$username@$backendUrl"
}
