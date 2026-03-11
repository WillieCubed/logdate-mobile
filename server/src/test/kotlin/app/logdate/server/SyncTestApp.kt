package app.logdate.server

import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.InMemoryLogDateBackupRepository
import app.logdate.server.logdate.InMemoryLogDateCollectionsMetadataStore
import app.logdate.server.logdate.InMemoryLogDateMediaRepository
import app.logdate.server.logdate.RepoBackedLogDateCollectionsRepository
import app.logdate.server.routes.syncRoutes
import app.logdate.server.sync.GcsMediaStorage
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.MediaAccessPolicy
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
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class SyncTestEnvironment(
    val tokenService: JwtTokenService,
    val repository: InMemorySyncRepository,
    val mediaRepository: InMemoryLogDateMediaRepository,
    val backupRepository: InMemoryLogDateBackupRepository,
    val metrics: SyncMetricsRegistry,
)

@OptIn(ExperimentalUuidApi::class)
fun TestApplicationBuilder.configureSyncTestApp(
    tokenService: JwtTokenService = JwtTokenService("test-secret-for-sync-tests"),
    repository: InMemorySyncRepository = InMemorySyncRepository(),
    metrics: SyncMetricsRegistry = SyncMetricsRegistry(),
    mediaStorage: GcsMediaStorage? = null,
    mediaAccessPolicy: MediaAccessPolicy = MediaAccessPolicy(useSignedUrls = false, signedUrlTtlHours = 1),
): SyncTestEnvironment {
    val accountRepository = InMemoryAccountRepository()
    val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "sync-test-kek")
    val atprotoIdentityService =
        AtprotoIdentityService(
            accountRepository = accountRepository,
            signingKeyService = signingKeyService,
            config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
        )
    val collectionsRepository =
        RepoBackedLogDateCollectionsRepository(
            accountRepository = accountRepository,
            identityService = atprotoIdentityService,
            signingKeyService = signingKeyService,
            blockStore = InMemoryRepoBlockStore(),
            metadataStore = InMemoryLogDateCollectionsMetadataStore(),
        )
    val mediaRepository = InMemoryLogDateMediaRepository()
    val backupRepository = InMemoryLogDateBackupRepository()
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
            route("/api/v1") {
                syncRoutes(
                    tokenService = tokenService,
                    mediaStorage = mediaStorage,
                    metrics = metrics,
                    mediaAccessPolicy = mediaAccessPolicy,
                    collectionsRepository = collectionsRepository,
                    mediaRepository = mediaRepository,
                    backupRepository = backupRepository,
                )
            }
        }
    }

    return SyncTestEnvironment(tokenService, repository, mediaRepository, backupRepository, metrics)
}
