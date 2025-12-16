package app.logdate.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
    val timestamp: String,
    val version: String
)

fun testDatabase(): String {
    return try {
        val databaseUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:15432/logdate"
        val user = System.getenv("DATABASE_USER") ?: "logdate"
        val password = System.getenv("DATABASE_PASSWORD") ?: "logdate"
        
        val config = HikariConfig().apply {
            jdbcUrl = databaseUrl
            username = user
            this.password = password
            driverClassName = "org.postgresql.Driver"
        }
        
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)
        
        transaction {
            // Simple test query
            val result = exec("SELECT version();") { rs ->
                rs.next()
                rs.getString(1)
            }
            
            "Connected: ${result?.take(50)}..."
        } ?: "Connected but no version info"
        
    } catch (e: Exception) {
        "Database error: ${e.message}"
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        
        routing {
            get("/") {
                call.respondText("LogDate Test Server - Docker Integration Working!")
            }
            
            get("/health") {
                val health = HealthResponse(
                    status = "healthy",
                    database = testDatabase(),
                    timestamp = kotlinx.datetime.Clock.System.now().toString(),
                    version = "test-1.0.0"
                )
                call.respond(health)
            }
            
            get("/test") {
                call.respond(mapOf(
                    "message" to "Docker integration test successful!",
                    "environment" to mapOf(
                        "DATABASE_URL" to System.getenv("DATABASE_URL"),
                        "DATABASE_USER" to System.getenv("DATABASE_USER"),
                        "REDIS_URL" to System.getenv("REDIS_URL"),
                        "PORT" to System.getenv("PORT")
                    )
                ))
            }
        }
    }.start(wait = true)
}