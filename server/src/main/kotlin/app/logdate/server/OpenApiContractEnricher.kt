package app.logdate.server

import app.logdate.server.routes.DOCS_MARKER_EXTENSION
import app.logdate.server.routes.DOCS_MARKER_VERSION
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses

/** Set on every operation that still relies on the machine-generated text below. */
internal const val AUTODOC_EXTENSION = "x-logdate-autodoc"

/**
 * Temporary safety net while the reference is being rewritten: fills in mechanical text for
 * operations that have not yet been hand-documented and marks them so tests can hold the line.
 * Every operation documented with the helpers in `routes/OpenApiDocumentation.kt` passes through
 * untouched, apart from having its internal marker removed.
 */
internal fun completeOpenApiContract(api: OpenAPI) {
    api.paths?.forEach { (path, pathItem) ->
        pathItem.readOperationsMap().forEach { (method, operation) ->
            val handWritten = operation.extensions?.get(DOCS_MARKER_EXTENSION) == DOCS_MARKER_VERSION
            operation.extensions?.remove(DOCS_MARKER_EXTENSION)
            if (operation.extensions?.isEmpty() == true) operation.extensions = null
            if (handWritten) return@forEach

            val tag = tagFor(path)
            if (operation.operationId.isNullOrBlank()) operation.operationId = operationId(method.name, path)
            if (operation.tags.isNullOrEmpty()) operation.tags = listOf(tag)
            if (operation.summary.isNullOrBlank()) operation.summary = summaryFor(method.name, path)
            if (operation.description.isNullOrBlank()) operation.description = "${operation.summary}."
            if (operation.responses.isNullOrEmpty()) {
                val status = if (method.name == "DELETE") "204" else "200"
                operation.responses = ApiResponses().addApiResponse(status, ApiResponse().description("Successful response"))
            }
            operation.addExtension(AUTODOC_EXTENSION, true)
        }
    }
}

private fun tagFor(path: String): String =
    when {
        path.startsWith("/oauth/") || path.contains("oauth-") -> "OAuth"
        path.startsWith("/xrpc/") || path.endsWith("atproto-did") -> "AT Protocol"
        else -> path.removePrefix("/api/v1/").substringBefore('/').replaceFirstChar(Char::uppercase)
    }

private fun summaryFor(
    method: String,
    path: String,
): String {
    val action = method.lowercase().replaceFirstChar(Char::uppercase)
    val resource = path.substringAfterLast('/').replace(Regex("[{}:_-]+"), " ").trim()
    return "$action $resource"
}

private fun operationId(
    method: String,
    path: String,
): String {
    val words = path.split(Regex("[^A-Za-z0-9]+"), limit = 0).filter(String::isNotBlank)
    return method.lowercase() + words.joinToString("") { it.replaceFirstChar(Char::uppercase) }
}
