package app.logdate.server.openapi

import app.logdate.server.completeOpenApiContract
import app.logdate.server.serverJson
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthKeyLocation
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.config.ExampleEncoder
import io.github.smiley4.ktoropenapi.config.SchemaGenerator
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.swagger.v3.oas.models.OpenAPI
import java.util.concurrent.atomic.AtomicReference

/** Route prefixes that make up the public contract. Everything else stays out of the spec. */
private val publishedPrefixes = listOf("/api/v1/", "/oauth/", "/xrpc/", "/.well-known/")

/** Operational endpoints that exist for dashboards and maintenance jobs, not for API clients. */
private val hiddenPaths =
    setOf(
        "/api/v1/auth/metrics",
        "/api/v1/auth/metrics/prometheus",
        "/api/v1/ops/sync/metrics",
        "/api/v1/ops/sync/metrics/prometheus",
        "/api/v1/ops/sync/tombstones:purge",
        "/api/v1/ops/backups:purge",
    )

/**
 * Installs the OpenAPI plugin with everything that describes the LogDate Cloud contract as a
 * whole: metadata, security schemes, schema generation and the post-build pass.
 *
 * The finished [OpenAPI] object is stored in [openApiSpec] so the YAML route can serialize the
 * exact document the JSON route serves.
 */
fun Application.installLogDateOpenApi(openApiSpec: AtomicReference<OpenAPI?>) {
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
        // Schemas and examples are derived from the same Json the server serializes with, so
        // renames, sealed-class discriminators and contextual serializers match the wire.
        schemas {
            generator = SchemaGenerator.kotlinx(serverJson)
        }
        examples {
            encoder(ExampleEncoder.kotlinx(serverJson))
        }
        pathFilter = { _, segments ->
            val path = "/" + segments.joinToString("/")
            path !in hiddenPaths && publishedPrefixes.any(path::startsWith)
        }
        postBuild = { api, _ ->
            useReadableSchemaNames(api)
            describeSealedDiscriminators(api)
            completeOpenApiContract(api)
            openApiSpec.set(api)
        }
    }
}
