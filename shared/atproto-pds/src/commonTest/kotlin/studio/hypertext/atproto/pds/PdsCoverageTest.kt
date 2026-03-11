package studio.hypertext.atproto.pds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.repo.RepoListPage
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.syntax.AtUri
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdsCoverageTest {
    private val json = Json { encodeDefaults = true }
    private val repoDid = AtprotoDid.require("did:web:alice.logdate.app")
    private val didDocument = DidDocument(id = repoDid)
    private val collection = Nsid.require("com.atproto.repo.createRecord")
    private val recordKey = RecordKey.require("entry-1")
    private val record =
        RepoRecord(
            uri = AtUri.require("at://did:web:alice.logdate.app/com.atproto.repo.createRecord/entry-1"),
            cid = "bafyreicid",
            value = buildJsonObject { put("text", "hello") },
        )

    @Test
    fun `discovery and oauth models round trip through serialization`() {
        val oauthTokenUrl = "https://logdate.app/oauth/token"
        val shortRefreshValue = "r1"
        val shortAccessValue = "a1"
        val authorizationServerMetadata =
            AuthorizationServerMetadata(
                issuer = "https://logdate.app",
                authorization_endpoint = "https://logdate.app/oauth/authorize",
                token_endpoint = oauthTokenUrl,
                pushed_authorization_request_endpoint = "https://logdate.app/oauth/par",
                revocation_endpoint = "https://logdate.app/oauth/revoke",
                jwks_uri = "https://logdate.app/oauth/jwks",
                response_types_supported = listOf("code"),
                grant_types_supported = listOf("authorization_code", "refresh_token"),
                code_challenge_methods_supported = listOf("S256"),
                token_endpoint_auth_methods_supported = listOf("none", "private_key_jwt"),
                token_endpoint_auth_signing_alg_values_supported = listOf("ES256"),
                dpop_signing_alg_values_supported = listOf("ES256"),
                scopes_supported = listOf("atproto"),
                authorization_response_iss_parameter_supported = true,
                require_pushed_authorization_requests = true,
                client_id_metadata_document_supported = true,
            )
        val protectedResourceMetadata =
            ProtectedResourceMetadata(
                resource = "https://pds.logdate.app",
                authorization_servers = listOf("https://logdate.app"),
            )
        val resolveHandleResponse = ResolveHandleResponse(repoDid)
        val describeServerResponse =
            DescribeServerResponse(
                did = "did:web:logdate.app",
                availableUserDomains = listOf("logdate.app"),
                inviteCodeRequired = false,
                phoneVerificationRequired = false,
            )
        val describeRepoResponse =
            DescribeRepoResponse(
                handle = "alice.logdate.app",
                did = repoDid,
                didDoc = didDocument,
                collections = listOf(collection),
                handleIsCorrect = true,
            )
        val pushedAuthorizationRequest =
            PushedAuthorizationRequest(
                clientId = "https://viewer.example.com/client.json",
                redirectUri = "https://viewer.example.com/callback",
                scope = "atproto transition:generic",
                responseType = "code",
                codeChallenge = "challenge",
                codeChallengeMethod = "S256",
                state = "state-123",
                loginHint = "alice.logdate.app",
                dpopProof = "dpop-proof",
                htu = "https://logdate.app/oauth/par",
            )
        val authorizationPrompt =
            AuthorizationPrompt(
                requestUri = "urn:ietf:params:oauth:request_uri:test",
                clientId = "https://viewer.example.com/client.json",
                clientName = "Viewer",
                redirectUri = "https://viewer.example.com/callback",
                scope = "atproto",
                state = "state-123",
                loginHint = "alice.logdate.app",
            )
        val authorizationDecisionRequest =
            AuthorizationDecisionRequest(
                requestUri = "urn:ietf:params:oauth:request_uri:test",
                subjectDid = "did:plc:alice123",
                subjectHandle = "alice.logdate.app",
                approved = true,
            )
        val pushedAuthorizationResponse =
            PushedAuthorizationResponse(
                requestUri = "urn:ietf:params:oauth:request_uri:test",
                expiresInSeconds = 300L,
                dpopNonce = "nonce-1",
            )
        val pushedAuthorizationBody =
            PushedAuthorizationBody(
                requestUri = "urn:ietf:params:oauth:request_uri:test",
                expiresInSeconds = 300L,
            )
        val authorizationCodeTokenRequest =
            AuthorizationCodeTokenRequest(
                code = "code-1",
                redirectUri = "https://viewer.example.com/callback",
                clientId = "https://viewer.example.com/client.json",
                codeVerifier = "verifier",
                dpopProof = "dpop-proof",
                htu = "https://logdate.app/oauth/token",
            )
        val refreshTokenGrantRequest =
            RefreshTokenGrantRequest(
                refreshToken = shortRefreshValue,
                clientId = "https://viewer.example.com/client.json",
                dpopProof = "dpop-proof",
                htu = oauthTokenUrl,
            )
        val oauthRevokeRequest =
            OAuthRevokeRequest(
                refreshToken = shortRefreshValue,
                clientId = "https://viewer.example.com/client.json",
                dpopProof = "dpop-proof",
                htu = "https://logdate.app/oauth/revoke",
            )
        val oauthTokenResponse =
            OAuthTokenResponse(
                access_token = shortAccessValue,
                token_type = "DPoP",
                expires_in = 3600L,
                refresh_token = shortRefreshValue,
                sub = "did:plc:alice123",
                scope = "atproto",
            )
        val authorizationPromptResponse =
            AuthorizationPromptResponse(
                clientId = "https://viewer.example.com/client.json",
                clientName = "Viewer",
                redirectUri = "https://viewer.example.com/callback",
                scope = "atproto",
                state = "state-123",
                loginHint = "alice.logdate.app",
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
            )
        val oauthErrorResponse =
            OAuthErrorResponse(
                error = "invalid_request",
                errorDescription = "Missing value",
            )

        assertEquals(
            authorizationServerMetadata,
            json.decodeFromString(
                AuthorizationServerMetadata.serializer(),
                json.encodeToString(AuthorizationServerMetadata.serializer(), authorizationServerMetadata),
            ),
        )
        assertEquals(
            protectedResourceMetadata,
            json.decodeFromString(
                ProtectedResourceMetadata.serializer(),
                json.encodeToString(ProtectedResourceMetadata.serializer(), protectedResourceMetadata),
            ),
        )
        assertEquals(
            resolveHandleResponse,
            json.decodeFromString(
                ResolveHandleResponse.serializer(),
                json.encodeToString(ResolveHandleResponse.serializer(), resolveHandleResponse),
            ),
        )
        assertEquals(
            describeServerResponse,
            json.decodeFromString(
                DescribeServerResponse.serializer(),
                json.encodeToString(DescribeServerResponse.serializer(), describeServerResponse),
            ),
        )
        assertEquals(
            describeRepoResponse,
            json.decodeFromString(
                DescribeRepoResponse.serializer(),
                json.encodeToString(DescribeRepoResponse.serializer(), describeRepoResponse),
            ),
        )
        assertEquals(
            pushedAuthorizationRequest,
            json.decodeFromString(
                PushedAuthorizationRequest.serializer(),
                json.encodeToString(PushedAuthorizationRequest.serializer(), pushedAuthorizationRequest),
            ),
        )
        assertEquals(
            authorizationPrompt,
            json.decodeFromString(
                AuthorizationPrompt.serializer(),
                json.encodeToString(AuthorizationPrompt.serializer(), authorizationPrompt),
            ),
        )
        assertEquals(
            authorizationDecisionRequest,
            json.decodeFromString(
                AuthorizationDecisionRequest.serializer(),
                json.encodeToString(AuthorizationDecisionRequest.serializer(), authorizationDecisionRequest),
            ),
        )
        assertEquals(
            pushedAuthorizationResponse,
            json.decodeFromString(
                PushedAuthorizationResponse.serializer(),
                json.encodeToString(PushedAuthorizationResponse.serializer(), pushedAuthorizationResponse),
            ),
        )
        assertEquals(
            pushedAuthorizationBody,
            json.decodeFromString(
                PushedAuthorizationBody.serializer(),
                json.encodeToString(PushedAuthorizationBody.serializer(), pushedAuthorizationBody),
            ),
        )
        assertEquals(
            authorizationCodeTokenRequest,
            json.decodeFromString(
                AuthorizationCodeTokenRequest.serializer(),
                json.encodeToString(AuthorizationCodeTokenRequest.serializer(), authorizationCodeTokenRequest),
            ),
        )
        assertEquals(
            refreshTokenGrantRequest,
            json.decodeFromString(
                RefreshTokenGrantRequest.serializer(),
                json.encodeToString(RefreshTokenGrantRequest.serializer(), refreshTokenGrantRequest),
            ),
        )
        assertEquals(
            oauthRevokeRequest,
            json.decodeFromString(
                OAuthRevokeRequest.serializer(),
                json.encodeToString(OAuthRevokeRequest.serializer(), oauthRevokeRequest),
            ),
        )
        assertEquals(
            oauthTokenResponse,
            json.decodeFromString(
                OAuthTokenResponse.serializer(),
                json.encodeToString(OAuthTokenResponse.serializer(), oauthTokenResponse),
            ),
        )
        assertEquals(
            authorizationPromptResponse,
            json.decodeFromString(
                AuthorizationPromptResponse.serializer(),
                json.encodeToString(AuthorizationPromptResponse.serializer(), authorizationPromptResponse),
            ),
        )
        assertEquals(
            oauthErrorResponse,
            json.decodeFromString(
                OAuthErrorResponse.serializer(),
                json.encodeToString(OAuthErrorResponse.serializer(), oauthErrorResponse),
            ),
        )

        assertEquals("https://logdate.app", authorizationServerMetadata.issuer)
        assertEquals("https://pds.logdate.app", protectedResourceMetadata.resource)
        assertEquals(repoDid, resolveHandleResponse.did)
        assertEquals("did:web:logdate.app", describeServerResponse.did)
        assertEquals("alice.logdate.app", describeRepoResponse.handle)
        assertEquals("Viewer", authorizationPrompt.clientName)
        assertEquals(true, authorizationDecisionRequest.approved)
        assertEquals("nonce-1", pushedAuthorizationResponse.dpopNonce)
        assertEquals(300L, pushedAuthorizationBody.expiresInSeconds)
        assertEquals("code-1", authorizationCodeTokenRequest.code)
        assertEquals(shortRefreshValue, refreshTokenGrantRequest.refreshToken)
        assertEquals(shortRefreshValue, oauthRevokeRequest.refreshToken)
        assertEquals(shortAccessValue, oauthTokenResponse.access_token)
        assertEquals("alice.logdate.app", authorizationPromptResponse.handle)
        assertEquals("Missing value", oauthErrorResponse.errorDescription)
    }

    @Test
    fun `repo models and pds exceptions preserve wire shapes defaults and helpers`() {
        val getRecordRequest =
            GetRecordRequest(
                repo = repoDid,
                collection = collection,
                recordKey = recordKey,
                cid = "bafyreicid",
            )
        val listRecordsRequest =
            ListRecordsRequest(
                repo = repoDid,
                collection = collection,
            )
        val createRecordRequest =
            CreateRecordRequest(
                repo = repoDid,
                collection = collection,
                record = buildJsonObject { put("text", "hello") },
                recordKey = recordKey,
            )
        val putRecordRequest =
            PutRecordRequest(
                repo = repoDid,
                collection = collection,
                recordKey = recordKey,
                record = buildJsonObject { put("text", "hello") },
                swapRecord = "bafy-old",
            )
        val deleteRecordRequest =
            DeleteRecordRequest(
                repo = repoDid,
                collection = collection,
                recordKey = recordKey,
            )
        val errorResponse = PdsErrorResponse(error = "InvalidRequest", message = "Missing record")
        val listRecordsResponse = ListRecordsResponse.fromPage(RepoListPage(records = listOf(record), cursor = "next"))
        val emptyResponse = EmptyPdsResponse()
        val invalidRequest = PdsInvalidRequestException("Bad request")
        val unsupported = PdsUnsupportedException("Not enabled")

        val encodedCreateRecord =
            json.encodeToString(CreateRecordRequest.serializer(), createRecordRequest)
        val encodedPutRecord =
            json.encodeToString(PutRecordRequest.serializer(), putRecordRequest)
        val encodedDeleteRecord =
            json.encodeToString(DeleteRecordRequest.serializer(), deleteRecordRequest)

        assertEquals(
            getRecordRequest,
            json.decodeFromString(
                GetRecordRequest.serializer(),
                json.encodeToString(GetRecordRequest.serializer(), getRecordRequest),
            ),
        )
        assertEquals(
            listRecordsRequest,
            json.decodeFromString(
                ListRecordsRequest.serializer(),
                json.encodeToString(ListRecordsRequest.serializer(), listRecordsRequest),
            ),
        )
        assertEquals(
            createRecordRequest,
            json.decodeFromString(
                CreateRecordRequest.serializer(),
                encodedCreateRecord,
            ),
        )
        assertEquals(
            putRecordRequest,
            json.decodeFromString(
                PutRecordRequest.serializer(),
                encodedPutRecord,
            ),
        )
        assertEquals(
            deleteRecordRequest,
            json.decodeFromString(
                DeleteRecordRequest.serializer(),
                encodedDeleteRecord,
            ),
        )
        assertEquals(
            errorResponse,
            json.decodeFromString(
                PdsErrorResponse.serializer(),
                json.encodeToString(PdsErrorResponse.serializer(), errorResponse),
            ),
        )
        assertEquals(
            listRecordsResponse,
            json.decodeFromString(
                ListRecordsResponse.serializer(),
                json.encodeToString(ListRecordsResponse.serializer(), listRecordsResponse),
            ),
        )
        assertEquals(
            "{}",
            json.encodeToString(EmptyPdsResponse.serializer(), emptyResponse),
        )
        assertIs<EmptyPdsResponse>(
            json.decodeFromString(
                EmptyPdsResponse.serializer(),
                json.encodeToString(EmptyPdsResponse.serializer(), emptyResponse),
            ),
        )

        assertEquals(ListRecordsRequest.DEFAULT_PAGE_SIZE, listRecordsRequest.limit)
        assertEquals(null, createRecordRequest.validate)
        assertEquals(null, createRecordRequest.swapCommit)
        assertEquals("bafy-old", putRecordRequest.swapRecord)
        assertEquals(null, deleteRecordRequest.swapRecord)
        assertEquals("InvalidRequest", errorResponse.error)
        assertEquals("next", listRecordsResponse.cursor)
        assertTrue(encodedCreateRecord.contains("\"rkey\":\"entry-1\""))
        assertTrue(encodedPutRecord.contains("\"rkey\":\"entry-1\""))
        assertTrue(encodedDeleteRecord.contains("\"rkey\":\"entry-1\""))
        assertEquals(400, invalidRequest.statusCode)
        assertEquals("invalid_request", invalidRequest.error)
        assertEquals("Bad request", invalidRequest.message)
        assertEquals(501, unsupported.statusCode)
        assertEquals("unsupported", unsupported.error)
        assertEquals("Not enabled", unsupported.message)
    }

    @Test
    fun `repo request defaults remain nullable and serialize cleanly`() {
        val getRecordRequest =
            GetRecordRequest(
                repo = repoDid,
                collection = collection,
                recordKey = recordKey,
            )
        val createRecordRequest =
            CreateRecordRequest(
                repo = repoDid,
                collection = collection,
                record = buildJsonObject { put("text", "hello") },
            )

        val encodedGetRecord = json.encodeToString(GetRecordRequest.serializer(), getRecordRequest)
        val encodedCreateRecord = json.encodeToString(CreateRecordRequest.serializer(), createRecordRequest)

        assertNull(getRecordRequest.cid)
        assertNull(createRecordRequest.recordKey)
        assertTrue(encodedGetRecord.contains("\"recordKey\":\"entry-1\""))
        assertTrue(encodedGetRecord.contains("\"cid\":null"))
        assertTrue(encodedCreateRecord.contains("\"record\":{\"text\":\"hello\"}"))
        assertTrue(encodedCreateRecord.contains("\"rkey\":null"))
        assertTrue(encodedCreateRecord.contains("\"validate\":null"))
    }

    @Test
    fun `session and sync models preserve standard wire fields`() {
        val createAccountRequest =
            CreateAccountRequest(
                email = "alice@example.com",
                handle = "alice.logdate.app",
                password = "pass123",
                recoveryKey = "did:key:zAliceRecovery",
            )
        val createSessionRequest =
            CreateSessionRequest(
                identifier = "alice@example.com",
                password = "pass123",
                allowTakendown = false,
            )
        val sessionInfo =
            SessionInfoResponse(
                handle = "alice.logdate.app",
                did = repoDid,
                didDoc = didDocument,
                email = "alice@example.com",
                emailConfirmed = true,
                active = true,
            )
        val sessionResponse =
            SessionResponse(
                accessJwt = "access-jwt",
                refreshJwt = "refresh-jwt",
                handle = "alice.logdate.app",
                did = repoDid,
                didDoc = didDocument,
                email = "alice@example.com",
                emailConfirmed = true,
                active = true,
            )
        val repoRequest = GetRepoRequest(did = repoDid, since = Tid.fromLong(5L))
        val latestCommitRequest = GetLatestCommitRequest(did = repoDid)
        val latestCommitResponse =
            GetLatestCommitResponse(
                cid =
                    studio.hypertext.atproto.repo.Cid
                        .require("bafyreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku"),
                rev = Tid.fromLong(7L),
            )
        val repoStatusRequest = GetRepoStatusRequest(did = repoDid)
        val repoStatusResponse =
            GetRepoStatusResponse(
                did = repoDid,
                active = true,
                rev = Tid.fromLong(7L),
            )

        assertEquals(
            createAccountRequest,
            json.decodeFromString(
                CreateAccountRequest.serializer(),
                json.encodeToString(CreateAccountRequest.serializer(), createAccountRequest),
            ),
        )
        assertEquals(
            createSessionRequest,
            json.decodeFromString(
                CreateSessionRequest.serializer(),
                json.encodeToString(CreateSessionRequest.serializer(), createSessionRequest),
            ),
        )
        assertEquals(
            sessionInfo,
            json.decodeFromString(
                SessionInfoResponse.serializer(),
                json.encodeToString(SessionInfoResponse.serializer(), sessionInfo),
            ),
        )
        assertEquals(
            sessionResponse,
            json.decodeFromString(
                SessionResponse.serializer(),
                json.encodeToString(SessionResponse.serializer(), sessionResponse),
            ),
        )
        assertEquals(
            repoRequest,
            json.decodeFromString(
                GetRepoRequest.serializer(),
                json.encodeToString(GetRepoRequest.serializer(), repoRequest),
            ),
        )
        assertEquals(
            latestCommitRequest,
            json.decodeFromString(
                GetLatestCommitRequest.serializer(),
                json.encodeToString(GetLatestCommitRequest.serializer(), latestCommitRequest),
            ),
        )
        assertEquals(
            latestCommitResponse,
            json.decodeFromString(
                GetLatestCommitResponse.serializer(),
                json.encodeToString(GetLatestCommitResponse.serializer(), latestCommitResponse),
            ),
        )
        assertEquals(
            repoStatusRequest,
            json.decodeFromString(
                GetRepoStatusRequest.serializer(),
                json.encodeToString(GetRepoStatusRequest.serializer(), repoStatusRequest),
            ),
        )
        assertEquals(
            repoStatusResponse,
            json.decodeFromString(
                GetRepoStatusResponse.serializer(),
                json.encodeToString(GetRepoStatusResponse.serializer(), repoStatusResponse),
            ),
        )
        assertEquals("alice.logdate.app", sessionInfo.handle)
        assertEquals("refresh-jwt", sessionResponse.refreshJwt)
        assertEquals(5L, repoRequest.since?.toLong())
        assertEquals(7L, latestCommitResponse.rev.toLong())
        assertEquals(true, repoStatusResponse.active)
    }
}
