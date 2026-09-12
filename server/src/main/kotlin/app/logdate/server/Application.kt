package app.logdate.server

import app.logdate.SERVER_PORT
import app.logdate.server.config.ProductionConfigValidator
import app.logdate.server.config.RuntimeProfile
import app.logdate.server.di.initializeDatabase
import app.logdate.server.di.installServerKoin
import app.logdate.server.logging.initializeSentry
import app.logdate.server.logging.installServerLogging
import app.logdate.server.routes.accountApiRoutes
import app.logdate.server.routes.atprotoRoutes
import app.logdate.server.routes.contentApiRoutes
import app.logdate.server.routes.serverMetaRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing

private const val HEALTH_TOKEN_ENV = "HEALTH_INTERNAL_TOKEN"

fun main() {
    // First: until an antilog is registered Napier discards everything, so anything logged
    // before this line is lost — including why startup validation refused to continue.
    installServerLogging()

    val profile = RuntimeProfile.fromEnvironment()
    ProductionConfigValidator.validate(profile)

    initializeSentry(profile)

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

/**
 * Assembles the server. Each line installs one concern; the details live next to the code they
 * configure (see docs/reference/standards/entrypoint-structure.md).
 */
fun Application.module(
    isDatabaseAvailable: Boolean = false,
    healthInternalToken: String = System.getenv(HEALTH_TOKEN_ENV).orEmpty(),
    releaseVersion: String = System.getenv("RELEASE_VERSION").orEmpty(),
) {
    val openApiSpec = installOpenApi()
    installNetworkEdge()
    installServerKoin(isDatabaseAvailable)
    installSyncMaintenance(isDatabaseAvailable)
    installJsonSerialization()

    routing {
        openApiRoutes(openApiSpec)
        serverMetaRoutes(isDatabaseAvailable, healthInternalToken, releaseVersion)
    }
    atprotoRoutes()
    accountApiRoutes()
    contentApiRoutes()
}
