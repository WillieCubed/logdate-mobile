package app.logdate.server

import app.logdate.server.openapi.installLogDateOpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.swagger.v3.core.util.Yaml31
import io.swagger.v3.oas.models.OpenAPI
import java.util.concurrent.atomic.AtomicReference

/**
 * Installs the OpenAPI plugin and returns the holder that receives the built spec once every
 * route has been registered. Pass it to [openApiRoutes]. What the spec says lives in
 * `openapi/OpenApiSpec.kt`.
 */
internal fun Application.installOpenApi(): AtomicReference<OpenAPI?> {
    val openApiSpec = AtomicReference<OpenAPI?>()
    installLogDateOpenApi(openApiSpec)
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
