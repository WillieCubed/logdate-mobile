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
import app.logdate.server.passkeys.InMemoryPasskeyRepository
import app.logdate.server.passkeys.PasskeyStepUpProof
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.passkeys.WebAuthnPasskeyStepUp
import app.logdate.shared.model.PasskeyAssertionAuthenticatorResponse
import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyInfo
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
 * register, prepare, sign on the "device", complete, and every refusal a client has to show. Every
 * change is confirmed with a passkey through the real step-up service, which runs its lightweight
 * verification here, so challenge kinds and account binding are what is being checked.
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

            val register = client.register(server, stepUp = null)
            val prepare = client.prepare(server)

            assertEquals(HttpStatusCode.Conflict, register.status)
            assertEquals("RECOVERY_KEYS_UNAVAILABLE", register.errorCode())
            assertEquals(HttpStatusCode.Conflict, prepare.status)
            assertEquals("RECOVERY_KEYS_UNAVAILABLE", prepare.errorCode())
        }

    @Test
    fun `registering, replacing and completing each need a passkey confirmation`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true)

            val unconfirmed = client.register(server, stepUp = null)
            assertEquals(HttpStatusCode.Forbidden, unconfirmed.status)
            assertEquals("STEP_UP_REQUIRED", unconfirmed.errorCode())

            assertEquals(HttpStatusCode.OK, client.register(server).status)
            val other = didKeyFor(server.signingKeys.generateKeyPair(EcCurve.P256).publicKeyMultibase)
            val replace = client.replace(server, other, stepUp = null)
            assertEquals(HttpStatusCode.Forbidden, replace.status)
            assertEquals("STEP_UP_REQUIRED", replace.errorCode())

            val prepared = json.decodeFromString<PrepareIdentityRecoveryResponse>(client.prepare(server).bodyAsText()).data
            val complete = client.complete(server, prepared.preparedKey, server.sign(prepared.signingPayloadBase64Url), stepUp = null)
            assertEquals(HttpStatusCode.Forbidden, complete.status)
            assertEquals("STEP_UP_REQUIRED", complete.errorCode())
        }

    @Test
    fun `a step up is single use and a sign in challenge or another account's passkey cannot stand in for one`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true)
            val spent = client.stepUp(server)
            client.register(server, stepUp = spent)
            val signInChallenge = server.webAuthn.generateAuthenticationOptions(userId = server.accountId).challenge
            server.addPasskey(Uuid.random(), "someone-elses-passkey")
            val stranger = client.stepUp(server).copy(credential = server.assertion("someone-elses-passkey"))
            val other = didKeyFor(server.signingKeys.generateKeyPair(EcCurve.P256).publicKeyMultibase)

            val reused = client.replace(server, other, stepUp = spent)
            val signIn = client.replace(server, other, stepUp = PasskeyStepUpProof(signInChallenge, server.assertion()))
            val strangers = client.replace(server, other, stepUp = stranger)

            listOf(reused, signIn, strangers).forEach { refused ->
                assertEquals(HttpStatusCode.Forbidden, refused.status)
                assertEquals("STEP_UP_FAILED", refused.errorCode())
            }
        }

    @Test
    fun `a sign in grant registers the first recovery key once and only for its own account`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true)
            val grant = server.webAuthn.issueStepUpGrant(server.accountId)
            val strangersGrant = server.webAuthn.issueStepUpGrant(Uuid.random())

            val stranger = client.registerWithGrant(server, strangersGrant)
            val registered = client.registerWithGrant(server, grant)
            val reused = client.registerWithGrant(server, grant)

            assertEquals(HttpStatusCode.Forbidden, stranger.status)
            assertEquals("STEP_UP_FAILED", stranger.errorCode())
            assertEquals(HttpStatusCode.OK, registered.status)
            assertEquals(
                server.recoveryDidKey,
                json.decodeFromString<RegisterPlcRecoveryKeyResponse>(registered.bodyAsText()).data.recoveryDidKey,
            )
            assertEquals(HttpStatusCode.Forbidden, reused.status)
            assertEquals("STEP_UP_FAILED", reused.errorCode())
        }

    @Test
    fun `a sign in grant cannot replace a recovery key or stand in for a step up challenge`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true)
            client.register(server)
            val other = didKeyFor(server.signingKeys.generateKeyPair(EcCurve.P256).publicKeyMultibase)
            val grant = server.webAuthn.issueStepUpGrant(server.accountId)

            val replace =
                client.post("/api/v1/identity/plc/recovery-key/replace") {
                    header(HttpHeaders.Authorization, "Bearer ${server.accessToken}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"recoveryDidKey":"$other","stepUpGrant":"$grant"}""")
                }
            val asChallenge = client.replace(server, other, stepUp = PasskeyStepUpProof(grant, server.assertion()))

            assertEquals(HttpStatusCode.Forbidden, replace.status)
            assertEquals("STEP_UP_REQUIRED", replace.errorCode())
            assertEquals(HttpStatusCode.Forbidden, asChallenge.status)
            assertEquals("STEP_UP_FAILED", asChallenge.errorCode())
        }

    @Test
    fun `a lost recovery key can be replaced with a passkey confirmation`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true)
            client.register(server)
            val replacement = didKeyFor(server.signingKeys.generateKeyPair(EcCurve.P256).publicKeyMultibase)

            val replaced = client.replace(server, replacement)

            assertEquals(HttpStatusCode.OK, replaced.status)
            assertEquals(replacement, json.decodeFromString<RegisterPlcRecoveryKeyResponse>(replaced.bodyAsText()).data.recoveryDidKey)
            val history =
                client.get("/api/v1/identity/plc/operations") { header(HttpHeaders.Authorization, "Bearer ${server.accessToken}") }
            val operations = json.decodeFromString<HostedPlcOperationsResponse>(history.bodyAsText()).data
            assertEquals(3, operations.size)
            assertTrue(operations.last().operationJson.contains("\"rotationKeys\":[\"$replacement\","))
            assertTrue(!operations.last().operationJson.contains(server.recoveryDidKey))
        }

    @Test
    fun `replacing before registering answers recovery key not set`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true)

            val refused = client.replace(server, server.recoveryDidKey)

            assertEquals(HttpStatusCode.Conflict, refused.status)
            assertEquals("RECOVERY_KEY_NOT_SET", refused.errorCode())
        }

    @Test
    fun `an account without a passkey cannot begin a confirmation`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true, withPasskey = false)

            val refused = client.stepUpResponse(server)

            assertEquals(HttpStatusCode.Conflict, refused.status)
            assertEquals("PASSKEY_REQUIRED", refused.errorCode())
        }

    @Test
    fun `a server that cannot confirm with a passkey refuses every change`() =
        testApplication {
            val server = installServer(recoveryKeysEnabled = true, withStepUp = false)
            val proof = PasskeyStepUpProof("unissued", server.assertion())

            val begin = client.stepUpResponse(server)
            val register = client.register(server, stepUp = proof)

            assertEquals("STEP_UP_UNAVAILABLE", begin.errorCode())
            assertEquals(HttpStatusCode.Conflict, register.status)
            assertEquals("STEP_UP_UNAVAILABLE", register.errorCode())
        }

    private class TestServer(
        val signingKeys: SigningKeyService,
        val accessToken: String,
        val recoveryDidKey: String,
        private val recoveryPrivateKey: ByteArray,
        val webAuthn: WebAuthnPasskeyService,
        val accountId: Uuid,
        private val passkeys: InMemoryPasskeyRepository,
    ) {
        fun sign(payloadBase64Url: String): String = sign(recoveryPrivateKey, payloadBase64Url)

        fun addPasskey(
            owner: Uuid,
            credentialId: String,
        ) {
            kotlinx.coroutines.runBlocking {
                passkeys.storePasskey(
                    userId = owner,
                    credentialId = credentialId,
                    publicKey = ByteArray(0),
                    signCount = 0,
                    info =
                        PasskeyInfo(
                            id = Uuid.random(),
                            credentialId = credentialId,
                            nickname = null,
                            deviceType = "platform",
                            createdAt = Clock.System.now(),
                            lastUsedAt = null,
                        ),
                )
            }
        }

        fun assertion(credentialId: String = PASSKEY_ID): PasskeyAssertionResponse =
            PasskeyAssertionResponse(
                id = credentialId,
                rawId = credentialId,
                response =
                    PasskeyAssertionAuthenticatorResponse(
                        clientDataJSON = "e30",
                        authenticatorData = "AA",
                        signature = "AA",
                        userHandle = accountId.toString(),
                    ),
            )
    }

    private fun ApplicationTestBuilder.installServer(
        recoveryKeysEnabled: Boolean,
        withPasskey: Boolean = true,
        withStepUp: Boolean = true,
    ): TestServer {
        val passkeys = InMemoryPasskeyRepository()
        val webAuthn = WebAuthnPasskeyService(passkeyRepository = passkeys, strictVerificationEnabled = false)
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
                        passkeyStepUp = if (withStepUp) WebAuthnPasskeyStepUp(webAuthn) else null,
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
            webAuthn = webAuthn,
            accountId = account.id,
            passkeys = passkeys,
        ).also { if (withPasskey) it.addPasskey(account.id, PASSKEY_ID) }
    }

    private suspend fun HttpClient.stepUpResponse(server: TestServer): HttpResponse =
        post("/api/v1/identity/recovery-key/step-up") {
            header(HttpHeaders.Authorization, "Bearer ${server.accessToken}")
        }

    /** Begins a step-up and answers it with the account's passkey, as the device would. */
    private suspend fun HttpClient.stepUp(server: TestServer): PasskeyStepUpProof {
        val response = stepUpResponse(server)
        assertEquals(HttpStatusCode.OK, response.status)
        val options = json.decodeFromString<RecoveryKeyStepUpResponse>(response.bodyAsText()).data
        assertEquals(listOf(PASSKEY_ID), options.allowCredentials.map { it.id })
        return PasskeyStepUpProof(challenge = options.challenge, credential = server.assertion())
    }

    /** [FRESH_STEP_UP] stands for "begin and answer a new step-up now". */
    private suspend fun HttpClient.resolve(
        server: TestServer,
        stepUp: PasskeyStepUpProof?,
    ): PasskeyStepUpProof? = if (stepUp === FRESH_STEP_UP) stepUp(server) else stepUp

    private fun stepUpJson(stepUp: PasskeyStepUpProof?): String =
        stepUp?.let { ",\"stepUp\":" + json.encodeToString(PasskeyStepUpProof.serializer(), it) }.orEmpty()

    private suspend fun HttpClient.replace(
        server: TestServer,
        recoveryDidKey: String,
        stepUp: PasskeyStepUpProof? = FRESH_STEP_UP,
    ): HttpResponse =
        post("/api/v1/identity/plc/recovery-key/replace") {
            header(HttpHeaders.Authorization, "Bearer ${server.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"recoveryDidKey":"$recoveryDidKey"${stepUpJson(resolve(server, stepUp))}}""")
        }

    private suspend fun HttpClient.register(
        server: TestServer,
        recoveryDidKey: String = server.recoveryDidKey,
        stepUp: PasskeyStepUpProof? = FRESH_STEP_UP,
    ): HttpResponse =
        post("/api/v1/identity/plc/recovery-key") {
            header(HttpHeaders.Authorization, "Bearer ${server.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"recoveryDidKey":"$recoveryDidKey"${stepUpJson(resolve(server, stepUp))}}""")
        }

    private suspend fun HttpClient.registerWithGrant(
        server: TestServer,
        grant: String,
    ): HttpResponse =
        post("/api/v1/identity/plc/recovery-key") {
            header(HttpHeaders.Authorization, "Bearer ${server.accessToken}")
            contentType(ContentType.Application.Json)
            setBody("""{"recoveryDidKey":"${server.recoveryDidKey}","stepUpGrant":"$grant"}""")
        }

    private suspend fun HttpClient.prepare(server: TestServer): HttpResponse =
        post("/api/v1/identity/recovery-key/recover/prepare") {
            header(HttpHeaders.Authorization, "Bearer ${server.accessToken}")
        }

    private suspend fun HttpClient.complete(
        server: TestServer,
        preparedKey: String,
        signature: String,
        stepUp: PasskeyStepUpProof? = FRESH_STEP_UP,
    ): HttpResponse =
        post("/api/v1/identity/recovery-key/recover/complete") {
            header(HttpHeaders.Authorization, "Bearer ${server.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CompleteIdentityRecoveryRequest.serializer(),
                    CompleteIdentityRecoveryRequest(preparedKey, signature, resolve(server, stepUp)),
                ),
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
        const val PASSKEY_ID = "alice-passkey"

        val FRESH_STEP_UP =
            PasskeyStepUpProof(
                challenge = "fresh",
                credential = PasskeyAssertionResponse("fresh", "fresh", PasskeyAssertionAuthenticatorResponse("", "", "", "")),
            )

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
