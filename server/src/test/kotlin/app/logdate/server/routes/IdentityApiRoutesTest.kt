package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.configureAuthV1TestApp
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class IdentityApiRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `signing key export requires bearer auth and non blank passphrase`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val env = configureAuthV1TestApp(accountRepository = accountRepository)
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "alice",
                        displayName = "Alice",
                        createdAt = Clock.System.now(),
                    ),
                )
            val accessToken = env.tokenService.generateAccessToken(account.id.toString())

            val missingAuth =
                client.post("/api/v1/identity/signing-key/export") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"passphrase":"secret"}""")
                }
            val blankPassphrase =
                client.post("/api/v1/identity/signing-key/export") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"passphrase":"   "}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, missingAuth.status)
            assertEquals(HttpStatusCode.BadRequest, blankPassphrase.status)
            assertEquals(
                "PASSPHRASE_REQUIRED",
                json
                    .parseToJsonElement(blankPassphrase.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonObject
                    ?.get("code")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `signing key export returns decryptable key material for authenticated account`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val env = configureAuthV1TestApp(accountRepository = accountRepository)
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "brie",
                        displayName = "Brie",
                        createdAt = Clock.System.now(),
                    ),
                )
            val accessToken = env.tokenService.generateAccessToken(account.id.toString())

            val response =
                client.post("/api/v1/identity/signing-key/export") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"passphrase":"correct horse battery staple"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.decodeFromString<ExportSigningKeyResponse>(response.bodyAsText())
            val decrypted =
                env.signingKeyService.decryptExportedKey(
                    payload.data.exportedKey,
                    passphrase = "correct horse battery staple",
                )

            assertTrue(payload.success)
            assertEquals("brie.logdate.app", payload.data.handle)
            assertTrue(payload.data.did.startsWith("did:plc:"))
            assertEquals("P-256", payload.data.exportedKey.algorithm)
            assertTrue(
                payload.data.exportedKey.publicKeyDidKey
                    .startsWith("did:key:z"),
            )
            assertNotNull(decrypted)
            assertEquals("EC", decrypted.algorithm)
        }

    @Test
    fun `signing key export rejects blank invalid malformed and missing-account tokens`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val env = configureAuthV1TestApp(accountRepository = accountRepository)
            val existingAccount =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "cora",
                        displayName = "Cora",
                        createdAt = Clock.System.now(),
                    ),
                )

            val blankBearer =
                client.post("/api/v1/identity/signing-key/export") {
                    header(HttpHeaders.Authorization, "Bearer    ")
                    contentType(ContentType.Application.Json)
                    setBody("""{"passphrase":"secret"}""")
                }
            val invalidToken =
                client.post("/api/v1/identity/signing-key/export") {
                    header(HttpHeaders.Authorization, "Bearer not-a-token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"passphrase":"secret"}""")
                }
            val malformedAccountId =
                client.post("/api/v1/identity/signing-key/export") {
                    header(HttpHeaders.Authorization, "Bearer ${env.tokenService.generateAccessToken("not-a-uuid")}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"passphrase":"secret"}""")
                }
            val missingAccount =
                client.post("/api/v1/identity/signing-key/export") {
                    header(HttpHeaders.Authorization, "Bearer ${env.tokenService.generateAccessToken(Uuid.random().toString())}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"passphrase":"secret"}""")
                }
            val validAccount =
                client.post("/api/v1/identity/signing-key/export") {
                    header(HttpHeaders.Authorization, "Bearer ${env.tokenService.generateAccessToken(existingAccount.id.toString())}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"passphrase":"secret"}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, blankBearer.status)
            assertEquals(HttpStatusCode.Unauthorized, invalidToken.status)
            assertEquals(HttpStatusCode.Unauthorized, malformedAccountId.status)
            assertEquals(HttpStatusCode.NotFound, missingAccount.status)
            assertEquals(HttpStatusCode.OK, validAccount.status)
        }

    @Test
    fun `signing key export returns server error when key export fails`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val tokenService = JwtTokenService("identity-api-route-secret")
            val signingKeyService = mockk<SigningKeyService>()
            val identityService =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
                )
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "dana",
                        displayName = "Dana",
                        createdAt = Clock.System.now(),
                    ),
                )

            coEvery { signingKeyService.exportActiveKey(account.id, "explode") } throws IllegalStateException("boom")

            application {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    route("/api/v1") {
                        identityApiRoutes(
                            accountRepository = accountRepository,
                            tokenService = tokenService,
                            atprotoIdentityService = identityService,
                            signingKeyService = signingKeyService,
                        )
                    }
                }
            }

            val response =
                client.post("/api/v1/identity/signing-key/export") {
                    header(HttpHeaders.Authorization, "Bearer ${tokenService.generateAccessToken(account.id.toString())}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"passphrase":"explode"}""")
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals(
                "SIGNING_KEY_EXPORT_FAILED",
                json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonObject
                    ?.get("code")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `identity export models serialize explicitly`() {
        val request = ExportSigningKeyRequest(passphrase = "secret")
        val exportedKey =
            SigningKeyService.ExportedSigningKey(
                algorithm = "P-256",
                publicKeyMultibase = "zPublic",
                publicKeyDidKey = "did:key:zPublic",
                encryptedPrivateKey = "ciphertext",
                salt = "salt",
                iv = "iv",
            )
        val data =
            ExportSigningKeyData(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                exportedKey = exportedKey,
            )

        val requestJson = json.encodeToString(ExportSigningKeyRequest.serializer(), request)
        val dataJson = json.encodeToString(ExportSigningKeyData.serializer(), data)
        val exportedKeyJson = json.encodeToString(SigningKeyService.ExportedSigningKey.serializer(), exportedKey)

        assertEquals(request, json.decodeFromString(ExportSigningKeyRequest.serializer(), requestJson))
        assertEquals(data, json.decodeFromString(ExportSigningKeyData.serializer(), dataJson))
        assertEquals(exportedKey, json.decodeFromString(SigningKeyService.ExportedSigningKey.serializer(), exportedKeyJson))
    }
}
