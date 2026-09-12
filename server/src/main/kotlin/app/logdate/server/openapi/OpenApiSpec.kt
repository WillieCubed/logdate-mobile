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

internal const val API_TITLE = "LogDate Cloud API"
internal const val API_VERSION = "1.0.0"
internal const val HOSTED_SERVER_URL = "https://cloud.logdate.app"
internal const val PRODUCT_URL = "https://logdate.app"

/** Route prefixes that make up the public contract. Everything else stays out of the spec. */
private val publishedPrefixes = listOf("/api/v1/", "/oauth/", "/xrpc/", "/.well-known/")

/**
 * Installs the OpenAPI plugin with everything that describes the LogDate Cloud contract as a
 * whole: the overview, tags and their groups, security schemes, schema generation and the
 * post-build passes that polish names and attach schema prose.
 *
 * The finished [OpenAPI] object is stored in [openApiSpec] so the YAML route can serialize the
 * exact document the JSON route serves.
 */
fun Application.installLogDateOpenApi(openApiSpec: AtomicReference<OpenAPI?>) {
    install(OpenApi) {
        info {
            title = API_TITLE
            version = API_VERSION
            summary = "Sync, backup and identity for the LogDate journaling apps."
            description = OpenApiOverview.markdown
            termsOfService = "$PRODUCT_URL/terms"
            contact {
                name = "LogDate"
                url = PRODUCT_URL
            }
        }
        externalDocs {
            url = PRODUCT_URL
            description = "About LogDate"
        }
        server {
            url = "/"
            description = "This deployment (the server serving this page)"
        }
        server {
            url = HOSTED_SERVER_URL
            description = "LogDate Cloud (hosted)"
        }
        security {
            securityScheme("bearerAuth") {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "JWT"
                description =
                    "Send the `accessToken` from any sign-in response as `Authorization: Bearer <accessToken>`. It is a " +
                    "JWT (a signed token the server verifies without a database lookup) and expires after a short " +
                    "while on purpose. On `401`, call **Refresh the access token** with your `refreshToken` and retry."
            }
            securityScheme("dpopProof") {
                type = AuthType.API_KEY
                name = "DPoP"
                location = AuthKeyLocation.HEADER
                description =
                    "A DPoP proof: a short-lived JWT your client signs with its own key pair for this exact request " +
                    "(method and URL). It ties an OAuth token to the client holding the private key, so a stolen " +
                    "token is useless on its own. Only AT Protocol OAuth clients need this."
            }
            securityScheme("oauth2") {
                type = AuthType.OAUTH2
                description =
                    "OAuth 2.0 authorization-code flow with PKCE for third-party AT Protocol clients. Push your request " +
                    "to `/oauth/par`, send the person to `/oauth/authorize`, then exchange the code at " +
                    "`/oauth/token`. The LogDate apps do not use this; they use bearer tokens."
                flows {
                    authorizationCode {
                        authorizationUrl = "/oauth/authorize"
                        tokenUrl = "/oauth/token"
                        scopes = mapOf("atproto" to "Read and write the person's AT Protocol repository on this server")
                    }
                }
            }
        }
        tags {
            apiTagGroups.forEach { group ->
                group.tags.forEach { apiTag ->
                    tag(apiTag.name) { description = renderApiText(apiTag.description) }
                }
                tagGroup(group.name) { group.tags.forEach { tag(it.name) } }
            }
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
            publishedPrefixes.any(path::startsWith)
        }
        postBuild = { api, _ ->
            useReadableSchemaNames(api)
            describeSealedDiscriminators(api)
            applySchemaDocumentation(api)
            completeOpenApiContract(api)
            openApiSpec.set(api)
        }
    }
}
