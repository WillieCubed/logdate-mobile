package app.logdate.server

import app.logdate.SERVER_PORT
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.di.initializeDatabase
import app.logdate.server.di.serverModule
import app.logdate.server.routes.accountRoutes
import app.logdate.server.routes.*
import app.logdate.server.sync.SyncRepository
import app.logdate.util.UuidSerializer
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi
import org.koin.ktor.plugin.Koin
import org.koin.ktor.ext.inject
import org.koin.logger.slf4jLogger

fun main() {
    val isDatabaseAvailable = initializeDatabase()

    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0") {
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

    environment.monitor.subscribe(ApplicationStopped) {
        try {
            org.koin.core.context.stopKoin()
        } catch (_: Exception) {
            // Ignore if already stopped
        }
    }

    val syncRepository: SyncRepository by inject()
    val tokenService: JwtTokenService by inject()

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
                    "timestamp" to kotlinx.datetime.Clock.System.now().toString(),
                    "version" to "1.0.0"
                )
                call.respond(status)
            } catch (e: Exception) {
                val status = mapOf(
                    "status" to "unhealthy",
                    "error" to e.message,
                    "timestamp" to kotlinx.datetime.Clock.System.now().toString()
                )
                call.respond(io.ktor.http.HttpStatusCode.ServiceUnavailable, status)
            }
        }

        route("/api/v1") {
            authRoutes()
            accountRoutes()
            passkeyRoutes()
            journalRoutes()
            notesRoutes()
            draftRoutes()
            mediaRoutes()
            syncRoutes(syncRepository, tokenService)
            aiRoutes()
            deviceRoutes()
            rewindRoutes()
            timelineRoutes()
        }
    }
}
