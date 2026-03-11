package app.logdate.server.oauth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import studio.hypertext.atproto.pds.AuthorizationPrompt
import studio.hypertext.atproto.pds.OAuthTokenResponse
import studio.hypertext.atproto.pds.PushedAuthorizationResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class OAuthAuthorizationServiceTest {
    @Test
    fun `authorization service completes pushed auth code refresh and revoke flow`() =
        kotlinx.coroutines.test.runTest {
            val clock = MutableClock(Instant.parse("2026-03-08T00:00:00Z"))
            val clientId = "https://viewer.example.com/client.json"
            val redirectUri = "https://viewer.example.com/callback"
            val resolver =
                OAuthClientMetadataResolver(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                respond(
                                    content =
                                        clientMetadataJson(
                                            clientId = clientId,
                                            redirectUri = redirectUri,
                                            extraRedirectUris = listOf("$redirectUri?mode=1"),
                                        ),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                    clock = clock,
                )
            val nonceService = OAuthNonceService(clock = clock)
            val keyService = OAuthKeyService()
            val accessTokenService = OAuthAccessTokenService(OAuthConfig(issuer = "https://logdate.app"), keyService, clock)
            val dpopVerifier = OAuthDpopVerifier(clock = clock)
            val service =
                OAuthAuthorizationService(
                    clientMetadataResolver = resolver,
                    dpopVerifier = dpopVerifier,
                    accessTokenService = accessTokenService,
                    nonceService = nonceService,
                    authorizationServerIssuer = "https://logdate.app",
                    clock = clock,
                )
            val clientKeyPair = generateP256KeyPair()

            val par =
                service.createPushedAuthorizationRequest(
                    clientId = clientId,
                    redirectUri = redirectUri,
                    scope = "atproto",
                    responseType = "code",
                    codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallengeMethod = "S256",
                    state = "state-123",
                    loginHint = "alice.logdate.app",
                    dpopProof = createDpopProof(clientKeyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                    htu = "https://logdate.app/oauth/par",
                )
            val prompt = service.describeAuthorizationRequest(par.requestUri)
            val redirect =
                service.completeAuthorization(
                    requestUri = par.requestUri,
                    subjectDid = "did:plc:alice123",
                    subjectHandle = "alice.logdate.app",
                    approved = true,
                )
            val code = redirect.substringAfter("code=").substringBefore('&')

            assertEquals("Journal Viewer", prompt.clientName)
            assertEquals("state-123", prompt.state)
            assertTrue(par.dpopNonce.isNotBlank())
            assertTrue(service.nonce().isNotBlank())

            val token =
                service.exchangeAuthorizationCode(
                    code = code,
                    redirectUri = redirectUri,
                    clientId = clientId,
                    codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                    dpopProof =
                        createDpopProof(
                            clientKeyPair,
                            "POST",
                            "https://logdate.app/oauth/token",
                            nonce = service.nonce(),
                            iat = clock.now().epochSeconds,
                        ),
                    htu = "https://logdate.app/oauth/token",
                )
            val validatedAccessToken = accessTokenService.validateAccessToken(token.access_token)
            val refreshed =
                service.exchangeRefreshToken(
                    refreshToken = token.refresh_token,
                    clientId = clientId,
                    dpopProof =
                        createDpopProof(
                            clientKeyPair,
                            "POST",
                            "https://logdate.app/oauth/token",
                            nonce = service.nonce(),
                            iat = clock.now().epochSeconds,
                        ),
                    htu = "https://logdate.app/oauth/token",
                )

            assertEquals("did:plc:alice123", token.sub)
            assertEquals("did:plc:alice123", validatedAccessToken?.subjectDid)
            assertNotEquals(token.refresh_token, refreshed.refresh_token)

            service.revokeRefreshToken(
                refreshToken = refreshed.refresh_token,
                clientId = clientId,
                dpopProof =
                    createDpopProof(
                        clientKeyPair,
                        "POST",
                        "https://logdate.app/oauth/revoke",
                        nonce = service.nonce(),
                        iat = clock.now().epochSeconds,
                    ),
                htu = "https://logdate.app/oauth/revoke",
            )
            service.revokeRefreshToken(
                refreshToken = refreshed.refresh_token,
                clientId = clientId,
                dpopProof =
                    createDpopProof(
                        clientKeyPair,
                        "POST",
                        "https://logdate.app/oauth/revoke",
                        nonce = service.nonce(),
                        iat = clock.now().epochSeconds,
                    ),
                htu = "https://logdate.app/oauth/revoke",
            )
        }

    @Test
    fun `authorization service binds confidential client assertions across par token refresh and revoke`() =
        kotlinx.coroutines.test.runTest {
            val clock = MutableClock(Instant.parse("2026-03-08T00:00:00Z"))
            val clientId = "https://viewer.example.com/client.json"
            val redirectUri = "https://viewer.example.com/callback"
            val confidentialClientKey = generateP256KeyPair()
            val otherClientKey = generateP256KeyPair()
            val resolver =
                OAuthClientMetadataResolver(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                respond(
                                    content =
                                        clientMetadataJson(
                                            clientId = clientId,
                                            redirectUri = redirectUri,
                                            tokenEndpointAuthMethod = "private_key_jwt",
                                            tokenEndpointAuthSigningAlg = "ES256",
                                            jwksJson = clientJwksJson(confidentialClientKey),
                                        ),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                    clock = clock,
                )
            val nonceService = OAuthNonceService(clock = clock)
            val keyService = OAuthKeyService()
            val accessTokenService = OAuthAccessTokenService(OAuthConfig(issuer = "https://logdate.app"), keyService, clock)
            val service =
                OAuthAuthorizationService(
                    clientMetadataResolver = resolver,
                    dpopVerifier = OAuthDpopVerifier(clock = clock),
                    accessTokenService = accessTokenService,
                    nonceService = nonceService,
                    authorizationServerIssuer = "https://logdate.app",
                    clock = clock,
                )
            val dpopKeyPair = generateP256KeyPair()

            val par =
                service.createPushedAuthorizationRequest(
                    clientId = clientId,
                    redirectUri = redirectUri,
                    scope = "atproto",
                    responseType = "code",
                    codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallengeMethod = "S256",
                    state = "confidential-state",
                    loginHint = "alice.logdate.app",
                    clientAssertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                    clientAssertion =
                        createClientAssertion(
                            keyPair = confidentialClientKey,
                            clientId = clientId,
                            audience = "https://logdate.app",
                            iat = clock.now().epochSeconds,
                            jti = "par-assertion",
                        ),
                    dpopProof =
                        createDpopProof(
                            dpopKeyPair,
                            "POST",
                            "https://logdate.app/oauth/par",
                            iat = clock.now().epochSeconds,
                        ),
                    htu = "https://logdate.app/oauth/par",
                )
            val redirect =
                service.completeAuthorization(
                    requestUri = par.requestUri,
                    subjectDid = "did:plc:alice123",
                    subjectHandle = "alice.logdate.app",
                    approved = true,
                )
            val code = redirect.substringAfter("code=").substringBefore('&')

            val token =
                service.exchangeAuthorizationCode(
                    code = code,
                    redirectUri = redirectUri,
                    clientId = clientId,
                    codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                    clientAssertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                    clientAssertion =
                        createClientAssertion(
                            keyPair = confidentialClientKey,
                            clientId = clientId,
                            audience = "https://logdate.app",
                            iat = clock.now().epochSeconds,
                            jti = "token-assertion",
                        ),
                    dpopProof =
                        createDpopProof(
                            dpopKeyPair,
                            "POST",
                            "https://logdate.app/oauth/token",
                            nonce = service.nonce(),
                            iat = clock.now().epochSeconds,
                        ),
                    htu = "https://logdate.app/oauth/token",
                )
            val refreshed =
                service.exchangeRefreshToken(
                    refreshToken = token.refresh_token,
                    clientId = clientId,
                    clientAssertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                    clientAssertion =
                        createClientAssertion(
                            keyPair = confidentialClientKey,
                            clientId = clientId,
                            audience = "https://logdate.app",
                            iat = clock.now().epochSeconds,
                            jti = "refresh-assertion",
                        ),
                    dpopProof =
                        createDpopProof(
                            dpopKeyPair,
                            "POST",
                            "https://logdate.app/oauth/token",
                            nonce = service.nonce(),
                            iat = clock.now().epochSeconds,
                        ),
                    htu = "https://logdate.app/oauth/token",
                )
            val wrongRefreshClient =
                runCatching {
                    service.exchangeRefreshToken(
                        refreshToken = refreshed.refresh_token,
                        clientId = clientId,
                        clientAssertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                        clientAssertion =
                            createClientAssertion(
                                keyPair = otherClientKey,
                                clientId = clientId,
                                audience = "https://logdate.app",
                                iat = clock.now().epochSeconds,
                                jti = "wrong-refresh-assertion",
                            ),
                        dpopProof =
                            createDpopProof(
                                dpopKeyPair,
                                "POST",
                                "https://logdate.app/oauth/token",
                                nonce = service.nonce(),
                                iat = clock.now().epochSeconds,
                            ),
                        htu = "https://logdate.app/oauth/token",
                    )
                }.exceptionOrNull()

            service.revokeRefreshToken(
                refreshToken = refreshed.refresh_token,
                clientId = clientId,
                clientAssertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                clientAssertion =
                    createClientAssertion(
                        keyPair = confidentialClientKey,
                        clientId = clientId,
                        audience = "https://logdate.app",
                        iat = clock.now().epochSeconds,
                        jti = "revoke-assertion",
                    ),
                dpopProof =
                    createDpopProof(
                        dpopKeyPair,
                        "POST",
                        "https://logdate.app/oauth/revoke",
                        nonce = service.nonce(),
                        iat = clock.now().epochSeconds,
                    ),
                htu = "https://logdate.app/oauth/revoke",
            )

            assertEquals("did:plc:alice123", token.sub)
            assertEquals("did:plc:alice123", refreshed.sub)
            assertIs<OAuthInvalidClientException>(wrongRefreshClient)
        }

    @Test
    fun `authorization service handles denial expiry and validation failures`() =
        kotlinx.coroutines.test.runTest {
            val clock = MutableClock(Instant.parse("2026-03-08T00:00:00Z"))
            val clientId = "https://viewer.example.com/client.json"
            val redirectUri = "https://viewer.example.com/callback"
            val resolver =
                OAuthClientMetadataResolver(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                respond(
                                    content = clientMetadataJson(clientId = clientId, redirectUri = redirectUri, scope = "atproto profile"),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                    clock = clock,
                )
            val service =
                OAuthAuthorizationService(
                    clientMetadataResolver = resolver,
                    dpopVerifier = OAuthDpopVerifier(clock = clock),
                    accessTokenService = OAuthAccessTokenService(OAuthConfig(issuer = "https://logdate.app"), OAuthKeyService(), clock),
                    nonceService = OAuthNonceService(clock = clock),
                    authorizationServerIssuer = "https://logdate.app",
                    clock = clock,
                )
            val keyPair = generateP256KeyPair()

            assertIs<OAuthInvalidRequestException>(
                runCatching {
                    service.createPushedAuthorizationRequest(
                        clientId = clientId,
                        redirectUri = redirectUri,
                        scope = "profile",
                        responseType = "token",
                        codeChallenge = "challenge",
                        codeChallengeMethod = "plain",
                        state = null,
                        loginHint = null,
                        dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                        htu = "https://logdate.app/oauth/par",
                    )
                }.exceptionOrNull(),
            )

            val par =
                service.createPushedAuthorizationRequest(
                    clientId = clientId,
                    redirectUri = redirectUri,
                    scope = "atproto",
                    responseType = "code",
                    codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallengeMethod = "S256",
                    state = null,
                    loginHint = "brie.logdate.app",
                    dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                    htu = "https://logdate.app/oauth/par",
                )
            val denial = service.completeAuthorization(par.requestUri, "did:plc:brie123", "brie.logdate.app", approved = false)
            assertTrue(denial.contains("access_denied"))

            val mismatchedLoginHint =
                service.createPushedAuthorizationRequest(
                    clientId = clientId,
                    redirectUri = redirectUri,
                    scope = "atproto",
                    responseType = "code",
                    codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallengeMethod = "S256",
                    state = null,
                    loginHint = "other.logdate.app",
                    dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                    htu = "https://logdate.app/oauth/par",
                )
            assertIs<OAuthInvalidRequestException>(
                runCatching {
                    service.completeAuthorization(mismatchedLoginHint.requestUri, "did:plc:brie123", "brie.logdate.app", approved = true)
                }.exceptionOrNull(),
            )

            val expiringRequest =
                service.createPushedAuthorizationRequest(
                    clientId = clientId,
                    redirectUri = redirectUri,
                    scope = "atproto",
                    responseType = "code",
                    codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallengeMethod = "S256",
                    state = null,
                    loginHint = null,
                    dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                    htu = "https://logdate.app/oauth/par",
                )
            clock.nowValue += 6.minutes
            assertIs<OAuthInvalidRequestException>(
                runCatching { service.describeAuthorizationRequest(expiringRequest.requestUri) }.exceptionOrNull(),
            )
        }

    @Test
    fun `authorization service validates request inputs codes and dpop refresh binding`() =
        kotlinx.coroutines.test.runTest {
            val clock = MutableClock(Instant.parse("2026-03-08T00:00:00Z"))
            val clientId = "https://viewer.example.com/client.json"
            val redirectUri = "https://viewer.example.com/callback"
            val resolver =
                OAuthClientMetadataResolver(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                respond(
                                    content =
                                        clientMetadataJson(
                                            clientId = clientId,
                                            redirectUri = redirectUri,
                                            scope = "atproto profile",
                                        ),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                    clock = clock,
                )
            val nonceService = OAuthNonceService(clock = clock)
            val service =
                OAuthAuthorizationService(
                    clientMetadataResolver = resolver,
                    dpopVerifier = OAuthDpopVerifier(clock = clock),
                    accessTokenService = OAuthAccessTokenService(OAuthConfig(issuer = "https://logdate.app"), OAuthKeyService(), clock),
                    nonceService = nonceService,
                    authorizationServerIssuer = "https://logdate.app",
                    clock = clock,
                )
            val keyPair = generateP256KeyPair()
            val otherKeyPair = generateP256KeyPair()

            assertIs<OAuthInvalidRequestException>(
                runCatching {
                    service.createPushedAuthorizationRequest(
                        clientId = clientId,
                        redirectUri = redirectUri,
                        scope = "atproto",
                        responseType = "code",
                        codeChallenge = " ",
                        codeChallengeMethod = "S256",
                        state = null,
                        loginHint = null,
                        dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                        htu = "https://logdate.app/oauth/par",
                    )
                }.exceptionOrNull(),
            )
            assertIs<OAuthInvalidRequestException>(
                runCatching {
                    service.createPushedAuthorizationRequest(
                        clientId = clientId,
                        redirectUri = redirectUri,
                        scope = "atproto",
                        responseType = "code",
                        codeChallenge = "challenge",
                        codeChallengeMethod = "plain",
                        state = null,
                        loginHint = null,
                        dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                        htu = "https://logdate.app/oauth/par",
                    )
                }.exceptionOrNull(),
            )
            assertIs<OAuthInvalidRequestException>(
                runCatching {
                    service.createPushedAuthorizationRequest(
                        clientId = clientId,
                        redirectUri = redirectUri,
                        scope = "profile",
                        responseType = "code",
                        codeChallenge = "challenge",
                        codeChallengeMethod = "S256",
                        state = null,
                        loginHint = null,
                        dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                        htu = "https://logdate.app/oauth/par",
                    )
                }.exceptionOrNull(),
            )
            assertIs<OAuthInvalidClientException>(
                runCatching {
                    service.createPushedAuthorizationRequest(
                        clientId = clientId,
                        redirectUri = "https://viewer.example.com/other",
                        scope = "atproto",
                        responseType = "code",
                        codeChallenge = "challenge",
                        codeChallengeMethod = "S256",
                        state = null,
                        loginHint = null,
                        dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                        htu = "https://logdate.app/oauth/par",
                    )
                }.exceptionOrNull(),
            )
            assertIs<OAuthInvalidClientException>(
                runCatching {
                    service.createPushedAuthorizationRequest(
                        clientId = clientId,
                        redirectUri = redirectUri,
                        scope = "atproto email",
                        responseType = "code",
                        codeChallenge = "challenge",
                        codeChallengeMethod = "S256",
                        state = null,
                        loginHint = null,
                        dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                        htu = "https://logdate.app/oauth/par",
                    )
                }.exceptionOrNull(),
            )

            val par =
                service.createPushedAuthorizationRequest(
                    clientId = clientId,
                    redirectUri = redirectUri,
                    scope = "atproto",
                    responseType = "code",
                    codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallengeMethod = "S256",
                    state = null,
                    loginHint = null,
                    dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                    htu = "https://logdate.app/oauth/par",
                )
            val redirect = service.completeAuthorization(par.requestUri, "did:plc:alice123", "alice.logdate.app", approved = true)
            val code = redirect.substringAfter("code=").substringBefore('&')

            assertIs<OAuthInvalidGrantException>(
                runCatching {
                    service.exchangeAuthorizationCode(
                        code = "missing",
                        redirectUri = redirectUri,
                        clientId = clientId,
                        codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                        dpopProof =
                            createDpopProof(
                                keyPair,
                                "POST",
                                "https://logdate.app/oauth/token",
                                nonce = nonceService.currentNonce(),
                                iat = clock.now().epochSeconds,
                            ),
                        htu = "https://logdate.app/oauth/token",
                    )
                }.exceptionOrNull(),
            )
            assertIs<OAuthInvalidGrantException>(
                runCatching {
                    service.exchangeAuthorizationCode(
                        code = code,
                        redirectUri = redirectUri,
                        clientId = clientId,
                        codeVerifier = " ",
                        dpopProof =
                            createDpopProof(
                                keyPair,
                                "POST",
                                "https://logdate.app/oauth/token",
                                nonce = nonceService.currentNonce(),
                                iat = clock.now().epochSeconds,
                            ),
                        htu = "https://logdate.app/oauth/token",
                    )
                }.exceptionOrNull(),
            )
            val mismatchPar =
                service.createPushedAuthorizationRequest(
                    clientId = clientId,
                    redirectUri = redirectUri,
                    scope = "atproto",
                    responseType = "code",
                    codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallengeMethod = "S256",
                    state = null,
                    loginHint = null,
                    dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                    htu = "https://logdate.app/oauth/par",
                )
            val mismatchCode =
                service
                    .completeAuthorization(mismatchPar.requestUri, "did:plc:alice123", "alice.logdate.app", approved = true)
                    .substringAfter("code=")
                    .substringBefore('&')
            assertIs<OAuthInvalidGrantException>(
                runCatching {
                    service.exchangeAuthorizationCode(
                        code = mismatchCode,
                        redirectUri = "https://viewer.example.com/other",
                        clientId = clientId,
                        codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                        dpopProof =
                            createDpopProof(
                                keyPair,
                                "POST",
                                "https://logdate.app/oauth/token",
                                nonce = nonceService.currentNonce(),
                                iat = clock.now().epochSeconds,
                            ),
                        htu = "https://logdate.app/oauth/token",
                    )
                }.exceptionOrNull(),
            )

            val nextPar =
                service.createPushedAuthorizationRequest(
                    clientId = clientId,
                    redirectUri = redirectUri,
                    scope = "atproto",
                    responseType = "code",
                    codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallengeMethod = "S256",
                    state = null,
                    loginHint = null,
                    dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                    htu = "https://logdate.app/oauth/par",
                )
            val nextCode =
                service
                    .completeAuthorization(nextPar.requestUri, "did:plc:alice123", "alice.logdate.app", approved = true)
                    .substringAfter("code=")
                    .substringBefore('&')
            val token =
                service.exchangeAuthorizationCode(
                    code = nextCode,
                    redirectUri = redirectUri,
                    clientId = clientId,
                    codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                    dpopProof =
                        createDpopProof(
                            keyPair,
                            "POST",
                            "https://logdate.app/oauth/token",
                            nonce = nonceService.currentNonce(),
                            iat = clock.now().epochSeconds,
                        ),
                    htu = "https://logdate.app/oauth/token",
                )

            assertIs<OAuthInvalidGrantException>(
                runCatching {
                    service.exchangeRefreshToken(
                        refreshToken = token.refresh_token,
                        clientId = clientId,
                        dpopProof =
                            createDpopProof(
                                otherKeyPair,
                                "POST",
                                "https://logdate.app/oauth/token",
                                nonce = nonceService.currentNonce(),
                                iat = clock.now().epochSeconds,
                            ),
                        htu = "https://logdate.app/oauth/token",
                    )
                }.exceptionOrNull(),
            )
            assertIs<OAuthInvalidGrantException>(
                runCatching {
                    service.revokeRefreshToken(
                        refreshToken = token.refresh_token,
                        clientId = clientId,
                        dpopProof =
                            createDpopProof(
                                otherKeyPair,
                                "POST",
                                "https://logdate.app/oauth/revoke",
                                nonce = nonceService.currentNonce(),
                                iat = clock.now().epochSeconds,
                            ),
                        htu = "https://logdate.app/oauth/revoke",
                    )
                }.exceptionOrNull(),
            )
        }

    @Test
    fun `authorization service rejects bad code verifier dpop mismatch wrong client and invalid refresh token`() =
        kotlinx.coroutines.test.runTest {
            val clock = MutableClock(Instant.parse("2026-03-08T00:00:00Z"))
            val clientId = "https://viewer.example.com/client.json"
            val redirectUri = "https://viewer.example.com/callback"
            val resolver =
                OAuthClientMetadataResolver(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                respond(
                                    content =
                                        clientMetadataJson(
                                            clientId = clientId,
                                            redirectUri = redirectUri,
                                            extraRedirectUris = listOf("$redirectUri?mode=1"),
                                        ),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                    clock = clock,
                )
            val nonceService = OAuthNonceService(clock = clock)
            val service =
                OAuthAuthorizationService(
                    clientMetadataResolver = resolver,
                    dpopVerifier = OAuthDpopVerifier(clock = clock),
                    accessTokenService = OAuthAccessTokenService(OAuthConfig(issuer = "https://logdate.app"), OAuthKeyService(), clock),
                    nonceService = nonceService,
                    authorizationServerIssuer = "https://logdate.app",
                    clock = clock,
                )
            val keyPair = generateP256KeyPair()
            val otherKeyPair = generateP256KeyPair()
            val par =
                service.createPushedAuthorizationRequest(
                    clientId = clientId,
                    redirectUri = redirectUri,
                    scope = "atproto",
                    responseType = "code",
                    codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallengeMethod = "S256",
                    state = null,
                    loginHint = null,
                    dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                    htu = "https://logdate.app/oauth/par",
                )
            val redirect = service.completeAuthorization(par.requestUri, "did:plc:alice123", "alice.logdate.app", approved = true)
            val code = redirect.substringAfter("code=").substringBefore('&')

            assertIs<OAuthInvalidGrantException>(
                runCatching {
                    service.exchangeAuthorizationCode(
                        code = code,
                        redirectUri = redirectUri,
                        clientId = clientId,
                        codeVerifier = "wrong",
                        dpopProof =
                            createDpopProof(
                                keyPair,
                                "POST",
                                "https://logdate.app/oauth/token",
                                nonce = nonceService.currentNonce(),
                                iat = clock.now().epochSeconds,
                            ),
                        htu = "https://logdate.app/oauth/token",
                    )
                }.exceptionOrNull(),
            )

            val nextPar =
                service.createPushedAuthorizationRequest(
                    clientId = clientId,
                    redirectUri = redirectUri,
                    scope = "atproto",
                    responseType = "code",
                    codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallengeMethod = "S256",
                    state = null,
                    loginHint = null,
                    dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                    htu = "https://logdate.app/oauth/par",
                )
            val nextCode =
                service
                    .completeAuthorization(nextPar.requestUri, "did:plc:alice123", "alice.logdate.app", approved = true)
                    .substringAfter("code=")
            assertIs<OAuthInvalidGrantException>(
                runCatching {
                    service.exchangeAuthorizationCode(
                        code = nextCode,
                        redirectUri = redirectUri,
                        clientId = clientId,
                        codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                        dpopProof =
                            createDpopProof(
                                otherKeyPair,
                                "POST",
                                "https://logdate.app/oauth/token",
                                nonce = nonceService.currentNonce(),
                                iat = clock.now().epochSeconds,
                            ),
                        htu = "https://logdate.app/oauth/token",
                    )
                }.exceptionOrNull(),
            )

            assertIs<OAuthInvalidGrantException>(
                runCatching {
                    service.exchangeRefreshToken(
                        refreshToken = "missing",
                        clientId = clientId,
                        dpopProof =
                            createDpopProof(
                                keyPair,
                                "POST",
                                "https://logdate.app/oauth/token",
                                nonce = nonceService.currentNonce(),
                                iat = clock.now().epochSeconds,
                            ),
                        htu = "https://logdate.app/oauth/token",
                    )
                }.exceptionOrNull(),
            )

            val successPar =
                service.createPushedAuthorizationRequest(
                    clientId = clientId,
                    redirectUri = "$redirectUri?mode=1",
                    scope = "atproto",
                    responseType = "code",
                    codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallengeMethod = "S256",
                    state = "state",
                    loginHint = null,
                    dpopProof = createDpopProof(keyPair, "POST", "https://logdate.app/oauth/par", iat = clock.now().epochSeconds),
                    htu = "https://logdate.app/oauth/par",
                )
            val successCode =
                service
                    .completeAuthorization(successPar.requestUri, "did:plc:alice123", "alice.logdate.app", approved = true)
                    .substringAfter("code=")
                    .substringBefore('&')
            val token =
                service.exchangeAuthorizationCode(
                    code = successCode,
                    redirectUri = "$redirectUri?mode=1",
                    clientId = clientId,
                    codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                    dpopProof =
                        createDpopProof(
                            keyPair,
                            "POST",
                            "https://logdate.app/oauth/token",
                            nonce = nonceService.currentNonce(),
                            iat = clock.now().epochSeconds,
                        ),
                    htu = "https://logdate.app/oauth/token",
                )

            assertIs<OAuthInvalidGrantException>(
                runCatching {
                    service.exchangeRefreshToken(
                        refreshToken = token.refresh_token,
                        clientId = "https://other.example.com/client.json",
                        dpopProof =
                            createDpopProof(
                                keyPair,
                                "POST",
                                "https://logdate.app/oauth/token",
                                nonce = nonceService.currentNonce(),
                                iat = clock.now().epochSeconds,
                            ),
                        htu = "https://logdate.app/oauth/token",
                    )
                }.exceptionOrNull(),
            )
            assertIs<OAuthInvalidGrantException>(
                runCatching {
                    service.revokeRefreshToken(
                        refreshToken = token.refresh_token,
                        clientId = "https://other.example.com/client.json",
                        dpopProof =
                            createDpopProof(
                                keyPair,
                                "POST",
                                "https://logdate.app/oauth/revoke",
                                nonce = nonceService.currentNonce(),
                                iat = clock.now().epochSeconds,
                            ),
                        htu = "https://logdate.app/oauth/revoke",
                    )
                }.exceptionOrNull(),
            )

            service.revokeRefreshToken(
                refreshToken = "missing",
                clientId = clientId,
                dpopProof =
                    createDpopProof(
                        keyPair,
                        "POST",
                        "https://logdate.app/oauth/revoke",
                        nonce = nonceService.currentNonce(),
                        iat = clock.now().epochSeconds,
                    ),
                htu = "https://logdate.app/oauth/revoke",
            )
        }

    @Test
    fun `authorization service covers blank verifier query separator and serializer models`() {
        val json = Json
        val prompt = AuthorizationPrompt("urn:request", "client", "Client", "https://viewer.example.com/callback", "atproto", null, null)
        val par = PushedAuthorizationResponse("urn:request", 300, "nonce")
        val token = OAuthTokenResponse("access", "DPoP", 3600, "refresh", "did:plc:alice123", "atproto")
        val authorizationCode =
            instantiatePrivate(
                "app.logdate.server.oauth.StoredAuthorizationCode",
                "code-1",
                "client",
                "https://viewer.example.com/callback",
                "did:plc:alice123",
                "alice.logdate.app",
                "atproto",
                "challenge",
                "thumbprint",
                null,
                null,
                Instant.parse("2026-03-08T00:00:00Z"),
            )
        val refreshToken =
            instantiatePrivate(
                "app.logdate.server.oauth.StoredRefreshToken",
                "refresh-1",
                "client",
                "did:plc:alice123",
                "atproto",
                "thumbprint",
                null,
                null,
                Instant.parse("2026-03-08T00:00:00Z"),
                null,
            )

        assertEquals(
            prompt,
            json.decodeFromString(AuthorizationPrompt.serializer(), json.encodeToString(AuthorizationPrompt.serializer(), prompt)),
        )
        assertEquals(
            par,
            json.decodeFromString(
                PushedAuthorizationResponse.serializer(),
                json.encodeToString(PushedAuthorizationResponse.serializer(), par),
            ),
        )
        assertEquals(
            token,
            json.decodeFromString(OAuthTokenResponse.serializer(), json.encodeToString(OAuthTokenResponse.serializer(), token)),
        )
        assertEquals("urn:request", prompt.requestUri)
        assertEquals("Client", prompt.clientName)
        assertEquals(null, prompt.state)
        assertEquals("nonce", par.dpopNonce)
        assertEquals("DPoP", token.token_type)
        assertEquals(3600L, token.expires_in)
        assertEquals("refresh", token.refresh_token)
        assertEquals("did:plc:alice123", token.sub)
        assertEquals("atproto", token.scope)
        assertEquals("code-1", invokeGetter(authorizationCode, "getCode"))
        assertEquals("client", invokeGetter(authorizationCode, "getClientId"))
        assertEquals("https://viewer.example.com/callback", invokeGetter(authorizationCode, "getRedirectUri"))
        assertEquals("did:plc:alice123", invokeGetter(authorizationCode, "getSubjectDid"))
        assertEquals("alice.logdate.app", invokeGetter(authorizationCode, "getSubjectHandle"))
        assertEquals("atproto", invokeGetter(authorizationCode, "getScope"))
        assertEquals("challenge", invokeGetter(authorizationCode, "getCodeChallenge"))
        assertEquals("thumbprint", invokeGetter(authorizationCode, "getDpopKeyThumbprint"))
        assertEquals(null, invokeGetter(authorizationCode, "getClientAuthKeyId"))
        assertEquals(null, invokeGetter(authorizationCode, "getClientAuthKeyThumbprint"))
        assertEquals("refresh-1", invokeGetter(refreshToken, "getToken"))
        assertEquals("atproto", invokeGetter(refreshToken, "getScope"))
        assertEquals(null, invokeGetter(refreshToken, "getClientAuthKeyId"))
        assertEquals(null, invokeGetter(refreshToken, "getClientAuthKeyThumbprint"))
        assertTrue(authorizationCode.toString().contains("code-1"))
        assertTrue(refreshToken.toString().contains("refresh-1"))
        assertTrue(
            runCatching {
                Class
                    .forName("app.logdate.server.oauth.OAuthUnsupportedGrantTypeException")
                    .getDeclaredConstructor(String::class.java)
                    .newInstance("unsupported")
            }.isSuccess,
        )
    }

    private fun instantiatePrivate(
        className: String,
        vararg args: Any?,
    ): Any {
        val clazz = Class.forName(className)
        val constructor =
            clazz.declaredConstructors.single { candidate ->
                val parameterTypes = candidate.parameterTypes
                parameterTypes.size == args.size &&
                    parameterTypes.withIndex().all { (index, type) ->
                        val value = args[index]
                        value == null || parameterMatches(type, value)
                    }
            }
        constructor.isAccessible = true
        return constructor.newInstance(*args)
    }

    private fun invokeGetter(
        target: Any,
        methodName: String,
    ): Any? =
        target.javaClass
            .getDeclaredMethod(methodName)
            .apply { isAccessible = true }
            .invoke(target)

    private fun parameterMatches(
        type: Class<*>,
        value: Any,
    ): Boolean =
        when {
            type.isPrimitive && type == java.lang.Long.TYPE -> value is Long
            else -> type.isAssignableFrom(value.javaClass)
        }
}
