package app.logdate.server

import app.logdate.SERVER_PORT
import app.logdate.server.atproto.AtprotoPasswordService
import app.logdate.server.atproto.AtprotoPdsSessionService
import app.logdate.server.atproto.AtprotoSessionTokenService
import app.logdate.server.atproto.LogDatePdsBlobStore
import app.logdate.server.atproto.LogDateRepoStore
import app.logdate.server.auth.AccountIdentityRepository
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.AuthMetricsRegistry
import app.logdate.server.auth.GoogleIdTokenVerifier
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.SessionManager
import app.logdate.server.config.ProductionConfigValidator
import app.logdate.server.config.RuntimeProfile
import app.logdate.server.config.profileAwareBoolEnv
import app.logdate.server.di.initializeDatabase
import app.logdate.server.di.serverModule
import app.logdate.server.entitlements.EntitlementEnforcer
import app.logdate.server.entitlements.entitlementsModule
import app.logdate.server.ratelimit.SlidingWindowRateLimiter
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.CompositeLogDateMediaBlobRepository
import app.logdate.server.logdate.FilesystemLogDateBlobStorage
import app.logdate.server.logdate.LogDateAtprotoBlobRepository
import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateCollectionsMetadataStore
import app.logdate.server.logdate.LogDateMediaRepository
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
import app.logdate.server.routes.authV1Routes
import app.logdate.server.routes.identityApiRoutes
import app.logdate.server.routes.identityRoutes
import app.logdate.server.routes.oauthRoutes
import app.logdate.server.routes.openApiRoutes
import app.logdate.server.routes.serverInfoRoutes
import app.logdate.server.routes.syncRoutes
import app.logdate.server.routes.xrpcRoutes
import app.logdate.server.sync.GcsMediaStorage
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.server.sync.SyncRepository
import app.logdate.util.UuidSerializer
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.httpsredirect.HttpsRedirect
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.registerBearerAuthSecurityScheme
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
import studio.hypertext.atproto.pds.DescribeServerResponse
import studio.hypertext.atproto.pds.runtime.DefaultPdsBlobService
import studio.hypertext.atproto.pds.runtime.DefaultPdsRepoService
import studio.hypertext.atproto.pds.runtime.DefaultPdsSyncService
import studio.hypertext.atproto.pds.runtime.StaticPdsDiscoveryService
import studio.hypertext.atproto.repo.RepoBlockStore
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() {
    ProductionConfigValidator.validate(RuntimeProfile.fromEnvironment())

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
    registerBearerAuthSecurityScheme(
        name = "bearerAuth",
        description = "JWT bearer token for authenticated endpoints.",
        bearerFormat = "JWT",
    )

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

    val syncRepository: SyncRepository by inject()
    val syncMetrics: SyncMetricsRegistry by inject()
    val authMetrics: AuthMetricsRegistry by inject()
    val tokenService: JwtTokenService by inject()
    val accountRepository: AccountRepository by inject()
    val accountIdentityRepository: AccountIdentityRepository by inject()
    val googleIdTokenVerifier: GoogleIdTokenVerifier by inject()
    val sessionManager: SessionManager by inject()
    val webAuthnService: WebAuthnPasskeyService by inject()
    val restoreCredentialService: RestoreCredentialService by inject()
    val atprotoIdentityService: AtprotoIdentityService by inject()
    val serverDescriptorConfig: ServerDescriptorConfig by inject()
    val signingKeyService: SigningKeyService by inject()
    val oauthConfig: OAuthConfig by inject()
    val oauthKeyService: OAuthKeyService by inject()
    val oauthNonceService: OAuthNonceService by inject()
    val oauthDpopVerifier: OAuthDpopVerifier by inject()
    val oauthAccessTokenService: OAuthAccessTokenService by inject()
    val oauthAuthorizationService: OAuthAuthorizationService by inject()
    val atprotoPasswordService: AtprotoPasswordService by inject()
    val atprotoSessionTokenService: AtprotoSessionTokenService by inject()
    val atprotoPdsSessionService: AtprotoPdsSessionService by inject()
    val webAuthnConfig: WebAuthnConfig by inject()
    val repoBlockStore: RepoBlockStore by inject()
    val logDateCollectionsMetadataStore: LogDateCollectionsMetadataStore by inject()
    val logDateMediaRepository: LogDateMediaRepository by inject()
    val logDateBackupRepository: LogDateBackupRepository by inject()
    val entitlementEnforcer: EntitlementEnforcer by inject()
    val syncRateLimiter = SlidingWindowRateLimiter()
    val logDateAtprotoBlobRepository: LogDateAtprotoBlobRepository by inject()
    val logDateMediaBlobRepository =
        CompositeLogDateMediaBlobRepository(
            mediaRepository = logDateMediaRepository,
            atprotoBlobRepository = logDateAtprotoBlobRepository,
        )
    val blobStorage: LogDateBlobStorage? =
        GcsMediaStorage.fromEnvironment()
            ?: FilesystemLogDateBlobStorage.fromEnvironment()
    if (blobStorage == null) {
        log.warn(
            "No blob storage configured: media and backup endpoints will reject uploads. " +
                "Set GCS_* for Google Cloud Storage or LOGDATE_BLOB_STORAGE_DIR for an on-disk store.",
        )
    }
    val logDateCollectionsRepository =
        RepoBackedLogDateCollectionsRepository(
            accountRepository = accountRepository,
            identityService = atprotoIdentityService,
            signingKeyService = signingKeyService,
            blockStore = repoBlockStore,
            metadataStore = logDateCollectionsMetadataStore,
        )
    val logDateRepoStore =
        LogDateRepoStore(
            collectionsRepository = logDateCollectionsRepository,
            identityService = atprotoIdentityService,
            signingKeyService = signingKeyService,
            accountRepository = accountRepository,
            blockStore = repoBlockStore,
        )
    atprotoIdentityService.setRepoCollectionsResolver(logDateRepoStore::collectionsForDid)
    val pdsRepoService = DefaultPdsRepoService(logDateRepoStore)
    val pdsSyncService = DefaultPdsSyncService(logDateRepoStore)
    val pdsBlobService =
        blobStorage?.let { configuredStorage ->
            DefaultPdsBlobService(
                LogDatePdsBlobStore(
                    identityService = atprotoIdentityService,
                    mediaBlobRepository = logDateMediaBlobRepository,
                    blobStorage = configuredStorage,
                ),
            )
        }
    val pdsDiscoveryService =
        StaticPdsDiscoveryService(
            authorizationServerMetadata = oauthConfig.authorizationServerMetadata(),
            protectedResourceMetadata = oauthConfig.protectedResourceMetadata(),
            describeServerResponse =
                DescribeServerResponse(
                    did = atprotoIdentityService.config.serverDid,
                    availableUserDomains = listOf(atprotoIdentityService.config.normalizedHandleDomain),
                    inviteCodeRequired = false,
                    phoneVerificationRequired = false,
                ),
        )
    val serverDescriptor =
        serverDescriptorConfig.toDescriptor(
            identityConfig = atprotoIdentityService.config,
            webAuthnRpId = webAuthnConfig.relyingPartyId,
            webAuthnRpName = webAuthnConfig.relyingPartyName,
        )

    runCatching { runBlocking { atprotoIdentityService.backfillMissingIdentities() } }
        .onFailure { log.warn("Failed to backfill AT Protocol identities on startup", it) }

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
        openApiRoutes()

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
            sessionService = atprotoPdsSessionService,
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
 *    `TRUST_FORWARDED_HEADERS=false` the redirect looks at the raw socket scheme — correct for
 *    direct-facing deployments, wrong for LB-fronted ones.
 */
internal fun Application.installNetworkEdge(readEnv: (String) -> String? = System::getenv) {
    val allowedOrigins = parseAllowedOrigins(readEnv("ALLOWED_ORIGINS"))
    if (allowedOrigins.isNotEmpty()) {
        install(CORS) {
            allowedOrigins.forEach { origin ->
                val scheme = origin.scheme
                val hostWithPort =
                    if (origin.port != null) "${origin.host}:${origin.port}" else origin.host
                allowHost(hostWithPort, schemes = listOf(scheme))
            }
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Patch)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Accept)
            allowCredentials = true
            maxAgeInSeconds = 3600
        }
        log.info("CORS enabled for ${allowedOrigins.size} origin(s): ${allowedOrigins.joinToString { it.raw }}")
    } else {
        log.info("CORS disabled (ALLOWED_ORIGINS not set)")
    }

    val trustForwarded =
        profileAwareBoolEnv(
            "TRUST_FORWARDED_HEADERS",
            productionDefault = true,
            devDefault = false,
            readEnv = readEnv,
        )
    if (trustForwarded) {
        install(XForwardedHeaders)
        log.info("XForwardedHeaders installed; request scheme/host will follow forwarded headers")
    }

    val requireHttps =
        profileAwareBoolEnv(
            "REQUIRE_HTTPS",
            productionDefault = true,
            devDefault = false,
            readEnv = readEnv,
        )
    if (requireHttps) {
        install(HttpsRedirect)
        log.info("HttpsRedirect enabled (production default, or REQUIRE_HTTPS=true)")
    }
}

internal data class AllowedOrigin(
    val raw: String,
    val scheme: String,
    val host: String,
    val port: Int?,
)

internal fun parseAllowedOrigins(raw: String?): List<AllowedOrigin> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { entry ->
            runCatching {
                val uri = java.net.URI(entry)
                val scheme = uri.scheme?.lowercase()
                val host = uri.host
                if (scheme.isNullOrBlank() || host.isNullOrBlank()) return@runCatching null
                AllowedOrigin(
                    raw = entry,
                    scheme = scheme,
                    host = host,
                    port = if (uri.port == -1) null else uri.port,
                )
            }.getOrNull()
        }
}

private const val SYNC_PURGE_METRIC_NAME = "sync.maintenance.purge"
private const val MILLIS_PER_HOUR = 60 * 60 * 1000L
private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR

private fun Application.startSyncMaintenance(
    repository: SyncRepository,
    metrics: SyncMetricsRegistry,
    readEnv: (String) -> String?,
): Job? {
    val enabled = readBooleanEnv("SYNC_TOMBSTONE_PURGE_ENABLED", defaultValue = true, readEnv = readEnv)
    if (!enabled) {
        log.info("Sync tombstone purge disabled by SYNC_TOMBSTONE_PURGE_ENABLED")
        return null
    }

    val retentionDays = readEnv("SYNC_TOMBSTONE_RETENTION_DAYS")?.toLongOrNull() ?: 30L
    val intervalHours = readEnv("SYNC_TOMBSTONE_PURGE_INTERVAL_HOURS")?.toLongOrNull() ?: 24L
    val safeRetentionDays = retentionDays.coerceIn(1L, 3650L)
    val safeIntervalHours = intervalHours.coerceAtLeast(1L)
    val intervalMs = safeIntervalHours * MILLIS_PER_HOUR

    log.info(
        "Starting sync tombstone purge: retentionDays={}, intervalHours={}",
        safeRetentionDays,
        safeIntervalHours,
    )

    return launch(Dispatchers.IO) {
        while (isActive) {
            val start = System.currentTimeMillis()
            val cutoff = System.currentTimeMillis() - (safeRetentionDays * MILLIS_PER_DAY)
            try {
                val result = repository.purgeTombstonesOlderThan(cutoff)
                metrics.recordOperation(SYNC_PURGE_METRIC_NAME, System.currentTimeMillis() - start, true)
                log.info(
                    "Purged sync tombstones older than {} days: content={}, journals={}, associations={}, media={}",
                    safeRetentionDays,
                    result.contentPurged,
                    result.journalPurged,
                    result.associationPurged,
                    result.mediaPurged,
                )
            } catch (e: Exception) {
                metrics.recordOperation(SYNC_PURGE_METRIC_NAME, System.currentTimeMillis() - start, false)
                log.error("Sync tombstone purge failed", e)
            }
            try {
                delay(intervalMs)
            } catch (_: CancellationException) {
                break
            }
        }
    }
}

private fun readBooleanEnv(
    name: String,
    defaultValue: Boolean,
    readEnv: (String) -> String?,
): Boolean {
    val raw = readEnv(name) ?: return defaultValue
    return raw.equals("true", ignoreCase = true) ||
        raw.equals("yes", ignoreCase = true) ||
        raw == "1"
}
