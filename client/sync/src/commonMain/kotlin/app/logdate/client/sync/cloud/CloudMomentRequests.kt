package app.logdate.client.sync.cloud

import app.logdate.shared.model.sync.MomentChange
import app.logdate.shared.model.sync.MomentChangesResponse
import app.logdate.shared.model.sync.MomentUpdateRequest
import app.logdate.shared.model.sync.MomentUpdateResponse
import app.logdate.shared.model.sync.MomentUploadRequest
import io.github.aakira.napier.Napier
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

internal suspend fun LogDateCloudApiClient.requestCreateMomentIfAbsent(
    accessToken: String,
    moment: MomentUploadRequest,
): Result<ConditionalCreateResult> =
    momentRequest(accessToken) { baseUrl ->
        if (!supportsConditionalCreate()) return@momentRequest Result.failure(ConditionalCreateUnsupportedException())
        val response =
            transport.put("$baseUrl/moments/${moment.id}") {
                parameter("ifAbsent", "true")
                headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(moment)
            }
        when (response.status) {
            HttpStatusCode.Created -> response.readCreated()
            HttpStatusCode.PreconditionFailed -> response.readAlreadyExists(ConditionalRecord.MOMENT)
            HttpStatusCode.Gone -> response.readDeleted(ConditionalRecord.MOMENT)
            else -> handleApiError(response)
        }
    }

internal suspend fun LogDateCloudApiClient.requestUpdateMoment(
    accessToken: String,
    momentId: String,
    moment: MomentUpdateRequest,
): Result<MomentUpdateResponse> =
    momentRequest(accessToken) { baseUrl ->
        val response =
            transport.patch("$baseUrl/moments/$momentId") {
                headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(moment)
            }
        if (response.status == HttpStatusCode.OK) Result.success(response.body()) else handleApiError(response)
    }

internal suspend fun LogDateCloudApiClient.requestGetMoment(
    accessToken: String,
    momentId: String,
): Result<MomentChange> =
    momentRequest(accessToken) { baseUrl ->
        val response =
            transport.get("$baseUrl/moments/$momentId") {
                headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        if (response.status == HttpStatusCode.OK) Result.success(response.body()) else handleApiError(response)
    }

internal suspend fun LogDateCloudApiClient.requestGetMomentChanges(
    accessToken: String,
    since: Long,
    limit: Int?,
): Result<MomentChangesResponse> =
    momentRequest(accessToken) { baseUrl ->
        val response =
            transport.get("$baseUrl/moments") {
                headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
                parameter("since", since)
                limit?.let { parameter("limit", it) }
            }
        if (response.status == HttpStatusCode.OK) Result.success(response.body()) else handleApiError(response)
    }

internal suspend fun LogDateCloudApiClient.requestDeleteMoment(
    accessToken: String,
    momentId: String,
): Result<Unit> =
    momentRequest(accessToken) { baseUrl ->
        val response =
            transport.delete("$baseUrl/moments/$momentId") {
                headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        if (response.status == HttpStatusCode.NoContent) Result.success(Unit) else handleApiError(response)
    }

/** Resolve the credential-bound origin first; capabilities must describe that same destination. */
private suspend fun <T> LogDateCloudApiClient.momentRequest(
    accessToken: String,
    send: suspend (String) -> Result<T>,
): Result<T> =
    try {
        val baseUrl = getBaseUrl(accessToken)
        if (!supportsMomentRequests(baseUrl)) {
            Result.failure(UnsupportedOperationException("The selected server does not support moments"))
        } else {
            send(baseUrl)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: CloudApiException) {
        Result.failure(failure)
    } catch (failure: Exception) {
        Napier.e("Failed to sync marked moments: ${failure::class.simpleName}")
        Result.failure(CloudApiException("NETWORK_ERROR", "Failed to sync marked moments", cause = failure))
    }
