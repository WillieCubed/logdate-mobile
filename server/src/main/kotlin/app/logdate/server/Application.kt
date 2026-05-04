package app.logdate.server

import app.logdate.SERVER_PORT
import app.logdate.server.atproto.AtprotoSessionTokenService
import app.logdate.server.auth.AccountDeletionService
import app.logdate.server.auth.AccountIdentityRepository
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.AuthMetricsRegistry
import app.logdate.server.auth.GoogleIdTokenVerifier
import app.logdate.server.auth.SessionManager
import app.logdate.server.auth.TokenService
import app.logdate.server.config.ProductionConfigValidator
import app.logdate.server.config.RuntimeProfile
import app.logdate.server.config.profileAwareBoolEnv
import app.logdate.server.di.initializeDatabase
import app.logdate.server.di.serverModule
import app.logdate.server.entitlements.EntitlementEnforcer
import app.logdate.server.entitlements.EntitlementService
import app.logdate.server.entitlements.entitlementsModule
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.CompositeLogDateMediaBlobRepository
import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.RepoBackedLogDateCollectionsRepository
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
import app.logdate.server.routes.authV1Routes
import app.logdate.server.routes.identityApiRoutes
import app.logdate.server.routes.identityRoutes
import app.logdate.server.routes.oauthRoutes
import app.logdate.server.routes.serverInfoRoutes
import app.logdate.server.routes.syncRoutes
import app.logdate.server.routes.xrpcRoutes
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.server.sync.SyncRepository
import app.logdate.util.UuidSerializer
import io.github.aakira.napier.Napier
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.httpsredirect.HttpsRedirect
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import studio.hypertext.atproto.pds.PdsBlobService
import studio.hypertext.atproto.pds.PdsDiscoveryService
import studio.hypertext.atproto.pds.PdsRepoService
import studio.hypertext.atproto.pds.PdsSessionService
import studio.hypertext.atproto.pds.PdsSyncService
import java.net.URI
import java.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() {
    val profile = RuntimeProfile.fromEnvironment()
    ProductionConfigValidator.validate(profile)

    val isDatabaseAvailable = initializeDatabase()

    val port = System.getProperty("PORT")?.toIntOrNull() ?: System.getenv("PORT")?.toIntOrNull() ?: SERVER_PORT
    val host = System.getProperty("HOST") ?: System.getenv("HOST") ?: "0.0.0.0"
    val wait = System.getProperty("LOGDATE_SERVER_WAIT")?.toBooleanStrictOrNull() ?: true

    val engine = buildMainServer(isDatabaseAvailable, port, host)
    engine.start(wait = wait)
    if (!wait) {
        engine.stop(gracePeriodMillis = 0, timeoutMillis = 0)
    }
}

private fun buildMainServer(
    isDatabaseAvailable: Boolean,
    port: Int,
    host: String,
) = embeddedServer(Netty, port = port, host = host) {
    module(isDatabaseAvailable)
}

@OptIn(ExperimentalUuidApi::class)
fun Application.module(isDatabaseAvailable: Boolean = false) {
    val profile = RuntimeProfile.fromEnvironment()
    install(OpenApi) {
        security {
            securityScheme("bearerAuth") {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "JWT"
                description = "JWT bearer token for authenticated endpoints."
            }
        }
        info {
            title = "LogDate Server API"
            version = "1.0.0"
            description = "Machine-readable contract for LogDate auth and sync endpoints."
        }
        if (!profile.isProduction) {
            server {
                url = "http://localhost:8080"
                description = "Local development server"
            }
        }
    }

    // Stop any existing Koin instance to ensure clean state for tests
    runCatching {
        org.koin.core.context
            .stopKoin()
    }

    installNetworkEdge()

    install(Koin) {
        slf4jLogger()
        modules(
            serverModule(isDatabaseAvailable),
            entitlementsModule(databaseAvailable = isDatabaseAvailable),
        )
    }

    val accountRepository by inject<AccountRepository>()
    val accountIdentityRepository by inject<AccountIdentityRepository>()
    val sessionManager by inject<SessionManager>()
    val webAuthnService by inject<WebAuthnPasskeyService>()
    val restoreCredentialService by inject<RestoreCredentialService>()
    val atprotoIdentityService by inject<AtprotoIdentityService>()
    val tokenService by inject<TokenService>()
    val googleIdTokenVerifier by inject<GoogleIdTokenVerifier>()
    val authMetrics by inject<AuthMetricsRegistry>()
    val accountDeletionService by inject<AccountDeletionService>()
    val entitlementService by inject<EntitlementService>()
    val syncRepository by inject<SyncRepository>()
    val syncMetrics by inject<SyncMetricsRegistry>()
    val logDateCollectionsRepository by inject<RepoBackedLogDateCollectionsRepository>()
    val logDateMediaBlobRepository by inject<CompositeLogDateMediaBlobRepository>()
    val logDateBackupRepository by inject<LogDateBackupRepository>()
    val blobStorage by inject<LogDateBlobStorage>()
    val entitlementEnforcer by inject<EntitlementEnforcer>()
    val signingKeyService by inject<SigningKeyService>()

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

    val serverDescriptorConfig by inject<ServerDescriptorConfig>()
    val atprotoIdentityConfig by inject<AtprotoIdentityConfig>()
    val webAuthnConfig by inject<WebAuthnConfig>()

    val serverDescriptor =
        serverDescriptorConfig.toDescriptor(
            identityConfig = atprotoIdentityConfig,
            webAuthnRpId = webAuthnConfig.relyingPartyId,
            webAuthnRpName = webAuthnConfig.relyingPartyName,
        )

    val syncRateLimiter = SlidingWindowRateLimiter()

    val maintenanceReadEnv: (String) -> String? =
        if (isDatabaseAvailable) {
            System::getenv
        } else {
            { name -> if (name == "SYNC_TOMBSTONE_PURGE_ENABLED") "false" else null }
        }
    val maintenanceJob: Job? = startSyncMaintenance(syncRepository, syncMetrics, maintenanceReadEnv)
    monitor.subscribe(ApplicationStopped) {
        maintenanceJob?.cancel()
        runCatching {
            org.koin.core.context
                .stopKoin()
        }
    }

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                serializersModule =
                    SerializersModule {
                        contextual(Uuid::class, UuidSerializer)
                    }
            },
        )
    }

    routing {
        route("openapi.json") {
            openApi()
        }
        route("openapi.yaml") {
            openApi()
        }
        route("swagger") {
            swaggerUI("/openapi.json")
        }

        get("/") {
            call.respondText("LogDate Server API v1.0")
        }

        get("/health") {
            val status =
                mapOf(
                    "status" to "healthy",
                    "timestamp" to
                        kotlin.time.Clock.System
                            .now()
                            .toString(),
                    "version" to "1.0.0",
                )
            call.respond(status)
        }

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
            sessionService = pdsSessionService,
            syncService = pdsSyncService,
            blobService = pdsBlobService,
            atprotoSessionTokenService = atprotoSessionTokenService,
            oauthAccessTokenService = oauthAccessTokenService,
            oauthDpopVerifier = oauthDpopVerifier,
            oauthNonceService = oauthNonceService,
        )

        route("/api/v1") {
            serverInfoRoutes(serverDescriptor)
            authV1Routes(
                accountRepository = accountRepository,
                identityRepository = accountIdentityRepository,
                sessionManager = sessionManager,
                webAuthnService = webAuthnService,
                restoreCredentialService = restoreCredentialService,
                atprotoIdentityService = atprotoIdentityService,
                tokenService = tokenService,
                googleIdTokenVerifier = googleIdTokenVerifier,
                metrics = authMetrics,
                accountDeletionService = accountDeletionService,
                entitlementService = entitlementService,
            )
            identityApiRoutes(
                accountRepository = accountRepository,
                tokenService = tokenService,
                atprotoIdentityService = atprotoIdentityService,
                signingKeyService = signingKeyService,
            )
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
        }
    }
}

/**
 * Installs the CORS, forwarded-headers, and HTTPS-redirect plugins based on environment
 * configuration.
 *
 * Knobs:
 *  - `ALLOWED_ORIGINS` — comma-separated `scheme://host[:port]` entries trusted for CORS. Unset or
 *    blank disables the CORS plugin entirely (same-origin only). No default in any profile.
 *  - `TRUST_FORWARDED_HEADERS` — when `true`, installs [XForwardedHeaders] so the request's
 *    `scheme` / `remoteHost` reflect what the load balancer saw from the client rather than what
 *    the LB's backend socket looks like. **Defaults to `true` in production** (the typical deploy
 *    is behind Cloud Run / an ALB / GKE Ingress / Cloudflare, all of which forward plain HTTP to
 *    the container). Set `false` when the app is directly internet-facing — otherwise a malicious
 *    client could lie about their scheme with `X-Forwarded-Proto: https`.
 *  - `REQUIRE_HTTPS` — when `true`, installs [HttpsRedirect]. **Defaults to `true` in production.**
 *    Combined with [XForwardedHeaders] above, a request the LB received over HTTPS will pass
 *    through (scheme reads as `https`) while a request the LB received over HTTP gets 301'd. With
 *    [HttpsRedirect], any request that comes in as `http` (but not from `localhost` or `127.0.0.1`)
 *    is redirected to `https`.
 */
internal fun Application.installNetworkEdge(
    allowedOrigins: String = System.getenv("ALLOWED_ORIGINS") ?: "",
    trustForwarded: Boolean = profileAwareBoolEnv("TRUST_FORWARDED_HEADERS", productionDefault = true, devDefault = true),
    requireHttps: Boolean = profileAwareBoolEnv("REQUIRE_HTTPS", productionDefault = true, devDefault = false),
) {
    val parsedOrigins = parseAllowedOrigins(allowedOrigins)
    if (parsedOrigins.isNotEmpty()) {
        install(CORS) {
            parsedOrigins.forEach { origin ->
                val hostAndPort =
                    buildString {
                        append(origin.host)
                        origin.port?.let { append(":").append(it) }
                    }
                allowHost(host = hostAndPort, schemes = listOf(origin.scheme))
            }
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Patch)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowCredentials = true
        }
    }

    if (trustForwarded) {
        install(XForwardedHeaders)
    }

    if (requireHttps) {
        install(HttpsRedirect)
    }
}

/**
 * Parses a comma-separated list of origin URIs into a list of [AllowedOrigin] structures.
 * Malformed entries are logged and skipped.
 */
internal fun parseAllowedOrigins(raw: String?): List<AllowedOrigin> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { originStr ->
            runCatching {
                val uri = URI(originStr)
                requireNotNull(uri.scheme) { "Missing scheme in origin: $originStr" }
                requireNotNull(uri.host) { "Missing host in origin: $originStr" }
                AllowedOrigin(
                    scheme = uri.scheme,
                    host = uri.host,
                    port = if (uri.port != -1) uri.port else null,
                )
            }.getOrElse { error ->
                Napier.w("Skipping malformed ALLOWED_ORIGINS entry: $originStr", error)
                null
            }
        }
}

/**
 * Structured breakdown of a trusted origin for CORS configuration.
 */
internal data class AllowedOrigin(
    val scheme: String,
    val host: String,
    val port: Int? = null,
)

private fun startSyncMaintenance(
    syncRepository: SyncRepository,
    metrics: SyncMetricsRegistry,
    readEnv: (String) -> String?,
): Job? {
    val enabled = readEnv("SYNC_TOMBSTONE_PURGE_ENABLED")?.toBooleanStrictOrNull() ?: true
    if (!enabled) return null

    val intervalMinutes = readEnv("SYNC_TOMBSTONE_PURGE_INTERVAL_MINUTES")?.toLongOrNull() ?: 60L
    val retentionDays = readEnv("SYNC_TOMBSTONE_RETENTION_DAYS")?.toLongOrNull() ?: 30L

    return runBlocking(Dispatchers.Default) {
        launch {
            while (isActive) {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val cutoff = System.currentTimeMillis() - retentionDays * 24 * 60 * 60 * 1000
                    syncRepository.purgeTombstonesOlderThan(cutoff)
                    Napier.i("Purged sync tombstones older than $retentionDays days")
                    success = true
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Napier.e("Failed to purge sync tombstones", e)
                } finally {
                    metrics.recordOperation(
                        "maintenance.tombstone_purge",
                        System.currentTimeMillis() - start,
                        success,
                    )
                }
                delay(Duration.ofMinutes(intervalMinutes).toMillis())
            }
        }
    }
}
