package app.logdate.server

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses

internal fun completeOpenApiContract(api: OpenAPI) {
    api.paths?.forEach { (path, pathItem) ->
        pathItem.readOperationsMap().forEach { (method, operation) ->
            val tag = tagFor(path)
            if (operation.operationId.isNullOrBlank()) operation.operationId = operationId(method.name, path)
            if (operation.tags.isNullOrEmpty()) operation.tags = listOf(tag)
            if (operation.summary.isNullOrBlank()) operation.summary = summaryFor(method.name, path)
            if (operation.description.isNullOrBlank()) operation.description = "${operation.summary}."
            if (operation.responses.isNullOrEmpty()) {
                val status = if (method.name == "DELETE") "204" else "200"
                operation.responses = ApiResponses().addApiResponse(status, ApiResponse().description("Successful response"))
            }
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
