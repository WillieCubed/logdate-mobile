package app.logdate.server.responses

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class SimpleSuccessResponse<T>(
    val data: T,
    val message: String = "Success",
    val timestamp: Instant = Clock.System.now(),
)

@Serializable
data class SimpleErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val timestamp: Instant = Clock.System.now(),
)

fun <T> simpleSuccess(
    data: T,
    message: String = "Success",
): SimpleSuccessResponse<T> = SimpleSuccessResponse(data = data, message = message)

fun error(
    code: String,
    message: String,
    details: Map<String, String> = emptyMap(),
): SimpleErrorResponse = SimpleErrorResponse(code = code, message = message, details = details)
