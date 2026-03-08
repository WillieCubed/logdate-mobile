package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class IdentityRouteCoverageTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `identity routes return not found for unknown hosts and serve server did document`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val identityService = identityService(accountRepository)
            runBlocking {
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "alice",
                        displayName = "Alice",
                        createdAt = Clock.System.now(),
                    ),
                )
            }

            application {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    identityRoutes(identityService)
                }
            }

            val missingDid =
                client.get("/.well-known/atproto-did") {
                    header(HttpHeaders.Host, "missing.logdate.app")
                }
            val serverDidDocument =
                client.get("/.well-known/did.json") {
                    header(HttpHeaders.Host, "logdate.app")
                }
            val missingDidDocument =
                client.get("/.well-known/did.json") {
                    header(HttpHeaders.Host, "missing.logdate.app")
                }

            assertEquals(HttpStatusCode.NotFound, missingDid.status)
            assertEquals(HttpStatusCode.OK, serverDidDocument.status)
            assertEquals(HttpStatusCode.NotFound, missingDidDocument.status)

            val payload = json.parseToJsonElement(serverDidDocument.bodyAsText()).jsonObject
            assertEquals("did:web:logdate.app", payload["id"]?.jsonPrimitive?.content)
        }

    private fun identityService(accountRepository: InMemoryAccountRepository): AtprotoIdentityService =
        AtprotoIdentityService(
            accountRepository = accountRepository,
            signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
            config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
        )
}
