package app.logdate.client.sync.cloud

import io.github.aakira.napier.Napier
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

internal suspend fun LogDateCloudApiClient.requestCreateContentIfAbsent(
    accessToken: String,
    content: ContentUploadRequest,
): Result<ConditionalCreateResult> =
    conditionalCreate(accessToken, "create content", ConditionalRecord.CONTENT) { baseUrl ->
        transport.put("$baseUrl/contents/${content.id}") {
            parameter("ifAbsent", "true")
            headers.append("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(content)
        }
    }

internal suspend fun LogDateCloudApiClient.requestCreateJournalIfAbsent(
    accessToken: String,
    journal: JournalUploadRequest,
): Result<ConditionalCreateResult> =
    conditionalCreate(accessToken, "create journal", ConditionalRecord.JOURNAL) { baseUrl ->
        transport.put("$baseUrl/journals/${journal.id}") {
            parameter("ifAbsent", "true")
            headers.append("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(journal)
        }
    }

internal suspend fun LogDateCloudApiClient.requestContentState(
    accessToken: String,
    contentId: String,
): Result<ContentState> {
    if (!supportsConditionalCreate()) return Result.failure(ConditionalCreateUnsupportedException())
    return try {
        val baseUrl = getBaseUrl(accessToken)
        val response =
            transport.head("$baseUrl/contents/$contentId") {
                headers.append("Authorization", "Bearer $accessToken")
            }
        when (response.status) {
            HttpStatusCode.OK -> response.readPresentState()
            HttpStatusCode.NotFound -> Result.success(ContentState.Absent)
            HttpStatusCode.Gone -> response.readDeletedState()
            else -> handleApiError(response)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Napier.e("Failed to read content state")
        Result.failure(CloudApiException(errorCode = "NETWORK_ERROR", message = "Failed to read content state"))
    }
}

internal suspend fun LogDateCloudApiClient.requestUploadAssociationsIfAbsent(
    accessToken: String,
    associations: AssociationUploadRequest,
): Result<AssociationUploadResponse> {
    if (!supportsConditionalCreate()) return Result.failure(ConditionalCreateUnsupportedException())
    return try {
        val baseUrl = getBaseUrl(accessToken)
        val response =
            transport.post("$baseUrl/associations") {
                parameter("ifAbsent", "true")
                headers.append("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(associations)
            }
        when (response.status) {
            HttpStatusCode.OK -> Result.success(response.body<AssociationUploadResponse>())
            else -> handleApiError(response)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Napier.e("Failed to upload associations if absent")
        Result.failure(CloudApiException(errorCode = "NETWORK_ERROR", message = "Failed to upload associations"))
    }
}

/**
 * Sends one strict create and reads the answer. Only `201` means the record was created: a `200`
 * would mean the server updated an existing record, that is, ignored the condition, and is an error.
 */
private suspend fun LogDateCloudApiClient.conditionalCreate(
    accessToken: String,
    operation: String,
    record: ConditionalRecord,
    send: suspend (baseUrl: String) -> HttpResponse,
): Result<ConditionalCreateResult> {
    if (!supportsConditionalCreate()) return Result.failure(ConditionalCreateUnsupportedException())
    return try {
        val response = send(getBaseUrl(accessToken))
        when (response.status) {
            HttpStatusCode.Created -> response.readCreated()
            HttpStatusCode.PreconditionFailed -> response.readAlreadyExists(record)
            HttpStatusCode.Gone -> response.readDeleted(record)
            else -> handleApiError(response)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Napier.e("Failed to $operation")
        Result.failure(CloudApiException(errorCode = "NETWORK_ERROR", message = "Failed to $operation"))
    }
}
