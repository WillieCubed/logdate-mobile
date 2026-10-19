package app.logdate.client.sync.cloud.account

import app.logdate.client.datastore.UserSession
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.FakeSessionStorage
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Sync reads the signed-in account from `/auth/me` before anything else, so the storage mode the
 * transports choose from is the server's current answer. A device never guesses the mode: until the
 * server reports one for this account on this server, it is unknown.
 */
class ServerAccountRefreshTest {
    private val sessions = FakeSessionStorage()
    private val store = ServerAccountStore(InMemoryKeyValueStorage())
    private val modes = ServerAccountStorageMode(sessions, store)

    private class AccountApi(
        var answer: Result<LogDateAccount>,
        var expiredOnce: Boolean = false,
    ) : FakeCloudApiClient() {
        override suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount> {
            methodCalls.add("getAccountInfo")
            if (expiredOnce) {
                expiredOnce = false
                return Result.failure(CloudApiException("EXPIRED", "Access token expired", statusCode = 401))
            }
            return answer
        }
    }

    @Test
    fun `a full sync reads the account first and saves its DID and storage mode`() =
        runTest {
            signIn()
            val api = AccountApi(Result.success(account(ACCOUNT, AccountStorageMode.PERSONAL_SPACE_V1)))
            val sync = testDefaultSyncManager(sessionStorage = sessions, cloudApiClient = api, serverAccounts = store)

            val result = sync.fullSync()

            assertEquals("getAccountInfo", api.methodCalls.firstOrNull(), "calls ${api.methodCalls}, errors ${result.errors}")
            assertTrue(result.errors.none { "account could not be read" in it.message }, "${result.errors}")
            val saved = store.read()
            assertEquals("did:plc:owner", saved?.did)
            assertEquals(sessions.currentOrigin.trimEnd('/'), saved?.origin)
            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, modes.currentMode())
            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, saved?.toCloudAccount()?.storageMode)
        }

    @Test
    fun `an account the server can't describe fails the sync with a visible error`() =
        runTest {
            signIn()
            val api = AccountApi(Result.failure(IllegalStateException("offline")))
            val sync = testDefaultSyncManager(sessionStorage = sessions, cloudApiClient = api, serverAccounts = store)

            val result = sync.fullSync()

            assertFalse(result.success)
            assertTrue(result.errors.any { "account could not be read" in it.message }, "${result.errors}")
            assertNull(store.read())
            assertEquals(AccountStorageMode.UNRECOGNIZED, modes.currentMode())
        }

    @Test
    fun `an expired token is refreshed and the account is still saved`() =
        runTest {
            signIn()
            val api = AccountApi(Result.success(account(ACCOUNT, AccountStorageMode.LEGACY)), expiredOnce = true)
            val sync = testDefaultSyncManager(sessionStorage = sessions, cloudApiClient = api, serverAccounts = store)

            val refreshed = sync.refreshServerAccount()

            assertTrue(refreshed?.isSuccess == true, "$refreshed")
            assertEquals(2, api.methodCalls.count { it == "getAccountInfo" })
            assertEquals(AccountStorageMode.LEGACY, modes.currentMode())
        }

    @Test
    fun `a server that describes a different account is not believed`() =
        runTest {
            signIn()
            val api = AccountApi(Result.success(account("5f0c3a52-0000-4000-8000-000000000009", AccountStorageMode.LEGACY)))
            val sync = testDefaultSyncManager(sessionStorage = sessions, cloudApiClient = api, serverAccounts = store)

            val refreshed = sync.refreshServerAccount()

            assertTrue(refreshed?.isFailure == true)
            assertNull(store.read())
            assertEquals(AccountStorageMode.UNRECOGNIZED, modes.currentMode())
        }

    @Test
    fun `the saved mode belongs to one account on one server`() =
        runTest {
            signIn()
            store.write(ServerReportedAccount.of(sessions.currentOrigin, account(ACCOUNT, AccountStorageMode.LEGACY)))
            assertEquals(AccountStorageMode.LEGACY, modes.currentMode())

            sessions.currentOrigin = "https://other.logdate.test"
            assertEquals(AccountStorageMode.UNRECOGNIZED, modes.currentMode(), "another server's answer is not this one's")

            sessions.clearSession()
            assertNull(modes.currentMode(), "no one is signed in")
        }

    @Test
    fun `a mode the server hasn't reported is unknown and never legacy`() =
        runTest {
            signIn()

            assertEquals(AccountStorageMode.UNRECOGNIZED, modes.currentMode())
        }

    private suspend fun signIn() = sessions.saveSession(UserSession("access", "refresh", ACCOUNT))

    private fun account(
        id: String,
        mode: AccountStorageMode,
    ) = LogDateAccount(
        id = Uuid.parse(id),
        username = "owner",
        displayName = "Owner",
        did = "did:plc:owner",
        handle = "owner.logdate.test",
        createdAt = Instant.fromEpochMilliseconds(1_000),
        updatedAt = Instant.fromEpochMilliseconds(2_000),
        storageMode = mode,
    )

    private companion object {
        const val ACCOUNT = "5f0c3a52-0000-4000-8000-000000000001"
    }
}
