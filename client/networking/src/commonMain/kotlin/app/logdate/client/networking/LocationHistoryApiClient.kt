package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.sync.LocationHistoryBatchRequest
import app.logdate.shared.model.sync.LocationHistoryBatchResponse
import app.logdate.shared.model.sync.LocationHistoryChangesResponse
import app.logdate.shared.model.sync.LocationHistoryRecord
import app.logdate.shared.model.sync.LocationHistoryUpload
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface LocationHistoryApiClientContract {
    suspend fun upload(
        accessToken: String,
        records: List<LocationHistoryUpload>,
    ): List<LocationHistoryRecord>

    suspend fun changes(
        accessToken: String,
        since: Long,
        limit: Int = 100,
    ): LocationHistoryChangesResponse
}

class LocationHistoryTransportException(
    val statusCode: Int,
) : IllegalStateException("Location history request failed (HTTP $statusCode)")

/** Payloads have already been encrypted with the account key before reaching this transport. */
class LocationHistoryApiClient(
    private val httpClient: HttpClient,
    private val configRepository: LogDateConfigRepository,
) : LocationHistoryApiClientContract {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun upload(
        accessToken: String,
        records: List<LocationHistoryUpload>,
    ): List<LocationHistoryRecord> {
        val response =
            httpClient.put("${configRepository.apiBaseUrl.first()}/location-history") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LocationHistoryBatchRequest(records)))
            }
        if (!response.status.isSuccess()) throw LocationHistoryTransportException(response.status.value)
        return json.decodeFromString<LocationHistoryBatchResponse>(response.bodyAsText()).records
    }

    override suspend fun changes(
        accessToken: String,
        since: Long,
        limit: Int,
    ): LocationHistoryChangesResponse {
        val response =
            httpClient.get("${configRepository.apiBaseUrl.first()}/location-history") {
                bearerAuth(accessToken)
                parameter("since", since)
                parameter("limit", limit)
            }
        if (!response.status.isSuccess()) throw LocationHistoryTransportException(response.status.value)
        return json.decodeFromString(response.bodyAsText())
    }
}
