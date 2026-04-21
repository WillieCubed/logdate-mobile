package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.configureAuthV1TestApp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests the identity resolution and metadata endpoints for AT Protocol compatibility.
 *
 * This suite validates the server's ability to resolve DIDs via `.well-known` paths for
 * both PLC and Web methods. It also covers XRPC endpoints for handle resolution and
 * repository/server descriptions, ensuring that the server correctly identifies itself
 * and its users to the broader AT Protocol network.
 */
@OptIn(ExperimentalUuidApi::class)
class IdentityRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `well known identity routes resolve host mapped account`() =
        testApplication {
            val env = configureAuthV1TestApp()
            runBlocking {
                env.accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "alice",
                        displayName = "Alice",
                        createdAt = Clock.System.now(),
                    ),
                )
            }

            val didResponse =
                client.get("/.well-known/atproto-did") {
                    header(HttpHeaders.Host, "alice.logdate.app")
                }
            assertEquals(HttpStatusCode.OK, didResponse.status)
            assertTrue(didResponse.bodyAsText().startsWith("did:plc:"))

            val didDocumentResponse =
                client.get("/.well-known/did.json") {
                    header(HttpHeaders.Host, "alice.logdate.app")
                }
            assertEquals(HttpStatusCode.NotFound, didDocumentResponse.status)
        }

    @Test
    fun `well known did document serves did web account identities`() =
        testApplication {
            val env =
                configureAuthV1TestApp(
                    atprotoIdentityConfig =
                        app.logdate.server.identity.AtprotoIdentityConfig(
                            handleDomain = "logdate.app",
                            pdsServiceEndpoint = "https://pds.logdate.app",
                            hostedAccountDidMethod = app.logdate.server.identity.HostedAccountDidMethod.WEB,
                        ),
                )
            runBlocking {
                env.accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "didweb",
                        displayName = "Did Web",
                        createdAt = Clock.System.now(),
                        handle = "didweb.logdate.app",
                        did = "did:web:didweb.logdate.app",
                        signingKeyPublic = "zDidWebKey",
                    ),
                )
            }

            val didDocumentResponse =
                client.get("/.well-known/did.json") {
                    header(HttpHeaders.Host, "didweb.logdate.app")
                }

            assertEquals(HttpStatusCode.OK, didDocumentResponse.status)
            val payload = json.parseToJsonElement(didDocumentResponse.bodyAsText()).jsonObject
            assertEquals("did:web:didweb.logdate.app", payload["id"]?.jsonPrimitive?.content)
        }

    @Test
    fun `xrpc resolveHandle returns expected success and errors`() =
        testApplication {
            val env = configureAuthV1TestApp()
            runBlocking {
                env.accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "brie",
                        displayName = "Brie",
                        createdAt = Clock.System.now(),
                    ),
                )
            }

            val success = client.get("/xrpc/com.atproto.identity.resolveHandle?handle=brie.logdate.app")
            assertEquals(HttpStatusCode.OK, success.status)
            val successPayload = json.parseToJsonElement(success.bodyAsText()).jsonObject
            assertTrue(successPayload["did"]?.jsonPrimitive?.content?.startsWith("did:plc:") == true)

            val missingHandle = client.get("/xrpc/com.atproto.identity.resolveHandle")
            assertEquals(HttpStatusCode.BadRequest, missingHandle.status)
            assertTrue(missingHandle.bodyAsText().contains("InvalidRequest"))

            val missingAccount = client.get("/xrpc/com.atproto.identity.resolveHandle?handle=missing.logdate.app")
            assertEquals(HttpStatusCode.BadRequest, missingAccount.status)
            assertTrue(missingAccount.bodyAsText().contains("HandleNotFound"))
        }

    @Test
    fun `xrpc describeServer and describeRepo expose identity metadata`() =
        testApplication {
            val env = configureAuthV1TestApp()
            runBlocking {
                env.accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "cora",
                        displayName = "Cora",
                        createdAt = Clock.System.now(),
                    ),
                )
            }

            val describeServer = client.get("/xrpc/com.atproto.server.describeServer")
            assertEquals(HttpStatusCode.OK, describeServer.status)
            val serverPayload = json.parseToJsonElement(describeServer.bodyAsText()).jsonObject
            assertEquals("did:web:logdate.app", serverPayload["did"]?.jsonPrimitive?.content)
            assertEquals(
                "logdate.app",
                serverPayload["availableUserDomains"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonPrimitive
                    ?.content,
            )

            val describeRepo = client.get("/xrpc/com.atproto.repo.describeRepo?repo=cora.logdate.app")
            assertEquals(HttpStatusCode.OK, describeRepo.status)
            val repoPayload = json.parseToJsonElement(describeRepo.bodyAsText()).jsonObject
            assertEquals("cora.logdate.app", repoPayload["handle"]?.jsonPrimitive?.content)
            assertTrue(repoPayload["did"]?.jsonPrimitive?.content?.startsWith("did:plc:") == true)
        }
}
