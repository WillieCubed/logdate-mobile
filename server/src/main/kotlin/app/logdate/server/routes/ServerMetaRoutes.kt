package app.logdate.server.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest

private const val HEALTH_TOKEN_HEADER = "X-LogDate-Health-Token"

private val landingPageHtml: String by lazy {
    val resource =
        Application::class.java.classLoader.getResource("public/index.html")
            ?: error("public/index.html missing from server resources")
    resource.readText()
}

private val rootDescriptorJson: JsonObject by lazy {
    buildJsonObject {
        put("name", "LogDate Server API")
        put("version", "1.0.0")
        put("docs", "/docs")
        put("openapi", "/openapi.json")
        put("openapi_yaml", "/openapi.yaml")
        put("health", "/health")
    }
}

/**
 * The landing page / root descriptor and the health probe.
 *
 * Single /health route — public payload by default, db_connected unlocked when the
 * X-LogDate-Health-Token header matches HEALTH_INTERNAL_TOKEN. See
 * docs/observability/health-endpoint.md for the design rationale.
 */
internal fun Route.serverMetaRoutes(
    isDatabaseAvailable: Boolean,
    healthInternalToken: String,
    releaseVersion: String,
) {
    // Captured once at route registration so the per-request token check doesn't allocate a new
    // ByteArray on every probe.
    val healthInternalTokenBytes = healthInternalToken.toByteArray()

    get("/") {
        val accept = call.request.headers[HttpHeaders.Accept].orEmpty()
        if (accept.contains("text/html", ignoreCase = true)) {
            call.respondText(landingPageHtml, ContentType.Text.Html)
        } else {
            call.respond(rootDescriptorJson)
        }
    }

    get("/health") {
        val provided = call.request.headers[HEALTH_TOKEN_HEADER]
        val tokenMatches =
            provided != null &&
                healthInternalTokenBytes.isNotEmpty() &&
                MessageDigest.isEqual(healthInternalTokenBytes, provided.toByteArray())
        call.respond(
            buildJsonObject {
                put("status", "healthy")
                put(
                    "timestamp",
                    kotlin.time.Clock.System
                        .now()
                        .toString(),
                )
                put("version", "1.0.0")
                put("release", releaseVersion)
                if (tokenMatches) {
                    put("db_connected", isDatabaseAvailable)
                }
            },
        )
    }
}
