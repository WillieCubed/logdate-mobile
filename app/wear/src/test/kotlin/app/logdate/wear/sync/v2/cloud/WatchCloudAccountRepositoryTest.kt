package app.logdate.wear.sync.v2.cloud

import app.cash.turbine.test
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.sync.cloud.account.ServerAccountStore
import app.logdate.client.sync.cloud.account.ServerReportedAccount
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.AccountStorageMode
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Tests [WatchCloudAccountRepository]: a linked watch's current account is the one the server last
 * described to it at `/auth/me`, with its DID and storage mode, only while the watch is signed in to
 * that account on that server.
 */
class WatchCloudAccountRepositoryTest {
    private val config = DefaultLogDateConfigRepository(initialBackendUrl = ORIGIN)
    private val accounts = ServerAccountStore(MemoryKeyValueStorage())
    private var bound: OriginBoundSession? = OriginBoundSession(ORIGIN, UserSession("access", "refresh", ACCOUNT))
    private val sessionFlow = MutableStateFlow<UserSession?>(bound?.session)
    private val sessions =
        mockk<SessionStorage> {
            every { getOriginBoundSession() } answers { bound }
            every { getSessionFlow() } returns sessionFlow
        }
    private val repository = WatchCloudAccountRepository(mockk(), config, sessions, accounts)

    @Test
    fun `the account the server described carries its DID and storage mode`() =
        runTest {
            accounts.write(reported)

            val account = repository.getCurrentAccount()

            assertEquals(ACCOUNT, account?.id?.toString())
            assertEquals(ACCOUNT, account?.userId?.toString())
            assertEquals("did:plc:owner", account?.did)
            assertEquals("owner.logdate.test", account?.handle)
            assertEquals("Owner", account?.displayName)
            assertEquals(AccountStorageMode.PERSONAL_SPACE_V1, account?.storageMode)
        }

    @Test
    fun `a watch that hasn't read its account yet has none`() =
        runTest {
            assertNull(repository.getCurrentAccount())
        }

    @Test
    fun `an account for another session or server is not the current one`() =
        runTest {
            accounts.write(reported)

            bound = OriginBoundSession(ORIGIN, UserSession("access", "refresh", "5f0c3a52-0000-4000-8000-000000000009"))
            assertNull(repository.getCurrentAccount(), "signed in to another account")
            bound = null
            assertNull(repository.getCurrentAccount(), "signed out")
            bound = OriginBoundSession(ORIGIN, UserSession("access", "refresh", ACCOUNT))
            config.updateBackendUrl("https://other.test")
            assertNull(repository.getCurrentAccount(), "connected to another server")
        }

    @Test
    fun `the current account follows the saved account and the session`() =
        runTest {
            repository.observeCurrentAccount().test {
                assertNull(awaitItem())
                accounts.write(reported)
                assertEquals("did:plc:owner", awaitItem()?.did)
                bound = null
                sessionFlow.value = null
                assertNull(awaitItem())
            }
        }

    private companion object {
        const val ORIGIN = "https://logdate.test"
        const val ACCOUNT = "5f0c3a52-0000-4000-8000-000000000001"
        val reported =
            ServerReportedAccount(
                origin = ORIGIN,
                accountId = ACCOUNT,
                username = "owner",
                displayName = "Owner",
                did = "did:plc:owner",
                handle = "owner.logdate.test",
                createdAt = Instant.fromEpochMilliseconds(1_000),
                updatedAt = Instant.fromEpochMilliseconds(2_000),
                storageMode = AccountStorageMode.PERSONAL_SPACE_V1,
            )
    }
}
