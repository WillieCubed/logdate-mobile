package app.logdate.client.sync.cloud

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * The record kinds a strict create can target. Each answers `412` and `410` with its own error code,
 * and a code from another kind or route is never read as one of these outcomes.
 */
internal enum class ConditionalRecord(
    val existsCode: String,
    val deletedCode: String,
) {
    CONTENT("CONTENT_EXISTS", "CONTENT_DELETED"),
    JOURNAL("JOURNAL_EXISTS", "JOURNAL_DELETED"),
}

private val wireJson = Json { ignoreUnknownKeys = true }

/** The `201` body: the record's new version and when the server stored it. */
@Serializable
private data class CreatedBody(
    val serverVersion: Long,
    val uploadedAt: Long,
)

/** The server's error body. `412` and `410` put the version and deletion time in [details] as strings. */
@Serializable
private data class ConflictBody(
    val code: String = "",
    val details: Map<String, String> = emptyMap(),
)

internal suspend fun HttpResponse.readCreated(): Result<ConditionalCreateResult> {
    val body = decodeBody<CreatedBody>() ?: return unreadable()
    return Result.success(ConditionalCreateResult.Created(body.serverVersion, Instant.fromEpochMilliseconds(body.uploadedAt)))
}

/**
 * `412`: a live record holds the ID. The server leaves out the version only when the record vanished
 * between its check and its read, so a create sent again would succeed; that is returned as a failure
 * to retry instead of a version this client does not have.
 */
internal suspend fun HttpResponse.readAlreadyExists(record: ConditionalRecord): Result<ConditionalCreateResult> {
    val conflict = decodeBody<ConflictBody>() ?: return unreadable()
    if (conflict.code != record.existsCode) return otherError(conflict.code)
    val version =
        conflict.details["serverVersion"]?.toLongOrNull()
            ?: return Result.failure(
                CloudApiException(
                    record.existsCode,
                    "The server holds this record but did not report its version",
                    statusCode = status.value,
                ),
            )
    return Result.success(ConditionalCreateResult.AlreadyExists(version))
}

/** `410`: the ID was deleted, and the server wrote nothing. */
internal suspend fun HttpResponse.readDeleted(record: ConditionalRecord): Result<ConditionalCreateResult> {
    val conflict = decodeBody<ConflictBody>() ?: return unreadable()
    if (conflict.code != record.deletedCode) return otherError(conflict.code)
    val deletedAt = conflict.details["deletedAt"]?.toLongOrNull() ?: return unreadable()
    return Result.success(ConditionalCreateResult.Deleted(Instant.fromEpochMilliseconds(deletedAt)))
}

/** `HEAD` answered `200`: the version is in [SERVER_VERSION_HEADER]. */
internal fun HttpResponse.readPresentState(): Result<ContentState> {
    val version = headers[SERVER_VERSION_HEADER]?.toLongOrNull() ?: return unreadable()
    return Result.success(ContentState.Present(version))
}

/** `HEAD` answered `410`: the deletion time is in [DELETED_AT_HEADER]. */
internal fun HttpResponse.readDeletedState(): Result<ContentState> {
    val deletedAt = headers[DELETED_AT_HEADER]?.toLongOrNull() ?: return unreadable()
    return Result.success(ContentState.Deleted(Instant.fromEpochMilliseconds(deletedAt)))
}

private suspend inline fun <reified T> HttpResponse.decodeBody(): T? =
    try {
        wireJson.decodeFromString<T>(bodyAsText())
    } catch (_: IllegalArgumentException) {
        null
    }

private fun <T> HttpResponse.unreadable(): Result<T> =
    Result.failure(CloudApiException("PARSE_ERROR", "Unreadable response (HTTP ${status.value})", statusCode = status.value))

private fun <T> HttpResponse.otherError(code: String): Result<T> =
    Result.failure(CloudApiException(safeCloudErrorCode(code), "Request failed (HTTP ${status.value})", statusCode = status.value))
