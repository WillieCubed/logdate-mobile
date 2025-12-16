package app.logdate.client.device

import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stub implementation of PlatformAccountManager for iOS.
 * 
 * This is a temporary implementation to allow compilation.
 * It should be replaced with a real implementation using KeyChain.
 */
class IosAccountManager : PlatformAccountManager {

    override suspend fun addAccount(
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
        backendUrl: String
    ): Result<Unit> = withContext(Dispatchers.Default) {
        Napier.i("Stub: Added LogDate account to iOS Keychain: ${account.username}")
        Result.success(Unit)
    }

    override suspend fun updateAccount(
        account: LogDateAccount,
        backendUrl: String
    ): Result<Unit> = withContext(Dispatchers.Default) {
        Napier.i("Stub: Updated LogDate account in iOS Keychain: ${account.username}")
        Result.success(Unit)
    }

    override suspend fun updateTokens(
        username: String,
        accessToken: String,
        refreshToken: String
    ): Result<Unit> = withContext(Dispatchers.Default) {
        Napier.d("Stub: Updated tokens for account: $username")
        Result.success(Unit)
    }

    override suspend fun removeAccount(username: String): Result<Unit> = withContext(Dispatchers.Default) {
        Napier.i("Stub: Removed LogDate account from iOS Keychain: $username")
        Result.success(Unit)
    }

    override suspend fun getStoredAccounts(): Result<List<PlatformAccountInfo>> = withContext(Dispatchers.Default) {
        Napier.d("Stub: Retrieved 0 LogDate accounts from iOS Keychain")
        Result.success(emptyList())
    }

    override suspend fun getTokens(username: String): Result<TokenPair?> = withContext(Dispatchers.Default) {
        Napier.d("Stub: Retrieved null tokens for account: $username")
        Result.success(null)
    }

    override suspend fun clearAllTokens(): Result<Unit> = withContext(Dispatchers.Default) {
        Napier.i("Stub: Cleared all tokens from iOS Keychain")
        Result.success(Unit)
    }
}