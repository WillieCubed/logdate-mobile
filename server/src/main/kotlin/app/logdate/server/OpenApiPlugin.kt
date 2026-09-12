package app.logdate.server

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthKeyLocation
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.openApi
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.swagger.v3.core.util.Yaml31
import io.swagger.v3.oas.models.OpenAPI
import java.util.concurrent.atomic.AtomicReference

/**
 * Installs the OpenAPI plugin and returns the holder that receives the built spec once every
 * route has been registered. Pass it to [openApiRoutes].
 */
internal fun Application.installOpenApi(): AtomicReference<OpenAPI?> {
    val openApiSpec = AtomicReference<OpenAPI?>()
    install(OpenApi) {
        security {
            securityScheme("bearerAuth") {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "JWT"
                description = "JWT bearer token for authenticated endpoints."
            }
            securityScheme("dpopProof") {
                type = AuthType.API_KEY
                name = "DPoP"
                location = AuthKeyLocation.HEADER
                description = "DPoP proof JWT bound to the request method and URL."
            }
            securityScheme("oauth2") {
                type = AuthType.OAUTH2
                description = "OAuth 2.0 authorization-code flow for AT Protocol clients."
                flows {
                    authorizationCode {
                        authorizationUrl = "/oauth/authorize"
                        tokenUrl = "/oauth/token"
                        scopes = mapOf("atproto" to "Access the user's AT Protocol repository")
                    }
                }
            }
        }
        info {
            title = "LogDate Server API"
            version = "1.0.0"
            description = "Machine-readable contract for LogDate auth and sync endpoints."
        }
        server {
            url = "/"
            description = "Current LogDate Cloud deployment"
        }
        val hiddenPaths =
            setOf(
                "/api/v1/auth/metrics",
                "/api/v1/auth/metrics/prometheus",
                "/api/v1/ops/sync/metrics",
                "/api/v1/ops/sync/metrics/prometheus",
                "/api/v1/ops/sync/tombstones:purge",
                "/api/v1/ops/backups:purge",
            )
        pathFilter = { _, segments ->
            val path = "/" + segments.joinToString("/")
            path !in hiddenPaths && listOf("/api/v1/", "/oauth/", "/xrpc/", "/.well-known/").any(path::startsWith)
        }
        // Capture the built spec so the /openapi.yaml route below can serialize
        // it directly. Smiley4 v5's spec("yaml") API doesn't share routes or
        // config with the parent spec, so a parallel YAML spec would ship
        // empty.
        postBuild = { api, _ ->
            completeOpenApiContract(api)
            openApiSpec.set(api)
        }
    }
    return openApiSpec
}

/** Serves the spec as JSON and YAML plus the interactive Scalar reference. */
internal fun Route.openApiRoutes(openApiSpec: AtomicReference<OpenAPI?>) {
    route("openapi.json") {
        openApi()
    }
    get("/openapi.yaml") {
        val spec = openApiSpec.get()
        if (spec == null) {
            call.respondText(
                "OpenAPI spec is still building",
                ContentType.Text.Plain,
                HttpStatusCode.ServiceUnavailable,
            )
        } else {
            call.respondText(Yaml31.pretty(spec), ContentType("application", "yaml"))
        }
    }
    scalarApiReferenceRoutes()
}
