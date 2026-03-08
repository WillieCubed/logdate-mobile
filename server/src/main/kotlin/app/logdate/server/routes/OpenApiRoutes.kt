package app.logdate.server.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource

private const val OPENAPI_VERSION = "3.1.0"
private const val OPENAPI_TITLE = "LogDate Server API"
private const val OPENAPI_DESCRIPTION = "Machine-readable contract for LogDate auth and sync endpoints."
private const val OPENAPI_DOC_VERSION = "1.0.0"

fun Route.openApiRoutes(
    baseDoc: OpenApiDoc = buildBaseOpenApiDoc(),
    jsonSource: OpenApiDocSource = OpenApiDocSource.Routing(contentType = ContentType.Application.Json),
    yamlSource: OpenApiDocSource = OpenApiDocSource.Routing(contentType = ContentType.Application.Yaml),
) {
    get("/openapi.json") {
        call.respondOpenApi(jsonSource, baseDoc)
    }

    get("/openapi.yaml") {
        call.respondOpenApi(yamlSource, baseDoc)
    }

    swaggerUI(path = "swagger") {
        remotePath = "openapi.json"
        source = jsonSource
        openapiVersion = OPENAPI_VERSION
        info =
            OpenApiInfo(
                title = OPENAPI_TITLE,
                version = OPENAPI_DOC_VERSION,
                description = OPENAPI_DESCRIPTION,
            )
    }
}

private fun buildBaseOpenApiDoc(): OpenApiDoc =
    OpenApiDoc
        .Builder()
        .apply {
            openapiVersion = OPENAPI_VERSION
            info =
                OpenApiInfo(
                    title = OPENAPI_TITLE,
                    version = OPENAPI_DOC_VERSION,
                    description = OPENAPI_DESCRIPTION,
                )
        }.build()

private suspend fun ApplicationCall.respondOpenApi(
    source: OpenApiDocSource,
    baseDoc: OpenApiDoc,
) {
    val rendered = source.read(application, baseDoc)
    if (rendered == null) {
        respond(
            HttpStatusCode.InternalServerError,
            mapOf(
                "error" to "OPENAPI_GENERATION_FAILED",
                "message" to "Unable to generate OpenAPI document.",
            ),
        )
        return
    }

    respondText(
        text = rendered.content,
        contentType = rendered.contentType,
    )
}
