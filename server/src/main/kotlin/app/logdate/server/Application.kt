package app.logdate.server

import app.logdate.SERVER_PORT
import app.logdate.server.atproto.LogDateRepoStore
import app.logdate.server.auth.AccountIdentityRepository
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.AuthMetricsRegistry
import app.logdate.server.auth.GoogleIdTokenVerifier
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.SessionManager
import app.logdate.server.di.initializeDatabase
import app.logdate.server.di.serverModule
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.LogDateCollectionsMetadataStore
import app.logdate.server.logdate.RepoBackedLogDateCollectionsRepository
import app.logdate.server.oauth.OAuthAccessTokenService
import app.logdate.server.oauth.OAuthAuthorizationService
import app.logdate.server.oauth.OAuthConfig
import app.logdate.server.oauth.OAuthDpopVerifier
import app.logdate.server.oauth.OAuthKeyService
import app.logdate.server.oauth.OAuthNonceService
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
import studio.hypertext.atproto.pds.runtime.DefaultPdsRepoService
import studio.hypertext.atproto.pds.runtime.StaticPdsDiscoveryService
import studio.hypertext.atproto.repo.RepoBlockStore
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() {
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

    install(Koin) {
        slf4jLogger()
        modules(serverModule(isDatabaseAvailable))
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
    val atprotoIdentityService: AtprotoIdentityService by inject()
    val serverDescriptorConfig: ServerDescriptorConfig by inject()
    val signingKeyService: SigningKeyService by inject()
    val oauthConfig: OAuthConfig by inject()
    val oauthKeyService: OAuthKeyService by inject()
    val oauthNonceService: OAuthNonceService by inject()
    val oauthDpopVerifier: OAuthDpopVerifier by inject()
    val oauthAccessTokenService: OAuthAccessTokenService by inject()
    val oauthAuthorizationService: OAuthAuthorizationService by inject()
    val webAuthnConfig: WebAuthnConfig by inject()
    val repoBlockStore: RepoBlockStore by inject()
    val logDateCollectionsMetadataStore: LogDateCollectionsMetadataStore by inject()
    val logDateCollectionsRepository =
        RepoBackedLogDateCollectionsRepository(
            accountRepository = accountRepository,
            identityService = atprotoIdentityService,
            blockStore = repoBlockStore,
            metadataStore = logDateCollectionsMetadataStore,
        )
    val logDateRepoStore =
        LogDateRepoStore(
            collectionsRepository = logDateCollectionsRepository,
            identityService = atprotoIdentityService,
            blockStore = repoBlockStore,
        )
    atprotoIdentityService.setRepoCollectionsResolver(logDateRepoStore::collectionsForDid)
    val pdsRepoService = DefaultPdsRepoService(logDateRepoStore)
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
            oauthAccessTokenService = oauthAccessTokenService,
            oauthDpopVerifier = oauthDpopVerifier,
            oauthNonceService = oauthNonceService,
        )

        route("/api/v1") {
            val mediaStorage = GcsMediaStorage.fromEnvironment()
            serverInfoRoutes(serverDescriptor)
            authV1Routes(
                accountRepository = accountRepository,
                identityRepository = accountIdentityRepository,
                sessionManager = sessionManager,
                webAuthnService = webAuthnService,
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
                repository = syncRepository,
                tokenService = tokenService,
                mediaStorage = mediaStorage,
                metrics = syncMetrics,
                collectionsRepository = logDateCollectionsRepository,
            )
        }
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
