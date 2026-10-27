package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.configureAuthV1TestApp
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.HostedAccountDidMethod
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PrivateIdentityDiscoveryTest {
    @Test
    fun `consent for another DID and inactive accounts do not permit discovery`() =
        testApplication {
            val env = configureAuthV1TestApp()
            val accounts =
                listOf(
                    Account(
                        id = Uuid.random(),
                        username = "adopted",
                        displayName = "Adopted",
                        createdAt = Clock.System.now(),
                        did = "did:web:adopted.logdate.app",
                        handle = "adopted.logdate.app",
                        signingKeyPublic = "zFixtureKey",
                        publicIdentityDid = "did:web:previous.logdate.app",
                    ),
                    Account(
                        id = Uuid.random(),
                        username = "inactive",
                        displayName = "Inactive",
                        createdAt = Clock.System.now(),
                        did = "did:web:inactive.logdate.app",
                        handle = "inactive.logdate.app",
                        signingKeyPublic = "zFixtureKey",
                        publicIdentityDid = "did:web:inactive.logdate.app",
                        isActive = false,
                    ),
                )
            runBlocking { accounts.forEach { env.accountRepository.save(it) } }
            for (account in accounts) {
                val response = client.get("/xrpc/com.atproto.identity.resolveHandle?handle=${account.handle}")
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val document = client.get("/.well-known/did.json") { header(HttpHeaders.Host, requireNotNull(account.handle)) }
                assertEquals(HttpStatusCode.NotFound, document.status)
                for (repo in listOf(account.handle, account.did)) {
                    assertEquals(HttpStatusCode.BadRequest, client.get("/xrpc/com.atproto.repo.describeRepo?repo=$repo").status)
                }
            }
        }

    @Test
    fun `private PLC identity looks like a missing handle without losing internal access`() =
        verifyPrivateIdentity(HostedAccountDidMethod.PLC)

    @Test
    fun `private Web identity looks like a missing handle and hides its DID document`() = verifyPrivateIdentity(HostedAccountDidMethod.WEB)

    private fun verifyPrivateIdentity(method: HostedAccountDidMethod) =
        testApplication {
            val env = configureAuthV1TestApp(atprotoIdentityConfig = AtprotoIdentityConfig(hostedAccountDidMethod = method))
            val account =
                runBlocking {
                    env.atprotoIdentityService.ensureIdentity(
                        env.accountRepository.save(
                            Account(id = Uuid.random(), username = "private", displayName = "Private", createdAt = Clock.System.now()),
                        ),
                    )
                }
            val handle = requireNotNull(account.handle)

            for (path in listOf("/.well-known/atproto-did", "/.well-known/did.json")) {
                val actual = client.get(path) { header(HttpHeaders.Host, handle) }
                val missing = client.get(path) { header(HttpHeaders.Host, "missing.logdate.app") }
                assertEquals(HttpStatusCode.NotFound, actual.status, path)
                assertEquals(missing.bodyAsText(), actual.bodyAsText(), path)
            }
            val resolved = client.get("/xrpc/com.atproto.identity.resolveHandle?handle=$handle")
            val missing = client.get("/xrpc/com.atproto.identity.resolveHandle?handle=missing.logdate.app")
            assertEquals(HttpStatusCode.BadRequest, resolved.status)
            assertEquals(missing.bodyAsText().replace("missing.logdate.app", handle), resolved.bodyAsText())

            val description = client.get("/xrpc/com.atproto.repo.describeRepo?repo=$handle")
            assertEquals(HttpStatusCode.BadRequest, description.status)

            runBlocking {
                assertNull(
                    env.atprotoIdentityService
                        .didDocument(
                            studio.hypertext.atproto.identity.AtprotoDid
                                .require(requireNotNull(account.did)),
                        ).getOrThrow(),
                )
                assertEquals(account, env.atprotoIdentityService.findByHandle(handle))
                assertEquals(account, env.atprotoIdentityService.findByDid(requireNotNull(account.did)))
                assertEquals(account, env.accountRepository.findById(account.id))
            }
        }
}
