package app.logdate.client.data.account

import app.logdate.client.repository.account.AccountRepository
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.Flow

/**
 * iOS [AccountRepository] that exposes the passkey-authenticated session as the
 * AccountRepository surface. Reads come straight from [PasskeyAccountRepository]; profile
 * mutations fail explicitly rather than silently succeeding, since iOS doesn't have a
 * backend account-management endpoint wired yet.
 */
class PasskeyBackedAccountRepository(
    private val passkeyRepository: PasskeyAccountRepository,
) : AccountRepository {
    override val currentAccount: Flow<LogDateAccount?> = passkeyRepository.currentAccount

    override suspend fun updateProfile(
        displayName: String?,
        username: String?,
    ): Result<LogDateAccount> =
        Result.failure(
            UnsupportedOperationException(
                "Profile updates require a backend account-management endpoint that is not yet wired on iOS.",
            ),
        )

    override suspend fun refreshAccount(): Result<LogDateAccount> = passkeyRepository.getAccountInfo()

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> =
        passkeyRepository.checkUsernameAvailability(username)
}
