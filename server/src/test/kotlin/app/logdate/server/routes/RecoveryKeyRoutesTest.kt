package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.HostedAccountDidMethod
import app.logdate.server.identity.InMemoryHostedPlcOperationRepository
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.PlcIdentityService
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.identity.didKeyFor
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.crypto.EcCurve
import studio.hypertext.atproto.crypto.EcKeySupport
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The recovery-key endpoints over HTTP on a server that publishes nothing to the PLC directory:
 * register, prepare, sign on the "device", complete, and every refusal a client has to show.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)
class RecoveryKeyRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a registered recovery key replaces the server key over http`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true)

            assertEquals(HttpStatusCode.OK, client.register(server).status)
            val prepared = client.prepare(server)
            assertEquals(HttpStatusCode.OK, prepared.status)
            val data = json.decodeFromString<PrepareIdentityRecoveryResponse>(prepared.bodyAsText()).data
            assertEquals(server.recoveryDidKey, data.recoveryDidKey)
            assertTrue(data.unsignedOperationJson.contains(""""rotationKeys":["${server.recoveryDidKey}","${data.nextPublicKeyDidKey}"]"""))

            val completed = client.complete(server, data.preparedKey, server.sign(data.signingPayloadBase64Url))

            assertEquals(HttpStatusCode.OK, completed.status)
            val installed = json.decodeFromString<ImportSigningKeyResponse>(completed.bodyAsText()).data
            assertEquals(data.nextPublicKeyDidKey, installed.publicKeyDidKey)
            val history =
                client.get("/api/v1/identity/plc/operations") { header(HttpHeaders.Authorization, "Bearer ${server.accessToken}") }
            val operations = json.decodeFromString<HostedPlcOperationsResponse>(history.bodyAsText()).data
            assertEquals(3, operations.size)
            assertEquals(operations[1].cid, operations[2].prevCid)
        }

    @Test
    fun `completing a stale recovery answers identity changed`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true)
            client.register(server)
            val first = json.decodeFromString<PrepareIdentityRecoveryResponse>(client.prepare(server).bodyAsText()).data
            val second = json.decodeFromString<PrepareIdentityRecoveryResponse>(client.prepare(server).bodyAsText()).data
            client.complete(server, first.preparedKey, server.sign(first.signingPayloadBase64Url))

            val stale = client.complete(server, second.preparedKey, server.sign(second.signingPayloadBase64Url))

            assertEquals(HttpStatusCode.Conflict, stale.status)
            assertEquals("IDENTITY_CHANGED", stale.errorCode())
        }

    @Test
    fun `completing needs a signature and a valid one`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true)
            client.register(server)
            val prepared = json.decodeFromString<PrepareIdentityRecoveryResponse>(client.prepare(server).bodyAsText()).data
            val stranger = server.signingKeys.generateKeyPair(EcCurve.P256)

            val blank = client.complete(server, prepared.preparedKey, " ")
            val wrongKey = client.complete(server, prepared.preparedKey, sign(stranger.privateKeyPkcs8, prepared.signingPayloadBase64Url))

            assertEquals(HttpStatusCode.BadRequest, blank.status)
            assertEquals("SIGNATURE_REQUIRED", blank.errorCode())
            assertEquals(HttpStatusCode.BadRequest, wrongKey.status)
            assertEquals("RECOVERY_SIGNATURE_INVALID", wrongKey.errorCode())
        }

    @Test
    fun `a different key is refused once one is registered`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true)
            client.register(server)
            val other = didKeyFor(server.signingKeys.generateKeyPair(EcCurve.P256).publicKeyMultibase)

            val refused = client.register(server, other)

            assertEquals(HttpStatusCode.Conflict, refused.status)
            assertEquals("RECOVERY_KEY_ALREADY_SET", refused.errorCode())
        }

    @Test
    fun `recovery before registering answers recovery key not set`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true)

            val refused = client.prepare(server)

            assertEquals(HttpStatusCode.Conflict, refused.status)
            assertEquals("RECOVERY_KEY_NOT_SET", refused.errorCode())
        }

    @Test
    fun `a server without recovery keys refuses every recovery key request`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = false)

            val register = client.register(server)
            val prepare = client.prepare(server)

            assertEquals(HttpStatusCode.Conflict, register.status)
            assertEquals("RECOVERY_KEYS_UNAVAILABLE", register.errorCode())
            assertEquals(HttpStatusCode.Conflict, prepare.status)
            assertEquals("RECOVERY_KEYS_UNAVAILABLE", prepare.errorCode())
        }

    private class TestServer(
        val signingKeys: SigningKeyService,
        val accessToken: String,
        val recoveryDidKey: String,
        private val recoveryPrivateKey: ByteArray,
    ) {
        fun sign(payloadBase64Url: String): String = sign(recoveryPrivateKey, payloadBase64Url)
    }

    private fun ApplicationTestBuilder.installServer(recoveryKeysEnabled: Boolean): TestServer {
        val accounts = InMemoryAccountRepository()
        val tokenService = JwtTokenService("recovery-key-route-secret")
        val signingKeys = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
        val config =
            AtprotoIdentityConfig(
                handleDomain = "logdate.app",
                pdsServiceEndpoint = "https://logdate.app",
                hostedAccountDidMethod = HostedAccountDidMethod.PLC,
                recoveryKeysEnabled = recoveryKeysEnabled,
            )
        val identity =
            AtprotoIdentityService(
                accountRepository = accounts,
                signingKeyService = signingKeys,
                config = config,
                plcIdentityService =
                    PlcIdentityService(
                        signingKeyService = signingKeys,
                        config = config,
                        hostedPlcOperationRepository = InMemoryHostedPlcOperationRepository(),
                    ),
            )
        val account =
            kotlinx.coroutines.runBlocking {
                accounts.save(Account(id = Uuid.random(), username = "alice", displayName = "Alice", createdAt = Clock.System.now()))
            }
        application {
            install(ContentNegotiation) { json(json) }
            routing {
                route("/api/v1") {
                    identityApiRoutes(
                        accountRepository = accounts,
                        tokenService = tokenService,
                        atprotoIdentityService = identity,
                        signingKeyService = signingKeys,
                    )
                }
            }
        }
        val recoveryKey = signingKeys.generateKeyPair(EcCurve.P256)
        return TestServer(
            signingKeys = signingKeys,
            accessToken = tokenService.generateAccessToken(account.id.toString()),
            recoveryDidKey = didKeyFor(recoveryKey.publicKeyMultibase),
            recoveryPrivateKey = recoveryKey.privateKeyPkcs8,
        )
    }

    private suspend fun HttpClient.register(
        server: TestServer,
        recoveryDidKey: String = server.recoveryDidKey,
    ): HttpResponse =
        post("/api/v1/identity/plc/recovery-key") {
            header(HttpHeaders.Authorization, "Bearer ${server.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"recoveryDidKey":"$recoveryDidKey"}""")
        }

    private suspend fun HttpClient.prepare(server: TestServer): HttpResponse =
        post("/api/v1/identity/recovery-key/recover/prepare") {
            header(HttpHeaders.Authorization, "Bearer ${server.accessToken}")
        }

    private suspend fun HttpClient.complete(
        server: TestServer,
        preparedKey: String,
        signature: String,
    ): HttpResponse =
        post("/api/v1/identity/recovery-key/recover/complete") {
            header(HttpHeaders.Authorization, "Bearer ${server.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(CompleteIdentityRecoveryRequest.serializer(), CompleteIdentityRecoveryRequest(preparedKey, signature)),
            )
        }

    private suspend fun HttpResponse.errorCode(): String? =
        json
            .parseToJsonElement(bodyAsText())
            .jsonObject["error"]
            ?.jsonObject
            ?.get("code")
            ?.jsonPrimitive
            ?.content

    private companion object {
        fun sign(
            privateKeyPkcs8: ByteArray,
            payloadBase64Url: String,
        ): String {
            val payload = Base64.UrlSafe.decode(payloadBase64Url + "=".repeat((4 - payloadBase64Url.length % 4) % 4))
            return Base64.UrlSafe
                .encode(EcKeySupport.signSha256(EcKeySupport.decodePrivateKey(privateKeyPkcs8), EcCurve.P256, payload))
                .trimEnd('=')
        }
    }
}
