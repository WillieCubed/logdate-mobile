package app.logdate.server.routes

import app.logdate.server.ServerDescriptorConfig
import app.logdate.server.atproto.AtprotoSessionTokenService
import app.logdate.server.auth.AccountDeletionService
import app.logdate.server.auth.AccountIdentityRepository
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.AuthMetricsRegistry
import app.logdate.server.auth.EmailVerificationService
import app.logdate.server.auth.GoogleIdTokenVerifier
import app.logdate.server.auth.RefreshTokenRevocationRepository
import app.logdate.server.auth.SessionManager
import app.logdate.server.auth.TokenService
import app.logdate.server.entitlements.EntitlementEnforcer
import app.logdate.server.entitlements.EntitlementService
import app.logdate.server.entitlements.PlanCatalogService
import app.logdate.server.entitlements.UsageCalculator
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.CompositeLogDateMediaBlobRepository
import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.RepoBackedLogDateCollectionsRepository
import app.logdate.server.logdate.ResourceRouteRepository
import app.logdate.server.oauth.OAuthAccessTokenService
import app.logdate.server.oauth.OAuthAuthorizationService
import app.logdate.server.oauth.OAuthConfig
import app.logdate.server.oauth.OAuthDpopVerifier
import app.logdate.server.oauth.OAuthKeyService
import app.logdate.server.oauth.OAuthNonceService
import app.logdate.server.passkeys.RestoreCredentialService
import app.logdate.server.passkeys.WebAuthnConfig
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.ratelimit.SlidingWindowRateLimiter
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.server.transcription.cloudTranscriptionSessionProviderFromEnvironment
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject
import studio.hypertext.atproto.pds.PdsBlobService
import studio.hypertext.atproto.pds.PdsDiscoveryService
import studio.hypertext.atproto.pds.PdsRepoService
import studio.hypertext.atproto.pds.PdsSessionService
import studio.hypertext.atproto.pds.PdsSyncService

// The composition root: each function resolves what its routes need from Koin and hands it over
// as explicit parameters, so the route files themselves stay container-free and testable with
// in-memory implementations.

/** AT Protocol surface: identity, app links, OAuth, and XRPC. */
internal fun Application.atprotoRoutes() {
    val accountRepository by inject<AccountRepository>()
    val tokenService by inject<TokenService>()
    val atprotoIdentityService by inject<AtprotoIdentityService>()
    val assetLinksConfig by inject<AssetLinksConfig>()
    val pdsDiscoveryService by inject<PdsDiscoveryService>()
    val pdsRepoService by inject<PdsRepoService>()
    val pdsSessionService by inject<PdsSessionService>()
    val pdsSyncService by inject<PdsSyncService>()
    val pdsBlobService by inject<PdsBlobService>()
    val atprotoSessionTokenService by inject<AtprotoSessionTokenService>()
    val oauthConfig by inject<OAuthConfig>()
    val oauthKeyService by inject<OAuthKeyService>()
    val oauthAuthorizationService by inject<OAuthAuthorizationService>()
    val oauthAccessTokenService by inject<OAuthAccessTokenService>()
    val oauthDpopVerifier by inject<OAuthDpopVerifier>()
    val oauthNonceService by inject<OAuthNonceService>()

    routing {
        identityRoutes(atprotoIdentityService)
        assetLinksRoutes(assetLinksConfig)
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
            sessionService = pdsSessionService,
            syncService = pdsSyncService,
            blobService = pdsBlobService,
            atprotoSessionTokenService = atprotoSessionTokenService,
            oauthAccessTokenService = oauthAccessTokenService,
            oauthDpopVerifier = oauthDpopVerifier,
            oauthNonceService = oauthNonceService,
        )
    }
}

/** `/api/v1` account surface: server info, plans, auth, and identity management. */
internal fun Application.accountApiRoutes() {
    val accountRepository by inject<AccountRepository>()
    val accountIdentityRepository by inject<AccountIdentityRepository>()
    val sessionManager by inject<SessionManager>()
    val webAuthnService by inject<WebAuthnPasskeyService>()
    val restoreCredentialService by inject<RestoreCredentialService>()
    val atprotoIdentityService by inject<AtprotoIdentityService>()
    val tokenService by inject<TokenService>()
    val refreshTokenRevocationRepository by inject<RefreshTokenRevocationRepository>()
    val googleIdTokenVerifier by inject<GoogleIdTokenVerifier>()
    val authMetrics by inject<AuthMetricsRegistry>()
    val accountDeletionService by inject<AccountDeletionService>()
    val entitlementService by inject<EntitlementService>()
    val planCatalogService by inject<PlanCatalogService>()
    val emailVerificationService by inject<EmailVerificationService>()
    val signingKeyService by inject<SigningKeyService>()

    routing {
        route("/api/v1") {
            serverInfoRoutes(serverDescriptor())
            planRoutes(planCatalogService)
            authV1Routes(
                accountRepository = accountRepository,
                identityRepository = accountIdentityRepository,
                sessionManager = sessionManager,
                webAuthnService = webAuthnService,
                restoreCredentialService = restoreCredentialService,
                atprotoIdentityService = atprotoIdentityService,
                tokenService = tokenService,
                refreshTokenRevocationRepository = refreshTokenRevocationRepository,
                googleIdTokenVerifier = googleIdTokenVerifier,
                metrics = authMetrics,
                accountDeletionService = accountDeletionService,
                entitlementService = entitlementService,
                emailVerificationService = emailVerificationService,
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

/** `/api/v1` content surface: sync, quota, resources, and transcription. */
internal fun Application.contentApiRoutes() {
    val accountRepository by inject<AccountRepository>()
    val tokenService by inject<TokenService>()
    val entitlementService by inject<EntitlementService>()
    val entitlementEnforcer by inject<EntitlementEnforcer>()
    val usageCalculator by inject<UsageCalculator>()
    val syncMetrics by inject<SyncMetricsRegistry>()
    val blobStorage by inject<LogDateBlobStorage>()
    val logDateCollectionsRepository by inject<RepoBackedLogDateCollectionsRepository>()
    val logDateMediaBlobRepository by inject<CompositeLogDateMediaBlobRepository>()
    val logDateBackupRepository by inject<LogDateBackupRepository>()
    val resourceRouteRepository by inject<ResourceRouteRepository>()
    val httpClient by inject<HttpClient>()
    val syncRateLimiter = SlidingWindowRateLimiter()

    routing {
        route("/api/v1") {
            syncRoutes(
                tokenService = tokenService,
                mediaStorage = blobStorage,
                metrics = syncMetrics,
                collectionsRepository = logDateCollectionsRepository,
                mediaBlobRepository = logDateMediaBlobRepository,
                backupRepository = logDateBackupRepository,
                entitlementEnforcer = entitlementEnforcer,
                rateLimiter = syncRateLimiter,
            )
            quotaRoutes(
                tokenService = tokenService,
                entitlementService = entitlementService,
                usageCalculator = usageCalculator,
            )
            resourceRoutes(
                accountRepository = accountRepository,
                collectionsRepository = logDateCollectionsRepository,
                resourceRouteRepository = resourceRouteRepository,
            )
            transcriptionRoutes(
                tokenService = tokenService,
                entitlementService = entitlementService,
                sessionProvider = cloudTranscriptionSessionProviderFromEnvironment(httpClient),
            )
        }
    }
}

private fun Application.serverDescriptor() =
    inject<ServerDescriptorConfig>().value.toDescriptor(
        identityConfig = inject<AtprotoIdentityConfig>().value,
        webAuthnRpId = inject<WebAuthnConfig>().value.relyingPartyId,
        webAuthnRpName = inject<WebAuthnConfig>().value.relyingPartyName,
    )
