package app.logdate.server.responses

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
sealed class ApiResponse<out T> {
    abstract val timestamp: Instant
    
    @Serializable
    data class Success<T>(
        val data: T,
        val message: String? = null,
        override val timestamp: Instant = Clock.System.now()
    ) : ApiResponse<T>()
    
    @Serializable
    data class Error(
        val code: String,
        val message: String,
        val details: Map<String, String>? = null,
        override val timestamp: Instant = Clock.System.now()
    ) : ApiResponse<Nothing>()
}

// Simple non-sealed versions for testing
@Serializable
data class SimpleSuccessResponse<T>(
    val data: T,
    val message: String? = null,
    val timestamp: Instant = Clock.System.now()
)

@Serializable
data class SimpleErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
    val timestamp: Instant = Clock.System.now()
)

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val limit: Int,
    val total: Int,
    val hasMore: Boolean
)

fun <T> success(data: T, message: String? = null) = ApiResponse.Success(data, message)

@Suppress("UNCHECKED_CAST")
fun <T> success(message: String) = ApiResponse.Success(Unit as T, message)

fun error(code: String, message: String, details: Map<String, String>? = null) = 
    ApiResponse.Error(code, message, details)

// Simple helper functions for cleaner responses
fun <T> simpleSuccess(data: T, message: String? = null) = SimpleSuccessResponse(data, message)

fun <T> simpleSuccess(message: String) = SimpleSuccessResponse(Unit, message)

fun simpleError(code: String, message: String, details: Map<String, String>? = null) = 
    SimpleErrorResponse(code, message, details)