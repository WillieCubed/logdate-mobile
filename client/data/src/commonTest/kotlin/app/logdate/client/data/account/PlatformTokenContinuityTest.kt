package app.logdate.client.data.account

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.GuardedPlatformAccountManager
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

class PlatformTokenContinuityTest {
    @Test
    fun `delayed platform write cannot overwrite a newer refreshed access token`() = verifyDelayedWrite(false)

    @Test
    fun `delayed platform write cannot overwrite a newer identical-token explicit login`() = verifyDelayedWrite(true)

    private fun verifyDelayedWrite(explicitLogin: Boolean) =
        runTest {
            val source = DefaultPasskeyAccountRepositoryTest()
            val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
            val vault = SecureSessionStorage(RenewalStorage(), config, backgroundScope)
            val account =
                LogDateAccount(
                    id = Uuid.random(),
                    username = "owner",
                    displayName = "Owner",
                    bio = "",
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                )
            val initial = UserSession("initial-access", "refresh", account.id.toString())
            vault.saveExplicitSession(initial)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var writes = 0
            val platform =
                object :
                    GuardedPlatformAccountManager,
                    PlatformAccountManager by DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager() {
                    override suspend fun updateTokensIfCurrent(
                        username: String,
                        backendUrl: String,
                        accessToken: String,
                        refreshToken: String,
                        isCurrent: suspend () -> Boolean,
                    ): Result<Unit> {
                        entered.complete(Unit)
                        release.await()
                        if (isCurrent()) writes++
                        // Exercise the missing-account fallback too.
                        return Result.failure(IllegalStateException("No platform account"))
                    }

                    override suspend fun addAccountIfCurrent(
                        account: LogDateAccount,
                        accessToken: String,
                        refreshToken: String,
                        backendUrl: String,
                        isCurrent: suspend () -> Boolean,
                    ): Result<Unit> {
                        if (isCurrent()) writes++
                        return Result.success(Unit)
                    }

                    override suspend fun removeAccountIfSignedOut(
                        username: String,
                        backendUrl: String,
                        isSignedOut: suspend () -> Boolean,
                    ): Result<Unit> = Result.success(Unit)
                }
            val state = PasskeyAccountSessionState().apply { markAuthenticated(account, config.getCurrentBackendUrl()) }
            val coordinator =
                AccountSessionRefreshCoordinator(
                    source.FakePasskeyApiClient(),
                    vault,
                    platform,
                    config,
                    state,
                    { Result.success(Unit) },
                )
            val refreshing = async { coordinator.refreshAuthentication() }
            entered.await()
            val refreshed = requireNotNull(vault.getSession())
            if (explicitLogin) {
                vault.saveExplicitSession(refreshed)
            } else {
                vault.replaceSessionIfCurrent(requireNotNull(vault.getOriginBoundSession()), refreshed.copy(accessToken = "newest-access"))
            }
            release.complete(Unit)
            refreshing.await()
            assertEquals(0, writes, "A superseded refresh must not update or recreate the Android account")
        }
}
