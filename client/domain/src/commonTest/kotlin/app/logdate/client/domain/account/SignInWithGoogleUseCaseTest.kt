package app.logdate.client.domain.account

import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests [SignInWithGoogleUseCase], focusing on mapping repository outcomes to semantic
 * [GoogleAuthError] categories (the UI resolves these to localized strings).
 */
@OptIn(ExperimentalUuidApi::class)
class SignInWithGoogleUseCaseTest {
    private val account =
        LogDateAccount(
            id = Uuid.random(),
            username = "googleuser",
            displayName = "Google User",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )

    /** Minimal repository whose only meaningful behavior is the configurable Google sign-in result. */
    private class FakeRepository(
        private val googleResult: Result<LogDateAccount>,
    ) : PasskeyAccountRepository {
        override val currentAccount = MutableStateFlow<LogDateAccount?>(null)
        override val isAuthenticated = MutableStateFlow(false)

        override suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> =
            Result.failure(NotImplementedError())

        override suspend fun authenticateWithPasskey(username: String?): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = Result.failure(NotImplementedError())

        override suspend fun signOut(): Result<Unit> = Result.failure(NotImplementedError())

        override suspend fun getCurrentAccount(): LogDateAccount? = null

        override suspend fun getAccountInfo(): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun refreshAuthentication(): Result<Unit> = Result.failure(NotImplementedError())

        override suspend fun deletePasskey(credentialId: String): Result<Unit> = Result.failure(NotImplementedError())

        override suspend fun createRestoreKey(): Result<Unit> = Result.failure(NotImplementedError())

        override suspend fun signInWithRestoreKey(): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun deleteRestoreKey(): Result<Unit> = Result.failure(NotImplementedError())

        override suspend fun signInWithGoogle(): Result<LogDateAccount> = googleResult
    }

    @Test
    fun `success returns the account`() =
        runTest {
            val result = SignInWithGoogleUseCase(FakeRepository(Result.success(account)))()
            assertIs<SignInWithGoogleUseCase.Result.Success>(result)
            assertEquals(account, result.account)
        }

    @Test
    fun `server invalid-token code maps to InvalidToken`() =
        runTest {
            val failure = PasskeyApiException(PasskeyApiErrorCodes.GOOGLE_TOKEN_INVALID, "Invalid token")
            val result = SignInWithGoogleUseCase(FakeRepository(Result.failure(failure)))()
            assertIs<SignInWithGoogleUseCase.Result.Error>(result)
            assertEquals(GoogleAuthError.InvalidToken, result.error)
        }

    @Test
    fun `not-configured code maps to NotConfigured`() =
        runTest {
            val failure = PasskeyApiException(PasskeyApiErrorCodes.GOOGLE_AUTH_NOT_CONFIGURED, "Not configured")
            val result = SignInWithGoogleUseCase(FakeRepository(Result.failure(failure)))()
            assertIs<SignInWithGoogleUseCase.Result.Error>(result)
            assertEquals(GoogleAuthError.NotConfigured, result.error)
        }

    @Test
    fun `cancellation message maps to Cancelled`() =
        runTest {
            val result = SignInWithGoogleUseCase(FakeRepository(Result.failure(Exception("Google sign-in was cancelled"))))()
            assertIs<SignInWithGoogleUseCase.Result.Error>(result)
            assertEquals(GoogleAuthError.Cancelled, result.error)
        }
}
