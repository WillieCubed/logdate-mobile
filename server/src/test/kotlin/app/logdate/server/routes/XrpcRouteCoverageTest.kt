package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.oauth.OAuthAccessTokenService
import app.logdate.server.oauth.OAuthConfig
import app.logdate.server.oauth.OAuthDpopVerifier
import app.logdate.server.oauth.OAuthKeyService
import app.logdate.server.oauth.OAuthNonceService
import app.logdate.server.oauth.createDpopProof
import app.logdate.server.oauth.generateP256KeyPair
import app.logdate.server.oauth.publicJwk
import io.ktor.client.request.get
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
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.repo.RepoListPage
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.repo.RepoRecordStore
import studio.hypertext.atproto.repo.RepoValidationStatus
import studio.hypertext.atproto.repo.RepoWriteResult
import studio.hypertext.atproto.repo.UnsupportedCollectionException
import studio.hypertext.atproto.syntax.AtUri
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class XrpcRouteCoverageTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val defaultConstructorMarkerClass = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")

    @Test
    fun `xrpc route models expose getters and default constructors`() {
        val didDocument = DidDocument(id = AtprotoDid.require("did:web:alice.logdate.app"))
        val repoRecord =
            RepoRecord(
                uri = AtUri.require("at://did:web:alice.logdate.app/studio.hypertext.logdate.content/entry-1"),
                cid = "bafy-record",
                value = buildJsonObject { put("content", "hello") },
            )

        val errorResponse = instantiatePrivate("app.logdate.server.routes.XrpcErrorResponse", "error", "message")
        val resolveHandle = instantiatePrivate("app.logdate.server.routes.ResolveHandleResponse", "did:web:alice.logdate.app")
        val describeServer =
            instantiatePrivate(
                "app.logdate.server.routes.DescribeServerResponse",
                "did:web:logdate.app",
                listOf("logdate.app"),
                false,
                false,
            )
        val describeRepo =
            instantiatePrivate(
                "app.logdate.server.routes.DescribeRepoResponse",
                "alice.logdate.app",
                "did:web:alice.logdate.app",
                didDocument,
                listOf("studio.hypertext.logdate.content"),
                true,
            )
        val listRecords =
            instantiatePrivateDefault(
                "app.logdate.server.routes.ListRecordsResponse",
                arrayOf<Class<*>>(List::class.java, String::class.java, Int::class.javaPrimitiveType!!, defaultConstructorMarkerClass),
                listOf(repoRecord),
                null,
                2,
                null,
            )
        val createRequest =
            instantiatePrivateDefault(
                "app.logdate.server.routes.CreateRecordRequest",
                arrayOf<Class<*>>(
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    java.lang.Boolean::class.java,
                    JsonObject::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    defaultConstructorMarkerClass,
                ),
                "did:web:alice.logdate.app",
                "studio.hypertext.logdate.content",
                null,
                null,
                buildJsonObject { put("content", "hello") },
                null,
                44,
                null,
            )
        val putRequest =
            instantiatePrivateDefault(
                "app.logdate.server.routes.PutRecordRequest",
                arrayOf<Class<*>>(
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    java.lang.Boolean::class.java,
                    JsonObject::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    defaultConstructorMarkerClass,
                ),
                "did:web:alice.logdate.app",
                "studio.hypertext.logdate.content",
                "entry-1",
                null,
                buildJsonObject { put("content", "hello") },
                null,
                null,
                104,
                null,
            )
        val deleteRequest =
            instantiatePrivateDefault(
                "app.logdate.server.routes.DeleteRecordRequest",
                arrayOf<Class<*>>(
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    defaultConstructorMarkerClass,
                ),
                "did:web:alice.logdate.app",
                "studio.hypertext.logdate.content",
                "entry-1",
                null,
                null,
                24,
                null,
            )

        assertEquals("error", invokeGetter(errorResponse, "getError"))
        assertEquals("message", invokeGetter(errorResponse, "getMessage"))
        assertEquals("did:web:alice.logdate.app", invokeGetter(resolveHandle, "getDid"))
        assertEquals("did:web:logdate.app", invokeGetter(describeServer, "getDid"))
        assertEquals(listOf("logdate.app"), invokeGetter(describeServer, "getAvailableUserDomains"))
        assertFalse(invokeGetter(describeServer, "getInviteCodeRequired") as Boolean)
        assertFalse(invokeGetter(describeServer, "getPhoneVerificationRequired") as Boolean)
        assertEquals("alice.logdate.app", invokeGetter(describeRepo, "getHandle"))
        assertEquals("did:web:alice.logdate.app", invokeGetter(describeRepo, "getDid"))
        assertEquals(didDocument, invokeGetter(describeRepo, "getDidDoc"))
        assertEquals(listOf("studio.hypertext.logdate.content"), invokeGetter(describeRepo, "getCollections"))
        assertEquals(true, invokeGetter(describeRepo, "getHandleIsCorrect"))
        assertEquals(listOf(repoRecord), invokeGetter(listRecords, "getRecords"))
        assertEquals(null, invokeGetter(listRecords, "getCursor"))
        assertEquals(null, invokeGetter(createRequest, "getValidate"))
        assertEquals(null, invokeGetter(createRequest, "getSwapCommit"))
        assertEquals(null, invokeGetter(putRequest, "getValidate"))
        assertEquals(null, invokeGetter(putRequest, "getSwapCommit"))
        assertEquals(null, invokeGetter(deleteRequest, "getSwapCommit"))
    }

    @Test
    fun `xrpc routes default overload and missing repo store return unsupported`() =
        testApplication {
            val identityService = identityService(InMemoryAccountRepository())
            application {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    xrpcRoutes(identityService)
                }
            }

            val getRecord = client.get("/xrpc/com.atproto.repo.getRecord")
            val listRecords = client.get("/xrpc/com.atproto.repo.listRecords")
            val createRecord =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"repo":"alice.logdate.app","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
                }
            val putRecord =
                client.post("/xrpc/com.atproto.repo.putRecord") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"repo":"alice.logdate.app","collection":"studio.hypertext.logdate.content","rkey":"entry-1","record":{"type":"TEXT"}}""",
                    )
                }
            val deleteRecord =
                client.post("/xrpc/com.atproto.repo.deleteRecord") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"repo":"alice.logdate.app","collection":"studio.hypertext.logdate.content","rkey":"entry-1"}""")
                }

            assertEquals(HttpStatusCode.NotImplemented, getRecord.status)
            assertEquals(HttpStatusCode.NotImplemented, listRecords.status)
            assertEquals(HttpStatusCode.NotImplemented, createRecord.status)
            assertEquals(HttpStatusCode.NotImplemented, putRecord.status)
            assertEquals(HttpStatusCode.NotImplemented, deleteRecord.status)
            assertEquals(
                "Unsupported",
                json
                    .parseToJsonElement(getRecord.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `xrpc routes support dpop oauth auth and reject invalid proofs`() =
        testApplication {
            val store = StubRepoRecordStore()
            val env = configureXrpcApp(repoRecordStore = store)
            val account =
                runBlocking {
                    env.accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "dpop-user",
                            displayName = "DPoP User",
                            createdAt = Clock.System.now(),
                        ),
                    )
                }
            val ensuredAccount = runBlocking { env.identityService.ensureIdentity(account) }
            val clientKey = generateP256KeyPair()
            val thumbprint = env.oauthDpopVerifier.jwkThumbprint(publicJwk(clientKey))
            val accessToken =
                env.oauthAccessTokenService
                    .issueAccessToken(
                        subjectDid = requireNotNull(ensuredAccount.did),
                        clientId = "https://viewer.example.com/client.json",
                        scope = "atproto",
                        keyThumbprint = thumbprint,
                    ).token

            val created =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "DPoP $accessToken")
                    header(
                        "DPoP",
                        createDpopProof(
                            keyPair = clientKey,
                            method = "POST",
                            htu = "http://localhost/xrpc/com.atproto.repo.createRecord",
                            nonce = env.oauthNonceService.currentNonce(),
                            ath = env.oauthDpopVerifier.accessTokenHash(accessToken),
                        ),
                    )
                    setBody(
                        """
                        {
                          "repo": "dpop-user.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-1",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, created.status)

            val missingProof =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "DPoP $accessToken")
                    setBody("""{"repo":"dpop-user.logdate.app","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, missingProof.status)

            val wrongKey =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "DPoP $accessToken")
                    header(
                        "DPoP",
                        createDpopProof(
                            keyPair = generateP256KeyPair(),
                            method = "POST",
                            htu = "http://localhost/xrpc/com.atproto.repo.createRecord",
                            nonce = env.oauthNonceService.currentNonce(),
                            ath = env.oauthDpopVerifier.accessTokenHash(accessToken),
                        ),
                    )
                    setBody("""{"repo":"dpop-user.logdate.app","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, wrongKey.status)

            val wrongNonce =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "DPoP $accessToken")
                    header(
                        "DPoP",
                        createDpopProof(
                            keyPair = clientKey,
                            method = "POST",
                            htu = "http://localhost/xrpc/com.atproto.repo.createRecord",
                            nonce = "wrong-nonce",
                            ath = env.oauthDpopVerifier.accessTokenHash(accessToken),
                        ),
                    )
                    setBody("""{"repo":"dpop-user.logdate.app","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, wrongNonce.status)
            assertEquals(env.oauthNonceService.currentNonce(), wrongNonce.headers["DPoP-Nonce"])

            val missingAccountToken =
                env.oauthAccessTokenService
                    .issueAccessToken(
                        subjectDid = "did:plc:missing-user",
                        clientId = "https://viewer.example.com/client.json",
                        scope = "atproto",
                        keyThumbprint = thumbprint,
                    ).token
            val missingAccount =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "DPoP $missingAccountToken")
                    header(
                        "DPoP",
                        createDpopProof(
                            keyPair = clientKey,
                            method = "POST",
                            htu = "http://localhost/xrpc/com.atproto.repo.createRecord",
                            nonce = env.oauthNonceService.currentNonce(),
                            ath = env.oauthDpopVerifier.accessTokenHash(missingAccountToken),
                        ),
                    )
                    setBody("""{"repo":"dpop-user.logdate.app","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, missingAccount.status)
        }

    @Test
    fun `xrpc routes reject invalid auth schemes and support custom port dpop proofs`() =
        testApplication {
            val store = StubRepoRecordStore()
            val env = configureXrpcApp(repoRecordStore = store)
            val account =
                runBlocking {
                    env.accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "port-user",
                            displayName = "Port User",
                            createdAt = Clock.System.now(),
                        ),
                    )
                }
            val ensuredAccount = runBlocking { env.identityService.ensureIdentity(account) }
            val clientKey = generateP256KeyPair()
            val thumbprint = env.oauthDpopVerifier.jwkThumbprint(publicJwk(clientKey))
            val accessToken =
                env.oauthAccessTokenService
                    .issueAccessToken(
                        subjectDid = requireNotNull(ensuredAccount.did),
                        clientId = "https://viewer.example.com/client.json",
                        scope = "atproto",
                        keyThumbprint = thumbprint,
                    ).token

            val invalidToken =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "DPoP bad-token")
                    setBody("""{"repo":"port-user.logdate.app","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
                }
            val unsupportedScheme =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Basic abc")
                    setBody("""{"repo":"port-user.logdate.app","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
                }
            val customPort =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Host, "localhost:444")
                    header(HttpHeaders.Authorization, "DPoP $accessToken")
                    header(
                        "DPoP",
                        createDpopProof(
                            keyPair = clientKey,
                            method = "POST",
                            htu = "http://localhost:444/xrpc/com.atproto.repo.createRecord",
                            nonce = env.oauthNonceService.currentNonce(),
                            ath = env.oauthDpopVerifier.accessTokenHash(accessToken),
                        ),
                    )
                    setBody(
                        """
                        {
                          "repo": "port-user.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-custom-port",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.Unauthorized, invalidToken.status)
            assertEquals(HttpStatusCode.Unauthorized, unsupportedScheme.status)
            assertEquals(HttpStatusCode.OK, customPort.status)
        }

    @Test
    fun `xrpc routes return unsupported when dpop auth is requested without oauth services`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val identityService = identityService(accountRepository)
            application {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    xrpcRoutes(
                        identityService = identityService,
                        accountRepository = accountRepository,
                        tokenService = JwtTokenService("xrpc-route-coverage-secret"),
                        repoRecordStore = StubRepoRecordStore(),
                    )
                }
            }

            val response =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "DPoP token")
                    header("DPoP", createDpopProof(generateP256KeyPair(), "POST", "http://localhost/xrpc/com.atproto.repo.createRecord"))
                    setBody("""{"repo":"user.logdate.app","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
                }

            assertEquals(HttpStatusCode.NotImplemented, response.status)
            assertTrue(response.bodyAsText().contains("Unsupported"))
        }

    @Test
    fun `xrpc routes validate repo identifiers and missing resources`() =
        testApplication {
            val store = StubRepoRecordStore()
            val env = configureXrpcApp(repoRecordStore = store)
            val account =
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
            val ensuredAccount = runBlocking { env.identityService.ensureIdentity(account) }

            val missingDescribeRepo = client.get("/xrpc/com.atproto.repo.describeRepo")
            val unknownDescribeRepo = client.get("/xrpc/com.atproto.repo.describeRepo?repo=missing.logdate.app")
            val getRecordMissing = client.get("/xrpc/com.atproto.repo.getRecord")
            val getRecordInvalid =
                client.get(
                    "/xrpc/com.atproto.repo.getRecord?repo=alice.logdate.app&collection=bad collection&rkey=entry-1",
                )
            val getRecordMissingValue =
                client.get(
                    "/xrpc/com.atproto.repo.getRecord?repo=alice.logdate.app&collection=studio.hypertext.logdate.content&rkey=entry-1",
                )
            store.getRecordResult = Result.failure(UnsupportedCollectionException("studio.hypertext.logdate.other"))
            val getRecordFailure =
                client.get(
                    "/xrpc/com.atproto.repo.getRecord?repo=alice.logdate.app&collection=studio.hypertext.logdate.content&rkey=entry-1",
                )
            store.getRecordResult =
                Result.success(
                    RepoRecord(
                        uri = AtUri.require("at://${ensuredAccount.did}/studio.hypertext.logdate.content/entry-1"),
                        cid = "bafy-record",
                        value = buildJsonObject { put("content", "hello") },
                    ),
                )
            val getRecordCidMismatch =
                client.get(
                    "/xrpc/com.atproto.repo.getRecord?repo=alice.logdate.app&collection=studio.hypertext.logdate.content&rkey=entry-1&cid=bafy-other",
                )
            val listRecordsMissing = client.get("/xrpc/com.atproto.repo.listRecords")
            val listRecordsInvalid = client.get("/xrpc/com.atproto.repo.listRecords?repo=alice.logdate.app&collection=bad collection")
            store.listRecordsResult = Result.success(RepoListPage(records = emptyList(), cursor = "next"))
            val listRecordsSuccess =
                client.get(
                    "/xrpc/com.atproto.repo.listRecords?repo=alice.logdate.app&collection=studio.hypertext.logdate.content&limit=999&cursor=%20cursor-1%20&reverse=true",
                )

            assertEquals(HttpStatusCode.BadRequest, missingDescribeRepo.status)
            assertEquals(HttpStatusCode.BadRequest, unknownDescribeRepo.status)
            assertEquals(HttpStatusCode.BadRequest, getRecordMissing.status)
            assertEquals(HttpStatusCode.BadRequest, getRecordInvalid.status)
            assertEquals(HttpStatusCode.BadRequest, getRecordMissingValue.status)
            assertEquals(HttpStatusCode.BadRequest, getRecordFailure.status)
            assertEquals(HttpStatusCode.BadRequest, getRecordCidMismatch.status)
            assertEquals(HttpStatusCode.BadRequest, listRecordsMissing.status)
            assertEquals(HttpStatusCode.BadRequest, listRecordsInvalid.status)
            assertEquals(HttpStatusCode.OK, listRecordsSuccess.status)
            assertEquals(
                "RepoNotFound",
                json
                    .parseToJsonElement(unknownDescribeRepo.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "RecordNotFound",
                json
                    .parseToJsonElement(getRecordMissingValue.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "InvalidRequest",
                json
                    .parseToJsonElement(getRecordFailure.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "RecordNotFound",
                json
                    .parseToJsonElement(getRecordCidMismatch.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(100, store.lastListLimit)
            assertEquals("cursor-1", store.lastListCursor)
            assertEquals(true, store.lastListReverse)
        }

    @Test
    fun `xrpc routes surface auth validation repo errors and non atproto describe collections`() =
        testApplication {
            val store = StubRepoRecordStore()
            val env = configureXrpcApp(repoRecordStore = store)
            val account =
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
            val ensuredAccount = runBlocking { env.identityService.ensureIdentity(account) }
            val otherAccount =
                runBlocking {
                    env.accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "bob",
                            displayName = "Bob",
                            createdAt = Clock.System.now(),
                        ),
                    )
                }
            val ensuredOtherAccount = runBlocking { env.identityService.ensureIdentity(otherAccount) }
            val validToken = env.tokenService.generateAccessToken(ensuredAccount.id.toString())
            val missingAccountToken = env.tokenService.generateAccessToken(Uuid.random().toString())

            store.listRecordsResult =
                Result.success(
                    RepoListPage(
                        records = emptyList(),
                        cursor = "next-cursor",
                    ),
                )
            val describeRepo =
                client.get("/xrpc/com.atproto.repo.describeRepo?repo=alice.logdate.app")
            assertEquals(HttpStatusCode.OK, describeRepo.status)
            assertEquals(
                emptyList<String>(),
                json
                    .parseToJsonElement(describeRepo.bodyAsText())
                    .jsonObject["collections"]
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content },
            )

            val invalidBearer =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer not-a-jwt")
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }
            val missingAccount =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $missingAccountToken")
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }
            val wrongOwnerCreate =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $validToken")
                    setBody(
                        """
                        {
                          "repo": "${ensuredOtherAccount.handle}",
                          "collection": "studio.hypertext.logdate.content",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }

            store.createRecordResult = Result.failure(UnsupportedCollectionException("studio.hypertext.logdate.other"))
            val unsupportedCreate =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $validToken")
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }

            store.createRecordResult = Result.failure(IllegalStateException("boom"))
            val genericCreate =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $validToken")
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-1",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }

            store.putRecordResult =
                Result.success(
                    RepoWriteResult(
                        uri = AtUri.require("at://${ensuredAccount.did}/studio.hypertext.logdate.content/entry-1"),
                        cid = "bafy-put",
                        validationStatus = RepoValidationStatus.UNKNOWN,
                    ),
                )
            val successfulPut =
                client.post("/xrpc/com.atproto.repo.putRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $validToken")
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-1",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }
            store.putRecordResult = Result.failure(IllegalStateException("boom"))
            val failingPut =
                client.post("/xrpc/com.atproto.repo.putRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $validToken")
                    setBody(
                        """
                        {
                          "repo": "${ensuredAccount.did}",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-1",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }

            store.deleteRecordResult = Result.failure(IllegalStateException("boom"))
            val failingDelete =
                client.post("/xrpc/com.atproto.repo.deleteRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $validToken")
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-1"
                        }
                        """.trimIndent(),
                    )
                }
            val wrongOwnerDelete =
                client.post("/xrpc/com.atproto.repo.deleteRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $validToken")
                    setBody(
                        """
                        {
                          "repo": "${ensuredOtherAccount.handle}",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "entry-1"
                        }
                        """.trimIndent(),
                    )
                }

            val invalidCreateRequest =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $validToken")
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "bad key!",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }
            val invalidPutRequest =
                client.post("/xrpc/com.atproto.repo.putRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $validToken")
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "bad collection",
                          "rkey": "entry-1",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }
            val invalidDeleteRequest =
                client.post("/xrpc/com.atproto.repo.deleteRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $validToken")
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "rkey": "bad key!"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.Unauthorized, invalidBearer.status)
            assertEquals(HttpStatusCode.Unauthorized, missingAccount.status)
            assertEquals(HttpStatusCode.Forbidden, wrongOwnerCreate.status)
            assertEquals(HttpStatusCode.BadRequest, unsupportedCreate.status)
            assertEquals(HttpStatusCode.BadRequest, genericCreate.status)
            assertEquals(HttpStatusCode.OK, successfulPut.status)
            assertEquals(HttpStatusCode.BadRequest, failingPut.status)
            assertEquals(HttpStatusCode.BadRequest, failingDelete.status)
            assertEquals(HttpStatusCode.Forbidden, wrongOwnerDelete.status)
            assertEquals(HttpStatusCode.BadRequest, invalidCreateRequest.status)
            assertEquals(HttpStatusCode.BadRequest, invalidPutRequest.status)
            assertEquals(HttpStatusCode.BadRequest, invalidDeleteRequest.status)
            assertEquals(
                "AuthRequired",
                json
                    .parseToJsonElement(invalidBearer.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "AuthRequired",
                json
                    .parseToJsonElement(missingAccount.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "InvalidRequest",
                json
                    .parseToJsonElement(unsupportedCreate.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "InvalidRequest",
                json
                    .parseToJsonElement(genericCreate.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "bafy-put",
                json
                    .parseToJsonElement(successfulPut.bodyAsText())
                    .jsonObject["cid"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "InvalidRequest",
                json
                    .parseToJsonElement(failingPut.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "InvalidRequest",
                json
                    .parseToJsonElement(failingDelete.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `xrpc routes require auth wiring when repo store is present`() =
        testApplication {
            configureXrpcApp(
                repoRecordStore = StubRepoRecordStore(),
                accountRepository = null,
                tokenService = null,
            )

            val createResponse =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "repo": "alice.logdate.app",
                          "collection": "studio.hypertext.logdate.content",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }
            val putResponse =
                client.post("/xrpc/com.atproto.repo.putRecord") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"repo":"alice.logdate.app","collection":"studio.hypertext.logdate.content","rkey":"entry-1","record":{"type":"TEXT"}}""",
                    )
                }
            val deleteResponse =
                client.post("/xrpc/com.atproto.repo.deleteRecord") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"repo":"alice.logdate.app","collection":"studio.hypertext.logdate.content","rkey":"entry-1"}""")
                }

            assertEquals(HttpStatusCode.NotImplemented, createResponse.status)
            assertEquals(HttpStatusCode.NotImplemented, putResponse.status)
            assertEquals(HttpStatusCode.NotImplemented, deleteResponse.status)
            assertEquals(
                "Unsupported",
                json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "Unsupported",
                json
                    .parseToJsonElement(putResponse.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "Unsupported",
                json
                    .parseToJsonElement(deleteResponse.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `parseRecordId reflective coverage exercises success and unknown repo paths`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = YieldingAccountRepository()
            val identityService = identityService(accountRepository)
            val account =
                identityService.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "alice",
                            displayName = "Alice",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )
            val parseRecordIdMethod =
                Class
                    .forName("app.logdate.server.routes.XrpcRoutesKt")
                    .getDeclaredMethod(
                        "parseRecordId",
                        AtprotoIdentityService::class.java,
                        String::class.java,
                        String::class.java,
                        String::class.java,
                        Continuation::class.java,
                    ).apply { isAccessible = true }

            val resolvedRecordId =
                invokeSuspendingMethod(
                    target = null,
                    method = parseRecordIdMethod,
                    identityService,
                    account.handle!!,
                    "studio.hypertext.logdate.content",
                    "entry-1",
                ) as RepoRecordId?
            val missingRecordId =
                invokeSuspendingMethod(
                    target = null,
                    method = parseRecordIdMethod,
                    identityService,
                    "missing.logdate.app",
                    "studio.hypertext.logdate.content",
                    "entry-1",
                ) as RepoRecordId?

            assertEquals(account.did, resolvedRecordId?.repo?.toString())
            assertEquals("entry-1", resolvedRecordId?.recordKey.toString())
            assertEquals(null, missingRecordId)
        }

    private fun instantiatePrivate(
        className: String,
        vararg args: Any,
    ): Any {
        val clazz = Class.forName(className)
        val ctor = clazz.declaredConstructors.single { it.parameterCount == args.size }
        ctor.isAccessible = true
        return ctor.newInstance(*args)
    }

    private fun instantiatePrivateDefault(
        className: String,
        parameterTypes: Array<out Class<*>>,
        vararg args: Any?,
    ): Any {
        val clazz = Class.forName(className)
        val ctor = clazz.getDeclaredConstructor(*parameterTypes)
        ctor.isAccessible = true
        return ctor.newInstance(*args)
    }

    private fun invokeGetter(
        target: Any,
        getter: String,
    ): Any? = target::class.java.getMethod(getter).invoke(target)

    private suspend fun invokeSuspendingMethod(
        target: Any?,
        method: java.lang.reflect.Method,
        vararg args: Any?,
    ): Any? =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            val result = method.invoke(target, *args, continuation)
            if (result === COROUTINE_SUSPENDED) {
                COROUTINE_SUSPENDED
            } else {
                result
            }
        }

    private fun TestApplicationBuilder.configureXrpcApp(
        repoRecordStore: RepoRecordStore,
        accountRepository: AccountRepository? = InMemoryAccountRepository(),
        tokenService: JwtTokenService? = JwtTokenService("xrpc-route-coverage-secret"),
    ): XrpcCoverageEnvironment {
        val concreteAccountRepository = accountRepository ?: InMemoryAccountRepository()
        val identityService = identityService(concreteAccountRepository)
        val oauthKeyService = OAuthKeyService()
        val oauthNonceService = OAuthNonceService()
        val oauthDpopVerifier = OAuthDpopVerifier()
        val oauthAccessTokenService = OAuthAccessTokenService(OAuthConfig(issuer = "https://logdate.app"), oauthKeyService)

        application {
            install(ContentNegotiation) {
                json(json)
            }
            routing {
                xrpcRoutes(
                    identityService = identityService,
                    accountRepository = accountRepository,
                    tokenService = tokenService,
                    repoRecordStore = repoRecordStore,
                    oauthAccessTokenService = oauthAccessTokenService,
                    oauthDpopVerifier = oauthDpopVerifier,
                    oauthNonceService = oauthNonceService,
                )
            }
        }

        return XrpcCoverageEnvironment(
            identityService = identityService,
            accountRepository = concreteAccountRepository,
            tokenService = tokenService ?: JwtTokenService("unused-secret"),
            oauthNonceService = oauthNonceService,
            oauthDpopVerifier = oauthDpopVerifier,
            oauthAccessTokenService = oauthAccessTokenService,
        )
    }

    private fun identityService(accountRepository: AccountRepository): AtprotoIdentityService =
        AtprotoIdentityService(
            accountRepository = accountRepository,
            signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
            config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
        )

    private class YieldingAccountRepository(
        private val delegate: InMemoryAccountRepository = InMemoryAccountRepository(),
    ) : AccountRepository by delegate {
        override suspend fun findByDid(did: String): Account? {
            kotlinx.coroutines.yield()
            return delegate.findByDid(did)
        }

        override suspend fun findByHandle(handle: String): Account? {
            kotlinx.coroutines.yield()
            return delegate.findByHandle(handle)
        }
    }

    private data class XrpcCoverageEnvironment(
        val identityService: AtprotoIdentityService,
        val accountRepository: AccountRepository,
        val tokenService: JwtTokenService,
        val oauthNonceService: OAuthNonceService,
        val oauthDpopVerifier: OAuthDpopVerifier,
        val oauthAccessTokenService: OAuthAccessTokenService,
    )

    private class StubRepoRecordStore : RepoRecordStore {
        var getRecordResult: Result<RepoRecord?> = Result.success(null)
        var listRecordsResult: Result<RepoListPage> = Result.success(RepoListPage(emptyList()))
        var createRecordResult: Result<RepoWriteResult> =
            Result.success(
                RepoWriteResult(
                    uri = AtUri.require("at://did:web:alice.logdate.app/studio.hypertext.logdate.content/entry-1"),
                    cid = "bafy-create",
                    validationStatus = RepoValidationStatus.UNKNOWN,
                ),
            )
        var putRecordResult: Result<RepoWriteResult> =
            Result.success(
                RepoWriteResult(
                    uri = AtUri.require("at://did:web:alice.logdate.app/studio.hypertext.logdate.content/entry-1"),
                    cid = "bafy-put",
                    validationStatus = RepoValidationStatus.UNKNOWN,
                ),
            )
        var deleteRecordResult: Result<Boolean> = Result.success(true)
        var lastListLimit: Int? = null
        var lastListCursor: String? = null
        var lastListReverse: Boolean? = null

        override suspend fun getRecord(recordId: RepoRecordId): Result<RepoRecord?> = getRecordResult

        override suspend fun listRecords(
            repo: AtprotoDid,
            collection: Nsid,
            limit: Int,
            cursor: String?,
            reverse: Boolean,
        ): Result<RepoListPage> {
            lastListLimit = limit
            lastListCursor = cursor
            lastListReverse = reverse
            return listRecordsResult
        }

        override suspend fun createRecord(
            repo: AtprotoDid,
            collection: Nsid,
            value: JsonObject,
            recordKey: RecordKey?,
        ): Result<RepoWriteResult> = createRecordResult

        override suspend fun putRecord(
            recordId: RepoRecordId,
            value: JsonObject,
            swapRecord: String?,
        ): Result<RepoWriteResult> = putRecordResult

        override suspend fun deleteRecord(
            recordId: RepoRecordId,
            swapRecord: String?,
        ): Result<Boolean> = deleteRecordResult
    }
}
