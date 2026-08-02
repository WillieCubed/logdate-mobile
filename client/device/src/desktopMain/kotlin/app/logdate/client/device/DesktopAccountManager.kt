package app.logdate.client.device

import app.logdate.shared.model.LogDateAccount

/**
 * Desktop has no user-authorized system account vault. The session store is the only supported
 * credential path until a single-identity desktop vault is available. Failing closed prevents an
 * in-memory account index from being mistaken for authoritative identity state.
 */
class DesktopAccountManager : PlatformAccountManager {
    override suspend fun addAccount(
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
        backendUrl: String,
    ): Result<Unit> = unavailable()

    override suspend fun updateAccount(
        account: LogDateAccount,
        backendUrl: String,
    ): Result<Unit> = unavailable()

    override suspend fun updateTokens(
        username: String,
        backendUrl: String,
        accessToken: String,
        refreshToken: String,
    ): Result<Unit> = unavailable()

    override suspend fun removeAccount(
        username: String,
        backendUrl: String,
    ): Result<Unit> = unavailable()

    override suspend fun getStoredAccounts(): Result<List<PlatformAccountInfo>> = unavailable()

    override suspend fun getTokens(
        username: String,
        backendUrl: String,
    ): Result<TokenPair?> = unavailable()

    override suspend fun clearAllTokens(): Result<Unit> = unavailable()

    private fun <T> unavailable(): Result<T> =
        Result.failure(PlatformAccountException("Desktop platform account storage is not authorized"))
}
