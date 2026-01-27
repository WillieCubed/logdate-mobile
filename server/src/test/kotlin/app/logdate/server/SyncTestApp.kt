package app.logdate.server

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.routes.syncRoutes
import app.logdate.server.sync.GcsMediaStorage
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.MediaAccessPolicy
import app.logdate.server.sync.MediaEncryptionService
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.util.UuidSerializer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class SyncTestEnvironment(
    val tokenService: JwtTokenService,
    val repository: InMemorySyncRepository,
    val metrics: SyncMetricsRegistry
)

@OptIn(ExperimentalUuidApi::class)
fun TestApplicationBuilder.configureSyncTestApp(
    tokenService: JwtTokenService = JwtTokenService("test-secret-for-sync-tests"),
    repository: InMemorySyncRepository = InMemorySyncRepository(),
    metrics: SyncMetricsRegistry = SyncMetricsRegistry(),
    mediaStorage: GcsMediaStorage? = null,
    mediaAccessPolicy: MediaAccessPolicy = MediaAccessPolicy(useSignedUrls = false, signedUrlTtlHours = 1),
    mediaEncryption: MediaEncryptionService = MediaEncryptionService.fromEnvironment()
): SyncTestEnvironment {
    val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            contextual(Uuid::class, UuidSerializer)
        }
    }

    application {
        install(ContentNegotiation) {
            json(json)
        }
        routing {
            route("/api/v1") {
                syncRoutes(
                    repository = repository,
                    tokenService = tokenService,
                    mediaStorage = mediaStorage,
                    metrics = metrics,
                    mediaAccessPolicy = mediaAccessPolicy,
                    mediaEncryption = mediaEncryption
                )
            }
        }
    }

    return SyncTestEnvironment(tokenService, repository, metrics)
}
