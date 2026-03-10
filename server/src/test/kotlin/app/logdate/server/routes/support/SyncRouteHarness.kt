package app.logdate.server.routes.support

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.logdate.asLogDateBackupRepository
import app.logdate.server.logdate.asLogDateCollectionsRepository
import app.logdate.server.logdate.asLogDateMediaRepository
import app.logdate.server.routes.syncRoutes
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.SyncMetricsRegistry
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import java.util.UUID

fun ApplicationTestBuilder.configureInMemorySyncApp(secret: String = "sync-test-secret"): JwtTokenService {
    val tokenService = JwtTokenService(secret)
    val repository = InMemorySyncRepository()
    application {
        install(ContentNegotiation) { json() }
        routing {
            route("/api/v1") {
                syncRoutes(
                    tokenService = tokenService,
                    mediaStorage = null,
                    metrics = SyncMetricsRegistry(),
                    collectionsRepository = repository.asLogDateCollectionsRepository(),
                    mediaRepository = repository.asLogDateMediaRepository(),
                    backupRepository = repository.asLogDateBackupRepository(),
                )
            }
        }
    }
    return tokenService
}

fun authHeader(tokenService: JwtTokenService): String = "Bearer ${tokenService.generateAccessToken(UUID.randomUUID().toString())}"
