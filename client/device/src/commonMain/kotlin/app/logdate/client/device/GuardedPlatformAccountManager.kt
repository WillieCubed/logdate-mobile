package app.logdate.client.device

import app.logdate.shared.model.LogDateAccount

/** Validates session ownership inside the platform's shared mutation boundary. */
interface GuardedPlatformAccountManager : PlatformAccountManager {
    suspend fun addAccountIfCurrent(
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
        backendUrl: String,
        isCurrent: suspend () -> Boolean,
    ): Result<Unit>

    suspend fun updateTokensIfCurrent(
        username: String,
        backendUrl: String,
        accessToken: String,
        refreshToken: String,
        isCurrent: suspend () -> Boolean,
    ): Result<Unit>

    suspend fun removeAccountIfSignedOut(
        username: String,
        backendUrl: String,
        isSignedOut: suspend () -> Boolean,
    ): Result<Unit>
}

suspend fun PlatformAccountManager.addAccountIfCurrent(
    account: LogDateAccount,
    accessToken: String,
    refreshToken: String,
    backendUrl: String,
    isCurrent: suspend () -> Boolean,
): Result<Unit> =
    when {
        this is GuardedPlatformAccountManager -> addAccountIfCurrent(account, accessToken, refreshToken, backendUrl, isCurrent)
        isCurrent() -> addAccount(account, accessToken, refreshToken, backendUrl)
        else -> Result.failure(PlatformAccountException("Authentication changed"))
    }

suspend fun PlatformAccountManager.updateTokensIfCurrent(
    username: String,
    backendUrl: String,
    accessToken: String,
    refreshToken: String,
    isCurrent: suspend () -> Boolean,
): Result<Unit> =
    when {
        this is GuardedPlatformAccountManager -> updateTokensIfCurrent(username, backendUrl, accessToken, refreshToken, isCurrent)
        isCurrent() -> updateTokens(username, backendUrl, accessToken, refreshToken)
        else -> Result.failure(PlatformAccountException("Authentication changed"))
    }

suspend fun PlatformAccountManager.removeAccountIfSignedOut(
    username: String,
    backendUrl: String,
    isSignedOut: suspend () -> Boolean,
): Result<Unit> =
    when {
        this is GuardedPlatformAccountManager -> removeAccountIfSignedOut(username, backendUrl, isSignedOut)
        isSignedOut() -> removeAccount(username, backendUrl)
        else -> Result.success(Unit)
    }
