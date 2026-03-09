package app.logdate.client.device

import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier

/**
 * Desktop implementation for account management
 * Note: Desktop platforms don't have a system account manager like mobile platforms,
 * so this implementation stores minimal information and relies on DataStore for persistence
 */
class DesktopAccountManager : PlatformAccountManager {
    // Desktop doesn't have a system account manager, so we'll keep a minimal in-memory store
    // The actual persistence is handled by DataStore in the repository layer
    private val accountStore = mutableMapOf<String, PlatformAccountInfo>()
    private val tokenStore = mutableMapOf<String, TokenPair>()

    override suspend fun addAccount(
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
        backendUrl: String,
    ): Result<Unit> =
        try {
            accountStore[accountKey(account.username, backendUrl)] =
                PlatformAccountInfo(
                    username = account.username,
                    displayName = account.displayName,
                    userId = account.id.toString(),
                    backendUrl = backendUrl,
                )

            tokenStore[accountKey(account.username, backendUrl)] =
                TokenPair(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                )

            Napier.i("Added LogDate account to desktop account store: ${account.username}")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error adding account to desktop account store", e)
            Result.failure(e)
        }

    override suspend fun updateAccount(
        account: LogDateAccount,
        backendUrl: String,
    ): Result<Unit> {
        return try {
            val existingInfo =
                accountStore[accountKey(account.username, backendUrl)]
                    ?: return Result.failure(Exception("Account not found"))

            accountStore[accountKey(account.username, backendUrl)] =
                existingInfo.copy(
                    displayName = account.displayName,
                    backendUrl = backendUrl,
                )

            Napier.i("Updated LogDate account in desktop account store: ${account.username}")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error updating account in desktop account store", e)
            Result.failure(e)
        }
    }

    override suspend fun updateTokens(
        username: String,
        backendUrl: String,
        accessToken: String,
        refreshToken: String,
    ): Result<Unit> =
        try {
            tokenStore[accountKey(username, backendUrl)] =
                TokenPair(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                )

            Napier.d("Updated tokens for account: $username")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error updating tokens in desktop account store", e)
            Result.failure(e)
        }

    override suspend fun removeAccount(
        username: String,
        backendUrl: String,
    ): Result<Unit> =
        try {
            accountStore.remove(accountKey(username, backendUrl))
            tokenStore.remove(accountKey(username, backendUrl))

            Napier.i("Removed LogDate account from desktop account store: $username")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error removing account from desktop account store", e)
            Result.failure(e)
        }

    override suspend fun getStoredAccounts(): Result<List<PlatformAccountInfo>> =
        try {
            val accounts = accountStore.values.toList()
            Napier.d("Retrieved ${accounts.size} LogDate accounts from desktop account store")
            Result.success(accounts)
        } catch (e: Exception) {
            Napier.e("Error retrieving accounts from desktop account store", e)
            Result.failure(e)
        }

    override suspend fun getTokens(
        username: String,
        backendUrl: String,
    ): Result<TokenPair?> =
        try {
            val tokens = tokenStore[accountKey(username, backendUrl)]
            Result.success(tokens)
        } catch (e: Exception) {
            Napier.e("Error retrieving tokens from desktop account store", e)
            Result.failure(e)
        }

    override suspend fun clearAllTokens(): Result<Unit> =
        try {
            tokenStore.clear()
            Napier.i("Cleared all tokens from desktop account store")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error clearing tokens from desktop account store", e)
            Result.failure(e)
        }

    private fun accountKey(
        username: String,
        backendUrl: String,
    ): String = "$username@$backendUrl"
}
