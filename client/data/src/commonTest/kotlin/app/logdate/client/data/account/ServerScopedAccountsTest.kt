package app.logdate.client.data.account

import app.logdate.client.datastore.OriginSessionVault
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerDescriptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerScopedAccountsTest {
    private val ownerId = "5f0c2a8e-8a3a-4a3d-9f5e-2b3c4d5e6f70"
    private val serverA = "https://cloud.logdate.app"
    private val serverB = "https://journal.example.com"
    private val sessionA = UserSession(accessToken = "access-a", refreshToken = "refresh-a", accountId = ownerId)
    private val sessionB = UserSession(accessToken = "access-b", refreshToken = "refresh-b", accountId = ownerId)

    @Test
    fun `a scoped account talks only to its own server with its own sign-in`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val vault =
                MapVault().apply {
                    sessions[serverA] = sessionA
                    sessions[serverB] = sessionB
                }
            val account = accounts(vault, requests).open(serverB, descriptorFor(serverB))

            account.repository.listPasskeys().getOrThrow()
            account.close()

            val request = requests.single()
            assertEquals("journal.example.com", request.url.host)
            assertEquals("Bearer access-b", request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun `signing out of a scoped account clears only its server's sign-in`() =
        runTest {
            val vault =
                MapVault().apply {
                    sessions[serverA] = sessionA
                    sessions[serverB] = sessionB
                }
            val account = accounts(vault, mutableListOf()).open(serverB, descriptorFor(serverB))

            account.repository.signOut().getOrThrow()
            repeat(3) { yield() }
            account.close()

            assertNull(account.session())
            assertEquals(sessionA, vault.sessions[serverA])
        }

    private fun accounts(
        vault: OriginSessionVault,
        requests: MutableList<HttpRequestData>,
    ) = DefaultServerScopedAccounts(
        httpClient =
            HttpClient(
                MockEngine { request ->
                    requests += request
                    respond(
                        content = """{"success":true,"data":[]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ),
        vault = vault,
        passkeyManager = DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
        platformAccountManager = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
        canonicalOwnerProvider = BoundOwner(ownerId),
        hasLocalData = { false },
        deviceName = { null },
    )

    private fun descriptorFor(origin: String) =
        ServerDescriptor(
            serverOrigin = origin,
            apiBaseUrl = "$origin/api/v1",
            deploymentKind = DeploymentKind.SELF_HOSTED,
            displayName = "Server",
        )

    private class MapVault : OriginSessionVault {
        val sessions = mutableMapOf<String, UserSession>()

        override suspend fun read(origin: String): UserSession? = sessions[origin]

        override suspend fun write(
            origin: String,
            session: UserSession,
        ) {
            sessions[origin] = session
        }

        override suspend fun clear(origin: String) {
            sessions.remove(origin)
        }
    }

    private class BoundOwner(
        private val ownerId: String,
    ) : CanonicalOwnerProvider {
        override suspend fun getCanonicalOwnerId(): String = ownerId

        override suspend fun hasBoundOwner(): Boolean = true
    }
}
