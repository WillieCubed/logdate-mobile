package app.logdate.client.sync.v2.link

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.networking.DeviceLinkApiClientContract
import app.logdate.client.networking.DeviceLinkApiException
import app.logdate.client.sync.cloud.RefreshedSession
import app.logdate.client.sync.cloud.SessionRefreshingAccountRepository
import app.logdate.client.sync.v2.FakeSessions
import app.logdate.shared.model.devices.DeviceLinkCodeResponse
import app.logdate.shared.model.devices.DeviceLinkRedeemResponse
import app.logdate.shared.model.devices.LinkedDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Tests [LinkCodeMinter]: a code is minted with the phone's own session, refreshed once when it expired. */
class LinkCodeMinterTest {
    private val sessions = FakeSessions(OriginBoundSession(ORIGIN, UserSession("access", "refresh", ACCOUNT)))
    private val links = FakeLinks()
    private val refresher = FakeRefresher()

    @Test
    fun `a signed-in phone mints a code with its access token`() =
        runTest {
            links.results += Result.success(CODE)

            assertEquals(CODE, LinkCodeMinter(links, sessions, refresher).mint().getOrThrow())
            assertEquals(listOf("access"), links.tokens)
        }

    @Test
    fun `an expired access token is refreshed once and the code minted`() =
        runTest {
            links.results += Result.failure(DeviceLinkApiException(401))
            links.results += Result.success(CODE)
            refresher.result = Result.success(RefreshedSession("new-access", "new-refresh"))

            assertEquals(CODE, LinkCodeMinter(links, sessions, refresher).mint().getOrThrow())
            assertEquals(listOf("access", "new-access"), links.tokens)
            assertEquals(UserSession("new-access", "new-refresh", ACCOUNT), sessions.replaced)
        }

    @Test
    fun `a phone that is not signed in mints nothing`() =
        runTest {
            sessions.bound = null

            assertTrue(LinkCodeMinter(links, sessions, refresher).mint().isFailure)
            assertTrue(links.tokens.isEmpty())
        }

    @Test
    fun `a refused refresh is the failure`() =
        runTest {
            links.results += Result.failure(DeviceLinkApiException(401))
            refresher.result = Result.failure(IllegalStateException("revoked"))

            assertTrue(LinkCodeMinter(links, sessions, refresher).mint().isFailure)
            assertEquals(listOf("access"), links.tokens)
        }

    @Test
    fun `a code minted while the phone signs out is discarded`() =
        runTest {
            links.results += Result.success(CODE)
            links.onMint = { sessions.bound = null }

            assertTrue(LinkCodeMinter(links, sessions, refresher).mint().isFailure)
        }

    @Test
    fun `a failed request carrying cancellation remains cancellation`() =
        runTest {
            links.results += Result.failure(CancellationException("cancel"))

            assertFailsWith<CancellationException> { LinkCodeMinter(links, sessions, refresher).mint() }
        }

    @Test
    fun `a refused request after sign out never refreshes the old account`() =
        runTest {
            links.results += Result.failure(DeviceLinkApiException(401))
            links.onMint = { sessions.bound = null }

            assertTrue(LinkCodeMinter(links, sessions, refresher).mint().isFailure)
            assertTrue(refresher.tokens.isEmpty())
        }

    @Test
    fun `the endpoint is captured once for the original session even across refresh`() =
        runTest {
            val captured = mutableListOf<OriginBoundSession>()
            links.results += Result.failure(DeviceLinkApiException(401))
            links.results += Result.success(CODE)
            refresher.result = Result.success(RefreshedSession("new-access", "new-refresh"))
            val original = sessions.bound!!
            val minter =
                LinkCodeMinter(sessions) { bound ->
                    captured += bound
                    LinkCodeMinter.Clients(links, refresher)
                }

            assertEquals(CODE, minter.mint().getOrThrow())
            assertEquals(listOf(original), captured)
        }

    @Test
    fun `changing session while capturing the endpoint sends no credentials`() =
        runTest {
            links.results += Result.success(CODE)
            val minter =
                LinkCodeMinter(sessions) {
                    sessions.bound = null
                    LinkCodeMinter.Clients(links, refresher)
                }

            assertTrue(minter.mint().isFailure)
            assertTrue(links.tokens.isEmpty())
        }

    private class FakeLinks : DeviceLinkApiClientContract {
        val tokens = mutableListOf<String>()
        var onMint: () -> Unit = {}
        val results = ArrayDeque<Result<DeviceLinkCodeResponse>>()

        override suspend fun mintLinkCode(accessToken: String): Result<DeviceLinkCodeResponse> {
            tokens += accessToken
            onMint()
            return results.removeFirst()
        }

        override suspend fun redeem(
            code: String,
            deviceName: String,
            platform: String,
        ): Result<DeviceLinkRedeemResponse> = error("The phone never redeems")

        override suspend fun listDevices(accessToken: String): Result<List<LinkedDevice>> = error("Not used")

        override suspend fun revoke(
            deviceId: String,
            accessToken: String,
        ): Result<Unit> = error("Not used")

        override suspend fun unlinkSelf(accessToken: String): Result<Unit> = error("Not used")
    }

    private class FakeRefresher : SessionRefreshingAccountRepository {
        val tokens = mutableListOf<String>()
        var result: Result<RefreshedSession> = Result.failure(IllegalStateException("No refresh expected"))

        override suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> {
            tokens += refreshToken
            return result
        }
    }

    private companion object {
        const val ORIGIN = "https://logdate.test"
        const val ACCOUNT = "5f0c3a52-0000-4000-8000-000000000001"
        val CODE = DeviceLinkCodeResponse(code = "code-1", expiresAt = 9_000)
    }
}
