package app.logdate.server.routes

import io.github.smiley4.ktoropenapi.config.RouteConfig
import kotlinx.serialization.Serializable

@Serializable
internal data class MessageErrorResponse(
    val error: String,
)

internal fun RouteConfig.publicOperation(
    operationId: String,
    tag: String,
    summary: String,
    description: String,
) {
    this.operationId = operationId
    tags = listOf(tag)
    this.summary = summary
    this.description = description
}

internal fun RouteConfig.bearerOperation(
    operationId: String,
    tag: String,
    summary: String,
    description: String,
) {
    publicOperation(operationId, tag, summary, description)
    protected = true
    securitySchemeNames = listOf("bearerAuth")
}

internal fun RouteConfig.dpopOperation(
    operationId: String,
    tag: String,
    summary: String,
    description: String,
) {
    publicOperation(operationId, tag, summary, description)
    protected = true
    securitySchemeNames = listOf("dpopProof")
}
