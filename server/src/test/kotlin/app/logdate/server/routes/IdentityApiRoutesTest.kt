package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.configureAuthV1TestApp
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.HostedAccountDidMethod
import app.logdate.server.identity.InMemoryHostedPlcOperationRepository
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.PlcIdentityService
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.identity.didKeyFor
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
import studio.hypertext.atproto.plc.PlcDirectoryClient
import studio.hypertext.atproto.plc.PlcIndexedOperation
import studio.hypertext.atproto.plc.PlcLogEntry
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
    fun `signing key rotation returns exported replacement key for did web accounts`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val env =
                configureAuthV1TestApp(
                    accountRepository = accountRepository,
                    atprotoIdentityConfig =
                        AtprotoIdentityConfig(
                            handleDomain = "logdate.app",
                            pdsServiceEndpoint = "https://logdate.app",
                            hostedAccountDidMethod = HostedAccountDidMethod.WEB,
                        ),
                )
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "rotate-web",
                        displayName = "Rotate Web",
                        createdAt = Clock.System.now(),
                    ),
                )
            val accessToken = env.tokenService.generateAccessToken(account.id.toString())

            val response =
                client.post("/api/v1/identity/signing-key/rotate") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"passphrase":"new-secret"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.decodeFromString<RotateSigningKeyResponse>(response.bodyAsText())
            val decrypted = env.signingKeyService.decryptExportedKey(payload.data.exportedKey, "new-secret")

            assertTrue(payload.success)
            assertEquals("rotate-web.logdate.app", payload.data.handle)
            assertEquals("did:web:rotate-web.logdate.app", payload.data.did)
            assertTrue(payload.data.previousPublicKeyDidKey.startsWith("did:key:z"))
            assertEquals("EC", decrypted.algorithm)
        }

    @Test
    fun `signing key rotation rejects hosted plc accounts without plc publishing`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val env = configureAuthV1TestApp(accountRepository = accountRepository)
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "rotate-plc",
                        displayName = "Rotate PLC",
                        createdAt = Clock.System.now(),
                    ),
                )
            val accessToken = env.tokenService.generateAccessToken(account.id.toString())

            val response =
                client.post("/api/v1/identity/signing-key/rotate") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"passphrase":"new-secret"}""")
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertEquals(
                "SIGNING_KEY_ROTATION_CONFLICT",
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
    fun `signing key import restores matching active key material and rejects mismatches`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val env =
                configureAuthV1TestApp(
                    accountRepository = accountRepository,
                    atprotoIdentityConfig =
                        AtprotoIdentityConfig(
                            handleDomain = "logdate.app",
                            pdsServiceEndpoint = "https://logdate.app",
                            hostedAccountDidMethod = HostedAccountDidMethod.WEB,
                        ),
                )
            val firstAccount =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "importable",
                        displayName = "Importable",
                        createdAt = Clock.System.now(),
                    ),
                )
            val secondAccount =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "other-user",
                        displayName = "Other User",
                        createdAt = Clock.System.now(),
                    ),
                )
            val firstToken = env.tokenService.generateAccessToken(firstAccount.id.toString())
            val firstEnsured = env.atprotoIdentityService.ensureIdentity(firstAccount)
            val secondEnsured = env.atprotoIdentityService.ensureIdentity(secondAccount)
            val matchingExport = env.signingKeyService.exportActiveKey(firstEnsured.id, "import-secret")
            val mismatchedExport = env.signingKeyService.exportActiveKey(secondEnsured.id, "other-secret")

            val success =
                client.post("/api/v1/identity/signing-key/import") {
                    header(HttpHeaders.Authorization, "Bearer $firstToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            ImportSigningKeyRequest.serializer(),
                            ImportSigningKeyRequest(
                                passphrase = "import-secret",
                                exportedKey = matchingExport,
                            ),
                        ),
                    )
                }
            val conflict =
                client.post("/api/v1/identity/signing-key/import") {
                    header(HttpHeaders.Authorization, "Bearer $firstToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            ImportSigningKeyRequest.serializer(),
                            ImportSigningKeyRequest(
                                passphrase = "other-secret",
                                exportedKey = mismatchedExport,
                            ),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.OK, success.status)
            assertEquals(HttpStatusCode.Conflict, conflict.status)
            val payload = json.decodeFromString<ImportSigningKeyResponse>(success.bodyAsText())
            assertTrue(payload.success)
            assertEquals("importable.logdate.app", payload.data.handle)
            assertEquals("did:web:importable.logdate.app", payload.data.did)
            assertTrue(payload.data.publicKeyDidKey.startsWith("did:key:z"))
        }

    @Test
    fun `plc recovery key registration publishes hosted update and persists canonical did key`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val tokenService = JwtTokenService("identity-api-route-secret")
            val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
            val published = mutableMapOf<String, MutableList<PlcIndexedOperation>>()
            val identityService =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = signingKeyService,
                    config =
                        AtprotoIdentityConfig(
                            handleDomain = "logdate.app",
                            pdsServiceEndpoint = "https://logdate.app",
                            publishHostedPlcOperations = true,
                        ),
                    plcIdentityService =
                        PlcIdentityService(
                            signingKeyService = signingKeyService,
                            config =
                                AtprotoIdentityConfig(
                                    handleDomain = "logdate.app",
                                    pdsServiceEndpoint = "https://logdate.app",
                                    publishHostedPlcOperations = true,
                                ),
                            hostedPlcOperationRepository = InMemoryHostedPlcOperationRepository(),
                            plcDirectoryClient =
                                object : PlcDirectoryClient {
                                    override suspend fun getDocument(
                                        did: studio.hypertext.atproto.identity.AtprotoDid,
                                    ): Result<studio.hypertext.atproto.identity.DidDocument> {
                                        error("unused")
                                    }

                                    override suspend fun getOperationLog(
                                        did: studio.hypertext.atproto.identity.AtprotoDid,
                                    ): Result<List<PlcLogEntry>> {
                                        error("unused")
                                    }

                                    override suspend fun getAuditLog(
                                        did: studio.hypertext.atproto.identity.AtprotoDid,
                                    ): Result<List<PlcIndexedOperation>> = Result.success(published[did.toString()].orEmpty())

                                    override suspend fun export(
                                        after: String?,
                                        count: Int?,
                                    ) = error("unused")

                                    override suspend fun submit(
                                        did: studio.hypertext.atproto.identity.AtprotoDid,
                                        entry: PlcLogEntry,
                                    ): Result<Unit> =
                                        runCatching {
                                            val operations = published.getOrPut(did.toString()) { mutableListOf() }
                                            operations +=
                                                PlcIndexedOperation(
                                                    did = did,
                                                    operation = entry,
                                                    cid = "cid-${operations.size + 1}",
                                                    nullified = false,
                                                    createdAt = "2026-03-09T00:00:00Z",
                                                )
                                        }
                                },
                        ),
                )
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "recoverable",
                        displayName = "Recoverable",
                        createdAt = Clock.System.now(),
                    ),
                )
            val accessToken = tokenService.generateAccessToken(account.id.toString())
            val recoveryDidKey = didKeyFor(signingKeyService.ensureActiveKey(Uuid.random()).publicKeyMultibase)

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
                client.post("/api/v1/identity/plc/recovery-key") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"recoveryDidKey":"  DID:KEY:${recoveryDidKey.removePrefix("did:key:")}  "}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.decodeFromString<RegisterPlcRecoveryKeyResponse>(response.bodyAsText())

            assertTrue(payload.success)
            assertEquals(recoveryDidKey, payload.data.recoveryDidKey)
            assertTrue(payload.data.did.startsWith("did:plc:"))
            assertEquals(recoveryDidKey, accountRepository.findById(account.id)?.plcRecoveryDidKey)
            assertEquals(2, published.getValue(payload.data.did).size)
        }

    @Test
    fun `plc recovery key registration rejects invalid did keys and did web identities`() =
        testApplication {
            val invalidAccountRepository = InMemoryAccountRepository()
            val invalidEnv = configureAuthV1TestApp(accountRepository = invalidAccountRepository)
            val invalidAccount =
                invalidAccountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "invalid-recovery",
                        displayName = "Invalid Recovery",
                        createdAt = Clock.System.now(),
                    ),
                )
            val invalidToken = invalidEnv.tokenService.generateAccessToken(invalidAccount.id.toString())
            val invalidResponse =
                client.post("/api/v1/identity/plc/recovery-key") {
                    header(HttpHeaders.Authorization, "Bearer $invalidToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"recoveryDidKey":"not-a-did-key"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, invalidResponse.status)
            assertEquals(
                "PLC_RECOVERY_KEY_INVALID",
                json
                    .parseToJsonElement(invalidResponse.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonObject
                    ?.get("code")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `plc recovery key registration rejects did web accounts`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val env =
                configureAuthV1TestApp(
                    accountRepository = accountRepository,
                    atprotoIdentityConfig =
                        AtprotoIdentityConfig(
                            handleDomain = "logdate.app",
                            pdsServiceEndpoint = "https://logdate.app",
                            hostedAccountDidMethod = HostedAccountDidMethod.WEB,
                        ),
                )
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "web-recovery",
                        displayName = "Web Recovery",
                        createdAt = Clock.System.now(),
                    ),
                )
            val accessToken = env.tokenService.generateAccessToken(account.id.toString())
            val recoveryDidKey = didKeyFor(env.signingKeyService.ensureActiveKey(Uuid.random()).publicKeyMultibase)

            val response =
                client.post("/api/v1/identity/plc/recovery-key") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"recoveryDidKey":"$recoveryDidKey"}""")
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertEquals(
                "PLC_RECOVERY_KEY_CONFLICT",
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
        val rotateRequest = RotateSigningKeyRequest(passphrase = "rotate-secret")
        val rotateData =
            RotateSigningKeyData(
                did = "did:web:alice.logdate.app",
                handle = "alice.logdate.app",
                previousPublicKeyDidKey = "did:key:zOld",
                exportedKey = exportedKey,
            )
        val importRequest = ImportSigningKeyRequest(passphrase = "import-secret", exportedKey = exportedKey)
        val importData =
            ImportSigningKeyData(
                did = "did:web:alice.logdate.app",
                handle = "alice.logdate.app",
                publicKeyDidKey = "did:key:zPublic",
            )
        val recoveryKeyRequest = RegisterPlcRecoveryKeyRequest(recoveryDidKey = "did:key:zRecovery")
        val recoveryKeyData =
            RegisterPlcRecoveryKeyData(
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
                recoveryDidKey = "did:key:zRecovery",
            )

        val requestJson = json.encodeToString(ExportSigningKeyRequest.serializer(), request)
        val dataJson = json.encodeToString(ExportSigningKeyData.serializer(), data)
        val rotateRequestJson = json.encodeToString(RotateSigningKeyRequest.serializer(), rotateRequest)
        val rotateDataJson = json.encodeToString(RotateSigningKeyData.serializer(), rotateData)
        val importRequestJson = json.encodeToString(ImportSigningKeyRequest.serializer(), importRequest)
        val importDataJson = json.encodeToString(ImportSigningKeyData.serializer(), importData)
        val recoveryKeyRequestJson = json.encodeToString(RegisterPlcRecoveryKeyRequest.serializer(), recoveryKeyRequest)
        val recoveryKeyDataJson = json.encodeToString(RegisterPlcRecoveryKeyData.serializer(), recoveryKeyData)
        val exportedKeyJson = json.encodeToString(SigningKeyService.ExportedSigningKey.serializer(), exportedKey)

        assertEquals(request, json.decodeFromString(ExportSigningKeyRequest.serializer(), requestJson))
        assertEquals(data, json.decodeFromString(ExportSigningKeyData.serializer(), dataJson))
        assertEquals(rotateRequest, json.decodeFromString(RotateSigningKeyRequest.serializer(), rotateRequestJson))
        assertEquals(rotateData, json.decodeFromString(RotateSigningKeyData.serializer(), rotateDataJson))
        assertEquals(importRequest, json.decodeFromString(ImportSigningKeyRequest.serializer(), importRequestJson))
        assertEquals(importData, json.decodeFromString(ImportSigningKeyData.serializer(), importDataJson))
        assertEquals(recoveryKeyRequest, json.decodeFromString(RegisterPlcRecoveryKeyRequest.serializer(), recoveryKeyRequestJson))
        assertEquals(recoveryKeyData, json.decodeFromString(RegisterPlcRecoveryKeyData.serializer(), recoveryKeyDataJson))
        assertEquals(exportedKey, json.decodeFromString(SigningKeyService.ExportedSigningKey.serializer(), exportedKeyJson))
    }
}
