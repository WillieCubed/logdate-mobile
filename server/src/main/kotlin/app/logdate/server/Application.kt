package app.logdate.server

import app.logdate.SERVER_PORT
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.SessionManager
import app.logdate.server.di.initializeDatabase
import app.logdate.server.di.serverModule
import app.logdate.server.routes.accountRoutes
import app.logdate.server.routes.*
import app.logdate.server.sync.SyncRepository
import app.logdate.server.sync.GcsMediaStorage
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.util.UuidSerializer
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi
import org.koin.ktor.plugin.Koin
import org.koin.ktor.ext.inject
import org.koin.logger.slf4jLogger

fun main() {
    val isDatabaseAvailable = initializeDatabase()

    val port = System.getenv("PORT")?.toIntOrNull() ?: SERVER_PORT
    val host = System.getenv("HOST") ?: "0.0.0.0"

    embeddedServer(Netty, port = port, host = host) {
        module(isDatabaseAvailable)
    }.start(wait = true)
}

@OptIn(ExperimentalUuidApi::class)
fun Application.module(isDatabaseAvailable: Boolean = false) {
    // Stop any existing Koin instance to ensure clean state for tests
    try {
        org.koin.core.context.stopKoin()
    } catch (_: Exception) {
        // Ignore if Koin wasn't started
    }

    install(Koin) {
        slf4jLogger()
        modules(serverModule(isDatabaseAvailable))
    }

    var maintenanceJob: Job? = null
    monitor.subscribe(ApplicationStopped) {
        maintenanceJob?.cancel()
        try {
            org.koin.core.context.stopKoin()
        } catch (_: Exception) {
            // Ignore if already stopped
        }
    }

    val syncRepository: SyncRepository by inject()
    val syncMetrics: SyncMetricsRegistry by inject()
    val tokenService: JwtTokenService by inject()
    val accountRepository: AccountRepository by inject()
    val sessionManager: SessionManager by inject()
    val webAuthnService: WebAuthnPasskeyService by inject()

    if (isDatabaseAvailable) {
        maintenanceJob = startSyncMaintenance(syncRepository, syncMetrics)
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            serializersModule = SerializersModule {
                contextual(Uuid::class, UuidSerializer)
            }
        })
    }

    routing {
        get("/") {
            call.respondText("LogDate Server API v1.0")
        }

        get("/health") {
            try {
                val status = mapOf(
                    "status" to "healthy",
                    "timestamp" to kotlin.time.Clock.System.now().toString(),
                    "version" to "1.0.0"
                )
                call.respond(status)
            } catch (e: Exception) {
                val status = mapOf(
                    "status" to "unhealthy",
                    "error" to e.message,
                    "timestamp" to kotlin.time.Clock.System.now().toString()
                )
                call.respond(io.ktor.http.HttpStatusCode.ServiceUnavailable, status)
            }
        }

        route("/api/v1") {
            val mediaStorage = GcsMediaStorage.fromEnvironment()
            accountRoutes(
                accountRepository = accountRepository,
                sessionManager = sessionManager,
                webAuthnService = webAuthnService,
                tokenService = tokenService
            )
            syncRoutes(syncRepository, tokenService, mediaStorage, syncMetrics)
        }
    }
}

private const val SYNC_PURGE_METRIC_NAME = "sync.maintenance.purge"
private const val MILLIS_PER_HOUR = 60 * 60 * 1000L
private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR

private fun Application.startSyncMaintenance(
    repository: SyncRepository,
    metrics: SyncMetricsRegistry
): Job? {
    val enabled = readBooleanEnv("SYNC_TOMBSTONE_PURGE_ENABLED", defaultValue = true)
    if (!enabled) {
        log.info("Sync tombstone purge disabled by SYNC_TOMBSTONE_PURGE_ENABLED")
        return null
    }

    val retentionDays = System.getenv("SYNC_TOMBSTONE_RETENTION_DAYS")?.toLongOrNull() ?: 30L
    val intervalHours = System.getenv("SYNC_TOMBSTONE_PURGE_INTERVAL_HOURS")?.toLongOrNull() ?: 24L
    val safeRetentionDays = retentionDays.coerceIn(1L, 3650L)
    val safeIntervalHours = intervalHours.coerceAtLeast(1L)
    val intervalMs = safeIntervalHours * MILLIS_PER_HOUR

    log.info(
        "Starting sync tombstone purge: retentionDays={}, intervalHours={}",
        safeRetentionDays,
        safeIntervalHours
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
                    result.mediaPurged
                )
            } catch (e: Exception) {
                metrics.recordOperation(SYNC_PURGE_METRIC_NAME, System.currentTimeMillis() - start, false)
                log.error("Sync tombstone purge failed", e)
            }
            delay(intervalMs)
        }
    }
}

private fun readBooleanEnv(name: String, defaultValue: Boolean): Boolean {
    val raw = System.getenv(name) ?: return defaultValue
    return raw.equals("true", ignoreCase = true) ||
        raw.equals("yes", ignoreCase = true) ||
        raw == "1"
}
