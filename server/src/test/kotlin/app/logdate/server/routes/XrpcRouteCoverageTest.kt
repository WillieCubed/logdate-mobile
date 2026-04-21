package app.logdate.server.routes

import app.logdate.server.atproto.AtprotoSessionTokenService
import app.logdate.server.atproto.InMemoryAtprotoSessionRepository
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
import studio.hypertext.atproto.pds.CreateAccountRequest
import studio.hypertext.atproto.pds.CreateRecordRequest
import studio.hypertext.atproto.pds.CreateSessionRequest
import studio.hypertext.atproto.pds.DeleteRecordRequest
import studio.hypertext.atproto.pds.DescribeRepoResponse
import studio.hypertext.atproto.pds.DescribeServerResponse
import studio.hypertext.atproto.pds.EmptyPdsResponse
import studio.hypertext.atproto.pds.GetLatestCommitRequest
import studio.hypertext.atproto.pds.GetLatestCommitResponse
import studio.hypertext.atproto.pds.GetRepoRequest
import studio.hypertext.atproto.pds.GetRepoStatusRequest
import studio.hypertext.atproto.pds.GetRepoStatusResponse
import studio.hypertext.atproto.pds.ListRecordsResponse
import studio.hypertext.atproto.pds.PdsErrorResponse
import studio.hypertext.atproto.pds.PdsSessionService
import studio.hypertext.atproto.pds.PdsSyncService
import studio.hypertext.atproto.pds.PutRecordRequest
import studio.hypertext.atproto.pds.RepoExportResponse
import studio.hypertext.atproto.pds.ResolveHandleResponse
import studio.hypertext.atproto.pds.SessionInfoResponse
import studio.hypertext.atproto.pds.SessionResponse
import studio.hypertext.atproto.pds.runtime.DefaultPdsRepoService
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.RepoEngine
import studio.hypertext.atproto.repo.RepoExport
import studio.hypertext.atproto.repo.RepoHead
import studio.hypertext.atproto.repo.RepoListPage
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.repo.RepoValidationStatus
import studio.hypertext.atproto.repo.RepoWriteResult
import studio.hypertext.atproto.repo.SignedRepoCommit
import studio.hypertext.atproto.repo.UnsupportedCollectionException
import studio.hypertext.atproto.syntax.AtUri
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A comprehensive coverage suite for XRPC routing and model behavior.
 *
 * This class verifies the structural integrity of AT Protocol DTOs and ensures that XRPC
 * routes correctly handle multiple authentication schemes, including OAuth with DPoP. It
 * also tests the proxying logic for core AT Protocol services such as session management,
 * handle resolution, and repository synchronization.
 */
@OptIn(ExperimentalUuidApi::class)
class XrpcRouteCoverageTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `xrpc route models expose shared contract fields and defaults`() {
        val didDocument = DidDocument(id = AtprotoDid.require("did:web:alice.logdate.app"))
        val collection = Nsid.require("studio.hypertext.logdate.content")
        val repoRecord =
            RepoRecord(
                uri = AtUri.require("at://did:web:alice.logdate.app/studio.hypertext.logdate.content/entry-1"),
                cid = "bafy-record",
                value = buildJsonObject { put("content", "hello") },
            )
        val errorResponse = PdsErrorResponse("error", "message")
        val resolveHandle = ResolveHandleResponse(AtprotoDid.require("did:web:alice.logdate.app"))
        val describeServer =
            DescribeServerResponse(
                did = "did:web:logdate.app",
                availableUserDomains = listOf("logdate.app"),
                inviteCodeRequired = false,
                phoneVerificationRequired = false,
            )
        val describeRepo =
            DescribeRepoResponse(
                handle = "alice.logdate.app",
                did = AtprotoDid.require("did:web:alice.logdate.app"),
                didDoc = didDocument,
                collections = listOf(collection),
                handleIsCorrect = true,
            )
        val listRecords = ListRecordsResponse.fromPage(RepoListPage(records = listOf(repoRecord)))
        val createRequest =
            CreateRecordRequest(
                repo = AtprotoDid.require("did:web:alice.logdate.app"),
                collection = collection,
                record = buildJsonObject { put("content", "hello") },
            )
        val putRequest =
            PutRecordRequest(
                repo = AtprotoDid.require("did:web:alice.logdate.app"),
                collection = collection,
                recordKey = RecordKey.require("entry-1"),
                record = buildJsonObject { put("content", "hello") },
            )
        val deleteRequest =
            DeleteRecordRequest(
                repo = AtprotoDid.require("did:web:alice.logdate.app"),
                collection = collection,
                recordKey = RecordKey.require("entry-1"),
            )
        val emptyResponse = EmptyPdsResponse()

        assertEquals("error", errorResponse.error)
        assertEquals("message", errorResponse.message)
        assertEquals("did:web:alice.logdate.app", resolveHandle.did.toString())
        assertEquals("did:web:logdate.app", describeServer.did)
        assertEquals(listOf("logdate.app"), describeServer.availableUserDomains)
        assertFalse(describeServer.inviteCodeRequired)
        assertFalse(describeServer.phoneVerificationRequired)
        assertEquals("alice.logdate.app", describeRepo.handle)
        assertEquals("did:web:alice.logdate.app", describeRepo.did.toString())
        assertEquals(didDocument, describeRepo.didDoc)
        assertEquals(listOf(collection), describeRepo.collections)
        assertTrue(describeRepo.handleIsCorrect)
        assertEquals(listOf(repoRecord), listRecords.records)
        assertEquals(null, listRecords.cursor)
        assertEquals(null, createRequest.validate)
        assertEquals(null, createRequest.swapCommit)
        assertEquals(null, putRequest.validate)
        assertEquals(null, putRequest.swapCommit)
        assertEquals(null, deleteRequest.swapCommit)
        assertNotNull(emptyResponse)
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
            val repoDid = requireNotNull(ensuredAccount.did)
            val clientKey = generateP256KeyPair()
            val thumbprint = env.oauthDpopVerifier.jwkThumbprint(publicJwk(clientKey))
            val accessToken =
                env.oauthAccessTokenService
                    .issueAccessToken(
                        subjectDid = repoDid,
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
                          "repo": "$repoDid",
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
                    setBody("""{"repo":"$repoDid","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
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
                    setBody("""{"repo":"$repoDid","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
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
                    setBody("""{"repo":"$repoDid","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
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
                    setBody("""{"repo":"$repoDid","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
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
            val repoDid = requireNotNull(ensuredAccount.did)
            val clientKey = generateP256KeyPair()
            val thumbprint = env.oauthDpopVerifier.jwkThumbprint(publicJwk(clientKey))
            val accessToken =
                env.oauthAccessTokenService
                    .issueAccessToken(
                        subjectDid = repoDid,
                        clientId = "https://viewer.example.com/client.json",
                        scope = "atproto",
                        keyThumbprint = thumbprint,
                    ).token

            val invalidToken =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "DPoP bad-token")
                    setBody("""{"repo":"$repoDid","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
                }
            val unsupportedScheme =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Basic abc")
                    setBody("""{"repo":"$repoDid","collection":"studio.hypertext.logdate.content","record":{"type":"TEXT"}}""")
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
                          "repo": "$repoDid",
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
                        repoService = DefaultPdsRepoService(StubRepoRecordStore()),
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
            val repoDid = requireNotNull(ensuredAccount.did)
            val otherRepoDid = requireNotNull(ensuredOtherAccount.did)
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
                          "repo": "$repoDid",
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
                          "repo": "$repoDid",
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
                          "repo": "$otherRepoDid",
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
                          "repo": "$repoDid",
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
                          "repo": "$repoDid",
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
                          "repo": "$repoDid",
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
                          "repo": "$repoDid",
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
                          "repo": "$otherRepoDid",
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
                          "repo": "$repoDid",
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
                          "repo": "$repoDid",
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
                          "repo": "$repoDid",
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

    @Test
    fun `xrpc routes proxy standard session endpoints`() =
        testApplication {
            val sessionService = StubSessionService()
            configureXrpcApp(
                repoRecordStore = StubRepoRecordStore(),
                sessionService = sessionService,
            )

            val createdAccount =
                client.post("/xrpc/com.atproto.server.createAccount") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "email": "alice@example.com",
                          "handle": "alice.logdate.app",
                          "password": "pass1"
                        }
                        """.trimIndent(),
                    )
                }
            val createdSession =
                client.post("/xrpc/com.atproto.server.createSession") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "identifier": "alice@example.com",
                          "password": "pass1"
                        }
                        """.trimIndent(),
                    )
                }
            val fetchedSession =
                client.get("/xrpc/com.atproto.server.getSession") {
                    header(HttpHeaders.Authorization, "Bearer hosted-access")
                }
            val refreshedSession =
                client.post("/xrpc/com.atproto.server.refreshSession") {
                    header(HttpHeaders.Authorization, "Bearer hosted-refresh")
                }
            val deletedSession =
                client.post("/xrpc/com.atproto.server.deleteSession") {
                    header(HttpHeaders.Authorization, "Bearer hosted-refresh")
                }

            assertEquals(HttpStatusCode.OK, createdAccount.status)
            assertEquals(HttpStatusCode.OK, createdSession.status)
            assertEquals(HttpStatusCode.OK, fetchedSession.status)
            assertEquals(HttpStatusCode.OK, refreshedSession.status)
            assertEquals(HttpStatusCode.OK, deletedSession.status)
            assertEquals("alice.logdate.app", sessionService.lastCreateAccountRequest?.handle)
            assertEquals("alice@example.com", sessionService.lastCreateSessionRequest?.identifier)
            assertEquals("hosted-access", sessionService.lastAccessToken)
            assertEquals("hosted-refresh", sessionService.lastRefreshToken)
            assertEquals("hosted-refresh", sessionService.deletedRefreshToken)
        }

    @Test
    fun `xrpc routes proxy standard sync export endpoints`() =
        testApplication {
            val syncService = StubSyncService()
            configureXrpcApp(
                repoRecordStore = StubRepoRecordStore(),
                syncService = syncService,
            )

            val repoResponse =
                client.get("/xrpc/com.atproto.sync.getRepo?did=did:web:alice.logdate.app&since=${Tid.fromLong(5L)}")
            val latestCommit = client.get("/xrpc/com.atproto.sync.getLatestCommit?did=did:web:alice.logdate.app")
            val repoStatus = client.get("/xrpc/com.atproto.sync.getRepoStatus?did=did:web:alice.logdate.app")

            assertEquals(HttpStatusCode.OK, repoResponse.status)
            assertEquals("application/vnd.ipld.car", repoResponse.contentType().toString())
            assertEquals("car-bytes", repoResponse.bodyAsText())
            assertEquals("did:web:alice.logdate.app", syncService.lastGetRepoRequest?.did.toString())
            assertEquals(5L, syncService.lastGetRepoRequest?.since?.toLong())
            assertEquals("did:web:alice.logdate.app", syncService.lastGetLatestCommitRequest?.did.toString())
            assertEquals("did:web:alice.logdate.app", syncService.lastGetRepoStatusRequest?.did.toString())
            assertEquals(
                "bafyreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku",
                json
                    .parseToJsonElement(latestCommit.bodyAsText())
                    .jsonObject["cid"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                Tid.fromLong(9L).toString(),
                json
                    .parseToJsonElement(repoStatus.bodyAsText())
                    .jsonObject["rev"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `xrpc repo routes accept hosted atproto session bearer tokens`() =
        testApplication {
            val store = StubRepoRecordStore()
            val atprotoSessionTokenService = AtprotoSessionTokenService(InMemoryAtprotoSessionRepository(), secret = "test")
            val env =
                configureXrpcApp(
                    repoRecordStore = store,
                    tokenService = null,
                    atprotoSessionTokenService = atprotoSessionTokenService,
                )
            val account =
                runBlocking {
                    env.accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "session-user",
                            displayName = "Session User",
                            createdAt = Clock.System.now(),
                        ),
                    )
                }
            val ensuredAccount = runBlocking { env.identityService.ensureIdentity(account) }
            val session = runBlocking { atprotoSessionTokenService.createSession(ensuredAccount) }

            val created =
                client.post("/xrpc/com.atproto.repo.createRecord") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${session.accessJwt}")
                    setBody(
                        """
                        {
                          "repo": "${ensuredAccount.did}",
                          "collection": "studio.hypertext.logdate.content",
                          "record": { "type": "TEXT" }
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.OK, created.status)
            assertEquals(
                "bafy-create",
                json
                    .parseToJsonElement(created.bodyAsText())
                    .jsonObject["cid"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }

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
        repoRecordStore: RepoEngine,
        accountRepository: AccountRepository? = InMemoryAccountRepository(),
        tokenService: JwtTokenService? = JwtTokenService("xrpc-route-coverage-secret"),
        sessionService: PdsSessionService? = null,
        syncService: PdsSyncService? = null,
        atprotoSessionTokenService: AtprotoSessionTokenService? = null,
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
                    repoService = DefaultPdsRepoService(repoRecordStore),
                    sessionService = sessionService,
                    syncService = syncService,
                    atprotoSessionTokenService = atprotoSessionTokenService,
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

    private class StubSessionService : PdsSessionService {
        var lastCreateAccountRequest: CreateAccountRequest? = null
        var lastCreateSessionRequest: CreateSessionRequest? = null
        var lastAccessToken: String? = null
        var lastRefreshToken: String? = null
        var deletedRefreshToken: String? = null

        private val sessionInfo =
            SessionInfoResponse(
                handle = "alice.logdate.app",
                did = AtprotoDid.require("did:web:alice.logdate.app"),
                active = true,
            )
        private val sessionResponse =
            SessionResponse(
                accessJwt = "hosted-access",
                refreshJwt = "hosted-refresh",
                handle = "alice.logdate.app",
                did = sessionInfo.did,
                active = true,
            )

        override suspend fun createAccount(request: CreateAccountRequest): Result<SessionResponse> {
            lastCreateAccountRequest = request
            return Result.success(sessionResponse)
        }

        override suspend fun createSession(request: CreateSessionRequest): Result<SessionResponse> {
            lastCreateSessionRequest = request
            return Result.success(sessionResponse)
        }

        override suspend fun getSession(accessJwt: String): Result<SessionInfoResponse> {
            lastAccessToken = accessJwt
            return Result.success(sessionInfo)
        }

        override suspend fun refreshSession(refreshJwt: String): Result<SessionResponse> {
            lastRefreshToken = refreshJwt
            return Result.success(sessionResponse)
        }

        override suspend fun deleteSession(refreshJwt: String): Result<Unit> {
            deletedRefreshToken = refreshJwt
            return Result.success(Unit)
        }
    }

    private class StubSyncService : PdsSyncService {
        var lastGetRepoRequest: GetRepoRequest? = null
        var lastGetLatestCommitRequest: GetLatestCommitRequest? = null
        var lastGetRepoStatusRequest: GetRepoStatusRequest? = null

        override suspend fun getRepo(request: GetRepoRequest): Result<RepoExportResponse?> {
            lastGetRepoRequest = request
            return Result.success(
                RepoExportResponse(
                    contentType = "application/vnd.ipld.car",
                    bytes = "car-bytes".encodeToByteArray(),
                ),
            )
        }

        override suspend fun getLatestCommit(request: GetLatestCommitRequest): Result<GetLatestCommitResponse?> {
            lastGetLatestCommitRequest = request
            return Result.success(
                GetLatestCommitResponse(
                    cid = Cid.require("bafyreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku"),
                    rev = Tid.fromLong(9L),
                ),
            )
        }

        override suspend fun getRepoStatus(request: GetRepoStatusRequest): Result<GetRepoStatusResponse?> {
            lastGetRepoStatusRequest = request
            return Result.success(
                GetRepoStatusResponse(
                    did = request.did,
                    active = true,
                    rev = Tid.fromLong(9L),
                ),
            )
        }
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

    private class StubRepoRecordStore : RepoEngine {
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
        var loadHeadResult: Result<RepoHead?> = Result.success(null)
        var listCommitsResult: Result<List<SignedRepoCommit>> = Result.success(emptyList())
        var exportResult: Result<RepoExport> =
            Result.success(
                RepoExport(
                    repo = DEFAULT_REPO,
                    head = DEFAULT_HEAD,
                    commits = emptyList(),
                    blocks = emptyList(),
                ),
            )
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

        override suspend fun loadHead(repo: AtprotoDid): Result<RepoHead?> = loadHeadResult

        override suspend fun listCommits(
            repo: AtprotoDid,
            limit: Int,
        ): Result<List<SignedRepoCommit>> = listCommitsResult

        override suspend fun export(
            repo: AtprotoDid,
            since: Tid?,
        ): Result<RepoExport> = exportResult

        override suspend fun import(export: RepoExport): Result<RepoHead> = Result.success(export.head)

        private companion object {
            private val DEFAULT_REPO = AtprotoDid.require("did:web:alice.logdate.app")
            private val DEFAULT_CID = Cid.sha256(codec = 0x71, bytes = "head".toByteArray())
            private val DEFAULT_HEAD =
                RepoHead(
                    repo = DEFAULT_REPO,
                    root = DEFAULT_CID,
                    commitCid = DEFAULT_CID,
                    revision = 1L,
                )
        }
    }
}
