package app.logdate.client.data.account

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.shared.config.PinnedLogDateConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RestoreCredentialRenewalRefreshTest {
    private val origin = "https://cloud.logdate.app"
    private val config = PinnedLogDateConfigRepository(origin, null)
    private val original = UserSession("expired", "refresh", "owner")
    private val sessions = BoundRenewalSessions(OriginBoundSession(origin, original))
    private val delegate = DefaultPasskeyAccountRepositoryTest().FakePasskeyApiClient()

    @Test
    fun `expired access token refreshes in the captured vault`() =
        runTest {
            val api =
                object : PasskeyApiClientContract by delegate {
                    override suspend fun refreshToken(refreshToken: String): Result<String> {
                        assertEquals("refresh", refreshToken)
                        return Result.success("fresh")
                    }
                }
            val renewal = RestoreCredentialRenewal(RenewalStorage()) { api }
            renewal.refreshSession(config, original, sessions) { true }
            assertEquals(original.copy(accessToken = "fresh"), sessions.getSession())
        }

    @Test
    fun `server changes during refresh never receive the previous servers tokens`() =
        runTest {
            val next = OriginBoundSession("https://other.example", original.copy(accessToken = "other"))
            val api =
                object : PasskeyApiClientContract by delegate {
                    override suspend fun refreshToken(refreshToken: String): Result<String> {
                        sessions.bound = next
                        return Result.success("fresh")
                    }
                }
            val renewal = RestoreCredentialRenewal(RenewalStorage()) { api }
            renewal.refreshSession(config, original, sessions) { true }
            assertEquals(next, sessions.getOriginBoundSession())
        }

    @Test
    fun `a new login during refresh cannot be replaced`() =
        runTest {
            val next = OriginBoundSession(origin, original.copy(accessToken = "new login"))
            val api =
                object : PasskeyApiClientContract by delegate {
                    override suspend fun refreshToken(refreshToken: String): Result<String> {
                        sessions.bound = next
                        return Result.success("fresh")
                    }
                }
            val renewal = RestoreCredentialRenewal(RenewalStorage()) { api }
            renewal.refreshSession(config, original, sessions) { true }
            assertEquals(next, sessions.getOriginBoundSession())
        }
}

private class BoundRenewalSessions(
    var bound: OriginBoundSession,
) : SessionStorage {
    override fun getOriginBoundSession() = bound

    override fun getSession() = bound.session

    override fun getSessionFlow() = MutableStateFlow(bound.session)

    override suspend fun hasValidSession() = true

    override suspend fun saveSession(session: UserSession) = error("Unbound write forbidden")

    override suspend fun clearSession() = error("Unexpected clear")

    override suspend fun replaceSessionIfCurrent(
        expected: OriginBoundSession,
        updated: UserSession,
    ): Boolean {
        if (bound != expected) return false
        bound = expected.copy(session = updated)
        return true
    }
}
