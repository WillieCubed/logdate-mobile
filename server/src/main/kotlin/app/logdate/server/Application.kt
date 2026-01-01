package app.logdate.server

import app.logdate.SERVER_PORT
import app.logdate.server.database.DatabaseConfig
import app.logdate.server.routes.accountRoutes
import app.logdate.server.routes.*
import app.logdate.util.UuidSerializer
import io.github.aakira.napier.Napier
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

fun main() {
    // Initialize repositories (database or in-memory fallback)
    app.logdate.server.database.RepositoryFactory.initializeDatabase()
    
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

@OptIn(ExperimentalUuidApi::class)
fun Application.module() {
    val syncRepository = app.logdate.server.database.RepositoryFactory.createSyncRepository()

    // Configure JSON serialization
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
                // Basic health check - can be enhanced to check database connectivity
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
        
        // API routes
        route("/api/v1") {
            // Import stub routes from StubRoutes.kt
            authRoutes()
            accountRoutes()
            passkeyRoutes()
            journalRoutes()
            notesRoutes()
            draftRoutes()
            mediaRoutes()
            syncRoutes(syncRepository)
            aiRoutes()
            deviceRoutes()
            rewindRoutes()
            timelineRoutes()
        }
    }
}
