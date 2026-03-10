package app.logdate.server

import app.logdate.server.atproto.LogDateRepoStore
import app.logdate.server.auth.AccountIdentityRepository
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.AuthMetricsRegistry
import app.logdate.server.auth.FakeGoogleIdTokenVerifier
import app.logdate.server.auth.GoogleIdTokenClaims
import app.logdate.server.auth.GoogleIdTokenVerifier
import app.logdate.server.auth.InMemoryAccountIdentityRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.InMemorySessionManager
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.SessionManager
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.oauth.OAuthAccessTokenService
import app.logdate.server.oauth.OAuthAuthorizationService
import app.logdate.server.oauth.OAuthClientMetadataResolver
import app.logdate.server.oauth.OAuthConfig
import app.logdate.server.oauth.OAuthDpopVerifier
import app.logdate.server.oauth.OAuthKeyService
import app.logdate.server.oauth.OAuthNonceService
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.routes.authV1Routes
import app.logdate.server.routes.identityApiRoutes
import app.logdate.server.routes.identityRoutes
import app.logdate.server.routes.oauthRoutes
import app.logdate.server.routes.xrpcRoutes
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncRepository
import app.logdate.util.UuidSerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import studio.hypertext.atproto.pds.DescribeServerResponse
import studio.hypertext.atproto.pds.runtime.DefaultPdsRepoService
import studio.hypertext.atproto.pds.runtime.StaticPdsDiscoveryService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class AuthV1TestEnvironment(
    val tokenService: JwtTokenService,
    val accountRepository: AccountRepository,
    val identityRepository: AccountIdentityRepository,
    val metrics: AuthMetricsRegistry,
    val syncRepository: SyncRepository,
    val signingKeyService: SigningKeyService,
    val atprotoIdentityService: AtprotoIdentityService,
    val oauthConfig: OAuthConfig,
    val oauthKeyService: OAuthKeyService,
    val oauthNonceService: OAuthNonceService,
    val oauthDpopVerifier: OAuthDpopVerifier,
    val oauthAccessTokenService: OAuthAccessTokenService,
    val oauthAuthorizationService: OAuthAuthorizationService,
)

@OptIn(ExperimentalUuidApi::class)
fun TestApplicationBuilder.configureAuthV1TestApp(
    tokenService: JwtTokenService = JwtTokenService("auth-v1-test-secret"),
    accountRepository: AccountRepository = InMemoryAccountRepository(),
    identityRepository: AccountIdentityRepository = InMemoryAccountIdentityRepository(),
    sessionManager: SessionManager = InMemorySessionManager(),
    webAuthnPasskeyService: WebAuthnPasskeyService = WebAuthnPasskeyService(),
    metrics: AuthMetricsRegistry = AuthMetricsRegistry(),
    googleIdTokenVerifier: GoogleIdTokenVerifier? = null,
    googleClaimsByToken: Map<String, GoogleIdTokenClaims> = emptyMap(),
    atprotoIdentityConfig: AtprotoIdentityConfig = AtprotoIdentityConfig("logdate.app", "https://logdate.app"),
    syncRepository: SyncRepository = InMemorySyncRepository(),
    oauthConfig: OAuthConfig = OAuthConfig(issuer = atprotoIdentityConfig.pdsServiceEndpoint),
    oauthClientMetadataResolver: OAuthClientMetadataResolver? = null,
): AuthV1TestEnvironment {
    val verifier: GoogleIdTokenVerifier = googleIdTokenVerifier ?: FakeGoogleIdTokenVerifier(googleClaimsByToken)
    val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-signing-key-kek")
    val atprotoIdentityService =
        AtprotoIdentityService(
            accountRepository = accountRepository,
            signingKeyService = signingKeyService,
            config = atprotoIdentityConfig,
        )
    val logDateRepoStore = LogDateRepoStore(syncRepository = syncRepository, identityService = atprotoIdentityService)
    atprotoIdentityService.setRepoCollectionsResolver(logDateRepoStore::collectionsForDid)
    val oauthKeyService = OAuthKeyService()
    val oauthNonceService = OAuthNonceService()
    val oauthDpopVerifier = OAuthDpopVerifier()
    val oauthAccessTokenService = OAuthAccessTokenService(config = oauthConfig, keyService = oauthKeyService)
    val pdsRepoService = DefaultPdsRepoService(logDateRepoStore)
    val pdsDiscoveryService =
        StaticPdsDiscoveryService(
            authorizationServerMetadata = oauthConfig.authorizationServerMetadata(),
            protectedResourceMetadata = oauthConfig.protectedResourceMetadata(),
            describeServerResponse =
                DescribeServerResponse(
                    did = atprotoIdentityConfig.serverDid,
                    availableUserDomains = listOf(atprotoIdentityConfig.normalizedHandleDomain),
                    inviteCodeRequired = false,
                    phoneVerificationRequired = false,
                ),
        )
    val metadataResolver = oauthClientMetadataResolver ?: OAuthClientMetadataResolver(HttpClient(OkHttp))
    val oauthAuthorizationService =
        OAuthAuthorizationService(
            clientMetadataResolver = metadataResolver,
            dpopVerifier = oauthDpopVerifier,
            accessTokenService = oauthAccessTokenService,
            nonceService = oauthNonceService,
        )

    val json =
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            serializersModule =
                SerializersModule {
                    contextual(Uuid::class, UuidSerializer)
                }
        }

    application {
        install(ContentNegotiation) {
            json(json)
        }
        routing {
            identityRoutes(atprotoIdentityService)
            oauthRoutes(
                config = oauthConfig,
                keyService = oauthKeyService,
                discoveryService = pdsDiscoveryService,
                authorizationService = oauthAuthorizationService,
                accountRepository = accountRepository,
                tokenService = tokenService,
                identityService = atprotoIdentityService,
            )
            xrpcRoutes(
                identityService = atprotoIdentityService,
                discoveryService = pdsDiscoveryService,
                accountRepository = accountRepository,
                tokenService = tokenService,
                repoService = pdsRepoService,
                oauthAccessTokenService = oauthAccessTokenService,
                oauthDpopVerifier = oauthDpopVerifier,
                oauthNonceService = oauthNonceService,
            )
            route("/api/v1") {
                authV1Routes(
                    accountRepository = accountRepository,
                    identityRepository = identityRepository,
                    sessionManager = sessionManager,
                    webAuthnService = webAuthnPasskeyService,
                    atprotoIdentityService = atprotoIdentityService,
                    tokenService = tokenService,
                    googleIdTokenVerifier = verifier,
                    metrics = metrics,
                )
                identityApiRoutes(
                    accountRepository = accountRepository,
                    tokenService = tokenService,
                    atprotoIdentityService = atprotoIdentityService,
                    signingKeyService = signingKeyService,
                )
            }
        }
    }

    return AuthV1TestEnvironment(
        tokenService = tokenService,
        accountRepository = accountRepository,
        identityRepository = identityRepository,
        metrics = metrics,
        syncRepository = syncRepository,
        signingKeyService = signingKeyService,
        atprotoIdentityService = atprotoIdentityService,
        oauthConfig = oauthConfig,
        oauthKeyService = oauthKeyService,
        oauthNonceService = oauthNonceService,
        oauthDpopVerifier = oauthDpopVerifier,
        oauthAccessTokenService = oauthAccessTokenService,
        oauthAuthorizationService = oauthAuthorizationService,
    )
}
