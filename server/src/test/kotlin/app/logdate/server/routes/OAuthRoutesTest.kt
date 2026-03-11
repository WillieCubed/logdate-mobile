package app.logdate.server.routes

import app.logdate.server.AuthV1TestEnvironment
import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.configureAuthV1TestApp
import app.logdate.server.oauth.OAuthAuthorizationService
import app.logdate.server.oauth.OAuthClientMetadataResolver
import app.logdate.server.oauth.OAuthConfig
import app.logdate.server.oauth.OAuthInvalidRequestException
import app.logdate.server.oauth.OAuthKeyService
import app.logdate.server.oauth.OAuthUseDpopNonceException
import app.logdate.server.oauth.clientMetadataJson
import app.logdate.server.oauth.createDpopProof
import app.logdate.server.oauth.generateP256KeyPair
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.pds.AuthorizationDecisionRequest
import studio.hypertext.atproto.pds.AuthorizationPrompt
import studio.hypertext.atproto.pds.AuthorizationPromptResponse
import studio.hypertext.atproto.pds.OAuthErrorResponse
import studio.hypertext.atproto.pds.PushedAuthorizationBody
import studio.hypertext.atproto.pds.PushedAuthorizationRequest
import studio.hypertext.atproto.pds.PushedAuthorizationResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class OAuthRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `oauth discovery routes expose metadata and jwks`() =
        testApplication {
            val config = OAuthConfig(issuer = "https://logdate.app", resource = "https://pds.logdate.app")

            application {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    oauthRoutes(
                        config = config,
                        keyService = OAuthKeyService(),
                    )
                }
            }

            val authorizationServer = client.get("/.well-known/oauth-authorization-server")
            val protectedResource = client.get("/.well-known/oauth-protected-resource")
            val jwks = client.get("/oauth/jwks")

            assertEquals(HttpStatusCode.OK, authorizationServer.status)
            assertEquals(HttpStatusCode.OK, protectedResource.status)
            assertEquals(HttpStatusCode.OK, jwks.status)

            val authorizationPayload = json.parseToJsonElement(authorizationServer.bodyAsText()).jsonObject
            val resourcePayload = json.parseToJsonElement(protectedResource.bodyAsText()).jsonObject
            val jwksPayload = json.parseToJsonElement(jwks.bodyAsText()).jsonObject

            assertEquals("https://logdate.app", authorizationPayload["issuer"]?.jsonPrimitive?.content)
            assertEquals("https://logdate.app/oauth/authorize", authorizationPayload["authorization_endpoint"]?.jsonPrimitive?.content)
            assertEquals("https://logdate.app/oauth/token", authorizationPayload["token_endpoint"]?.jsonPrimitive?.content)
            assertEquals(
                "https://logdate.app/oauth/par",
                authorizationPayload["pushed_authorization_request_endpoint"]?.jsonPrimitive?.content,
            )
            assertEquals("https://logdate.app/oauth/revoke", authorizationPayload["revocation_endpoint"]?.jsonPrimitive?.content)
            assertEquals("https://logdate.app/oauth/jwks", authorizationPayload["jwks_uri"]?.jsonPrimitive?.content)
            assertEquals(
                listOf("none", "private_key_jwt"),
                authorizationPayload["token_endpoint_auth_methods_supported"]
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content },
            )
            assertEquals(
                listOf("ES256", "ES256K"),
                authorizationPayload["token_endpoint_auth_signing_alg_values_supported"]
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content },
            )
            assertEquals("https://pds.logdate.app", resourcePayload["resource"]?.jsonPrimitive?.content)
            assertTrue(jwksPayload["keys"]?.jsonArray?.isNotEmpty() == true)
        }

    @Test
    fun `oauth stateful routes return not implemented without auth services`() =
        testApplication {
            application {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    oauthRoutes(
                        config = OAuthConfig(issuer = "https://logdate.app"),
                        keyService = OAuthKeyService(),
                    )
                }
            }

            val par = client.post("/oauth/par") { setBody(FormDataContent(Parameters.Empty)) }
            val authorize = client.get("/oauth/authorize?request_uri=urn:test")
            val authorizePost = client.post("/oauth/authorize") { setBody(FormDataContent(Parameters.Empty)) }
            val token = client.post("/oauth/token") { setBody(FormDataContent(Parameters.Empty)) }
            val revoke = client.post("/oauth/revoke") { setBody(FormDataContent(Parameters.Empty)) }

            assertEquals(HttpStatusCode.NotImplemented, par.status)
            assertEquals(HttpStatusCode.NotImplemented, authorize.status)
            assertEquals(HttpStatusCode.NotImplemented, authorizePost.status)
            assertEquals(HttpStatusCode.NotImplemented, token.status)
            assertEquals(HttpStatusCode.NotImplemented, revoke.status)
        }

    @Test
    fun `oauth route models expose getters`() {
        val pushed = PushedAuthorizationBody("urn:ietf:params:oauth:request_uri:test", 300L)
        val prompt =
            AuthorizationPromptResponse(
                clientId = "https://viewer.example.com/client.json",
                clientName = "Viewer",
                redirectUri = "https://viewer.example.com/callback",
                scope = "atproto",
                state = "state",
                loginHint = "alice.logdate.app",
                did = "did:plc:alice123",
                handle = "alice.logdate.app",
            )
        val error = OAuthErrorResponse("invalid_request", "Missing value")

        assertEquals("urn:ietf:params:oauth:request_uri:test", pushed.requestUri)
        assertEquals(300L, pushed.expiresInSeconds)
        assertEquals("https://viewer.example.com/client.json", prompt.clientId)
        assertEquals("Viewer", prompt.clientName)
        assertEquals("https://viewer.example.com/callback", prompt.redirectUri)
        assertEquals("atproto", prompt.scope)
        assertEquals("state", prompt.state)
        assertEquals("alice.logdate.app", prompt.loginHint)
        assertEquals("did:plc:alice123", prompt.did)
        assertEquals("alice.logdate.app", prompt.handle)
        assertEquals("invalid_request", error.error)
        assertEquals("Missing value", error.errorDescription)
    }

    @Test
    fun `oauth par authorize token refresh and revoke work end to end`() =
        testApplication {
            val clientId = "https://viewer.example.com/client.json"
            val redirectUri = "https://viewer.example.com/callback"
            val env =
                configureOAuthTestApp(
                    clientId = clientId,
                    redirectUri = redirectUri,
                )
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
            val logDateAccessToken = env.tokenService.generateAccessToken(account.id.toString())
            val clientKeyPair = generateP256KeyPair()

            val par =
                client.post("/oauth/par") {
                    header("DPoP", createDpopProof(clientKeyPair, "POST", "http://localhost/oauth/par"))
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", clientId)
                                append("response_type", "code")
                                append("scope", "atproto")
                                append("redirect_uri", redirectUri)
                                append("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                                append("code_challenge_method", "S256")
                                append("state", "state-123")
                                append("login_hint", "alice.logdate.app")
                            },
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, par.status)
            val parPayload = json.parseToJsonElement(par.bodyAsText()).jsonObject
            val requestUri = parPayload["request_uri"]?.jsonPrimitive?.content
            val nonce = par.headers["DPoP-Nonce"]
            assertNotNull(requestUri)
            assertNotNull(nonce)

            val unauthorizedPrompt = client.get("/oauth/authorize?request_uri=$requestUri")
            assertEquals(HttpStatusCode.Unauthorized, unauthorizedPrompt.status)

            val prompt =
                client.get("/oauth/authorize?request_uri=$requestUri") {
                    header(HttpHeaders.Authorization, "Bearer $logDateAccessToken")
                }
            assertEquals(HttpStatusCode.OK, prompt.status)
            val promptPayload = json.parseToJsonElement(prompt.bodyAsText()).jsonObject
            assertEquals("Journal Viewer", promptPayload["client_name"]?.jsonPrimitive?.content)
            assertEquals("alice.logdate.app", promptPayload["handle"]?.jsonPrimitive?.content)

            val invalidDecision =
                client.post("/oauth/authorize") {
                    header(HttpHeaders.Authorization, "Bearer $logDateAccessToken")
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("request_uri", requestUri)
                                append("decision", "maybe")
                            },
                        ),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, invalidDecision.status)

            val approve =
                client.post("/oauth/authorize") {
                    header(HttpHeaders.Authorization, "Bearer $logDateAccessToken")
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("request_uri", requestUri)
                                append("decision", "approve")
                            },
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Found, approve.status)
            val redirectLocation = approve.headers[HttpHeaders.Location]
            val authorizationCode = redirectLocation?.substringAfter("code=")?.substringBefore('&')
            assertNotNull(authorizationCode)

            val missingDpop =
                client.post("/oauth/token") {
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("grant_type", "authorization_code")
                                append("code", authorizationCode)
                                append("redirect_uri", redirectUri)
                                append("client_id", clientId)
                                append("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
                            },
                        ),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, missingDpop.status)

            val token =
                client.post("/oauth/token") {
                    header("DPoP", createDpopProof(clientKeyPair, "POST", "http://localhost/oauth/token", nonce = nonce))
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("grant_type", "authorization_code")
                                append("code", authorizationCode)
                                append("redirect_uri", redirectUri)
                                append("client_id", clientId)
                                append("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
                            },
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, token.status)
            val tokenPayload = json.parseToJsonElement(token.bodyAsText()).jsonObject
            val accessToken = tokenPayload["access_token"]?.jsonPrimitive?.content
            val refreshToken = tokenPayload["refresh_token"]?.jsonPrimitive?.content
            val refreshedNonce = token.headers["DPoP-Nonce"]
            assertEquals("DPoP", tokenPayload["token_type"]?.jsonPrimitive?.content)
            assertNotNull(accessToken)
            assertNotNull(refreshToken)
            assertNotNull(refreshedNonce)

            val refresh =
                client.post("/oauth/token") {
                    header("DPoP", createDpopProof(clientKeyPair, "POST", "http://localhost/oauth/token", nonce = refreshedNonce))
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("grant_type", "refresh_token")
                                append("refresh_token", refreshToken)
                                append("client_id", clientId)
                            },
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, refresh.status)
            val refreshPayload = json.parseToJsonElement(refresh.bodyAsText()).jsonObject
            val rotatedRefreshToken = refreshPayload["refresh_token"]?.jsonPrimitive?.content
            assertNotNull(rotatedRefreshToken)

            val revoke =
                client.post("/oauth/revoke") {
                    header(
                        "DPoP",
                        createDpopProof(clientKeyPair, "POST", "http://localhost/oauth/revoke", nonce = refresh.headers["DPoP-Nonce"]),
                    )
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("token", rotatedRefreshToken)
                                append("client_id", clientId)
                            },
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, revoke.status)
        }

    @Test
    fun `oauth routes surface server errors from stateful services`() =
        testApplication {
            val authorizationService = mockk<OAuthAuthorizationService>()
            coEvery {
                authorizationService.createPushedAuthorizationRequest(
                    clientId = any(),
                    redirectUri = any(),
                    scope = any(),
                    responseType = any(),
                    codeChallenge = any(),
                    codeChallengeMethod = any(),
                    state = any(),
                    loginHint = any(),
                    clientAssertionType = any(),
                    clientAssertion = any(),
                    dpopProof = any(),
                    htu = any(),
                )
            } throws IllegalStateException("boom")

            application {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    oauthRoutes(
                        config = OAuthConfig(issuer = "https://logdate.app"),
                        keyService = OAuthKeyService(),
                        authorizationService = authorizationService,
                        accountRepository = InMemoryAccountRepository(),
                        tokenService =
                            app.logdate.server.auth
                                .JwtTokenService("test-secret"),
                        identityService =
                            app.logdate.server.identity.AtprotoIdentityService(
                                accountRepository = InMemoryAccountRepository(),
                                signingKeyService =
                                    app.logdate.server.identity.SigningKeyService(
                                        app.logdate.server.identity
                                            .InMemorySigningKeyRepository(),
                                        "test-kek",
                                    ),
                                config =
                                    app.logdate.server.identity.AtprotoIdentityConfig(
                                        handleDomain = "logdate.app",
                                        pdsServiceEndpoint = "https://logdate.app",
                                    ),
                            ),
                    )
                }
            }

            val response =
                client.post("/oauth/par") {
                    header("DPoP", createDpopProof(generateP256KeyPair(), "POST", "http://localhost/oauth/par"))
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", "https://viewer.example.com/client.json")
                                append("response_type", "code")
                                append("scope", "atproto")
                                append("redirect_uri", "https://viewer.example.com/callback")
                                append("code_challenge", "abc")
                                append("code_challenge_method", "S256")
                            },
                        ),
                    )
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(response.bodyAsText().contains("server_error"))
        }

    @Test
    fun `oauth routes cover authorize auth failures and custom port htu`() =
        testApplication {
            val accountRepository = InMemoryAccountRepository()
            val tokenService =
                app.logdate.server.auth
                    .JwtTokenService("oauth-routes-secret")
            val identityService =
                app.logdate.server.identity.AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService =
                        app.logdate.server.identity.SigningKeyService(
                            app.logdate.server.identity
                                .InMemorySigningKeyRepository(),
                            "test-kek",
                        ),
                    config =
                        app.logdate.server.identity.AtprotoIdentityConfig(
                            handleDomain = "logdate.app",
                            pdsServiceEndpoint = "https://logdate.app",
                        ),
                )
            val account =
                runBlocking {
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
                }
            val accessToken = tokenService.generateAccessToken(account.id.toString())
            val authorizationService = mockk<OAuthAuthorizationService>()
            var capturedHtu: String? = null

            io.mockk.every { authorizationService.loadAuthorizationPrompt("urn:bad") } returns
                Result.failure(
                    OAuthInvalidRequestException("Bad request uri"),
                )
            io.mockk.every { authorizationService.loadAuthorizationPrompt("urn:good") } returns
                Result.success(
                    AuthorizationPrompt(
                        requestUri = "urn:good",
                        clientId = "https://viewer.example.com/client.json",
                        clientName = "Viewer",
                        redirectUri = "https://viewer.example.com/callback",
                        scope = "atproto",
                        state = null,
                        loginHint = "alice.logdate.app",
                    ),
                )
            io.mockk.every {
                authorizationService.completeAuthorization(
                    match<AuthorizationDecisionRequest> { request ->
                        request.requestUri == "urn:bad" && request.approved
                    },
                )
            } returns
                Result.failure(
                    OAuthInvalidRequestException("Bad request uri"),
                )
            coEvery {
                authorizationService.createPushedAuthorizationRequest(any())
            } answers {
                capturedHtu = firstArg<PushedAuthorizationRequest>().htu
                Result.success(
                    PushedAuthorizationResponse(
                        requestUri = "urn:good",
                        expiresInSeconds = 300,
                        dpopNonce = "nonce-1",
                    ),
                )
            }

            application {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    oauthRoutes(
                        config = OAuthConfig(issuer = "https://logdate.app"),
                        keyService = OAuthKeyService(),
                        authorizationService = authorizationService,
                        accountRepository = accountRepository,
                        tokenService = tokenService,
                        identityService = identityService,
                    )
                }
            }

            val missingRequestUri =
                client.get("/oauth/authorize") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            val invalidBearer =
                client.get("/oauth/authorize?request_uri=urn:good") {
                    header(HttpHeaders.Authorization, "Bearer bad-token")
                }
            val missingAccount =
                client.get("/oauth/authorize?request_uri=urn:good") {
                    header(HttpHeaders.Authorization, "Bearer ${tokenService.generateAccessToken(Uuid.random().toString())}")
                }
            val invalidPrompt =
                client.get("/oauth/authorize?request_uri=urn:bad") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            val postMissingAuth =
                client.post("/oauth/authorize") {
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("request_uri", "urn:good")
                                append("decision", "approve")
                            },
                        ),
                    )
                }
            val postMissingRequestUri =
                client.post("/oauth/authorize") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    setBody(FormDataContent(Parameters.build { append("decision", "approve") }))
                }
            val postInvalidRequestUri =
                client.post("/oauth/authorize") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("request_uri", "urn:bad")
                                append("decision", "approve")
                            },
                        ),
                    )
                }
            val customPortPar =
                client.post("/oauth/par") {
                    header(HttpHeaders.Host, "localhost:444")
                    header("DPoP", createDpopProof(generateP256KeyPair(), "POST", "http://localhost:444/oauth/par"))
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", "https://viewer.example.com/client.json")
                                append("response_type", "code")
                                append("scope", "atproto")
                                append("redirect_uri", "https://viewer.example.com/callback")
                                append("code_challenge", "challenge")
                                append("code_challenge_method", "S256")
                            },
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, missingRequestUri.status)
            assertEquals(HttpStatusCode.Unauthorized, invalidBearer.status)
            assertEquals(HttpStatusCode.Unauthorized, missingAccount.status)
            assertEquals(HttpStatusCode.BadRequest, invalidPrompt.status)
            assertEquals(HttpStatusCode.Unauthorized, postMissingAuth.status)
            assertEquals(HttpStatusCode.BadRequest, postMissingRequestUri.status)
            assertEquals(HttpStatusCode.BadRequest, postInvalidRequestUri.status)
            assertEquals(HttpStatusCode.Created, customPortPar.status)
            assertEquals("http://localhost:444/oauth/par", capturedHtu)
        }

    @Test
    fun `oauth routes cover nonce retries missing values unsupported grants and account resolution config`() =
        testApplication {
            val authorizationService = mockk<OAuthAuthorizationService>()

            coEvery { authorizationService.createPushedAuthorizationRequest(any()) } returns
                Result.failure(
                    OAuthUseDpopNonceException("fresh-nonce"),
                )

            application {
                install(ContentNegotiation) {
                    json(json)
                }
                routing {
                    oauthRoutes(
                        config = OAuthConfig(issuer = "https://logdate.app"),
                        keyService = OAuthKeyService(),
                        authorizationService = authorizationService,
                    )
                }
            }

            val parMissingClientId =
                client.post("/oauth/par") {
                    header("DPoP", createDpopProof(generateP256KeyPair(), "POST", "http://localhost/oauth/par"))
                    setBody(FormDataContent(Parameters.build { append("redirect_uri", "https://viewer.example.com/callback") }))
                }
            val parMissingDpop =
                client.post("/oauth/par") {
                    setBody(FormDataContent(Parameters.build { append("client_id", "https://viewer.example.com/client.json") }))
                }
            val parNonceRetry =
                client.post("/oauth/par") {
                    header("DPoP", createDpopProof(generateP256KeyPair(), "POST", "http://localhost/oauth/par"))
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", "https://viewer.example.com/client.json")
                                append("response_type", "code")
                                append("scope", "atproto")
                                append("redirect_uri", "https://viewer.example.com/callback")
                                append("code_challenge", "challenge")
                                append("code_challenge_method", "S256")
                            },
                        ),
                    )
                }
            val authorizeWithoutAccountResolution = client.get("/oauth/authorize?request_uri=urn:test")
            val unsupportedGrant =
                client.post("/oauth/token") {
                    header("DPoP", createDpopProof(generateP256KeyPair(), "POST", "http://localhost/oauth/token"))
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("grant_type", "client_credentials")
                            },
                        ),
                    )
                }
            val revokeMissingToken =
                client.post("/oauth/revoke") {
                    header("DPoP", createDpopProof(generateP256KeyPair(), "POST", "http://localhost/oauth/revoke"))
                    setBody(FormDataContent(Parameters.build { append("client_id", "https://viewer.example.com/client.json") }))
                }
            val revokeMissingDpop =
                client.post("/oauth/revoke") {
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("token", "refresh")
                                append("client_id", "https://viewer.example.com/client.json")
                            },
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, parMissingClientId.status)
            assertTrue(parMissingClientId.bodyAsText().contains("client_id is required"))
            assertEquals(HttpStatusCode.BadRequest, parMissingDpop.status)
            assertEquals(HttpStatusCode.BadRequest, parNonceRetry.status)
            assertEquals("fresh-nonce", parNonceRetry.headers["DPoP-Nonce"])
            assertEquals(HttpStatusCode.NotImplemented, authorizeWithoutAccountResolution.status)
            assertEquals(HttpStatusCode.BadRequest, unsupportedGrant.status)
            assertTrue(unsupportedGrant.bodyAsText().contains("unsupported_grant_type"))
            assertEquals(HttpStatusCode.BadRequest, revokeMissingToken.status)
            assertTrue(revokeMissingToken.bodyAsText().contains("token is required"))
            assertEquals(HttpStatusCode.BadRequest, revokeMissingDpop.status)
        }

    private fun TestApplicationBuilder.configureOAuthTestApp(
        clientId: String,
        redirectUri: String,
    ): AuthV1TestEnvironment {
        val metadataResolver =
            OAuthClientMetadataResolver(
                httpClient =
                    HttpClient(
                        MockEngine { request ->
                            respond(
                                content = clientMetadataJson(clientId = request.url.toString(), redirectUri = redirectUri),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                            )
                        },
                    ),
            )
        return configureAuthV1TestApp(
            oauthClientMetadataResolver = metadataResolver,
        )
    }
}
