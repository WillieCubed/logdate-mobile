package app.logdate.client.sync.space.migration

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudRequestBinding
import app.logdate.client.sync.cloud.safeCloudErrorCode
import app.logdate.shared.model.sync.LegacyInventory
import app.logdate.shared.model.sync.RegisteredLegacyMedia
import app.logdate.shared.model.sync.SpaceMigrationRequest
import app.logdate.shared.model.sync.SpaceMigrationStatus
import app.logdate.shared.model.sync.SpaceMigrationSwitchBackRequest
import app.logdate.shared.model.sync.SpaceMigrationSwitchRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * The server's `/api/v1/space-migration` calls, which move this account's journal from legacy
 * storage into its personal space. Every call needs a full-access token. A refusal is a
 * [CloudApiException] carrying the server's code, such as
 * [app.logdate.shared.model.sync.SpaceMigrationErrorCodes.LEGACY_CHANGED].
 */
interface SpaceMigrationApi {
    suspend fun status(accessToken: String): SpaceMigrationStatus

    suspend fun begin(
        accessToken: String,
        migrationId: String,
    ): SpaceMigrationStatus

    suspend fun hold(
        accessToken: String,
        migrationId: String,
    ): SpaceMigrationStatus

    suspend fun release(
        accessToken: String,
        migrationId: String,
    ): SpaceMigrationStatus

    /** What legacy storage holds, with the ids of [idsOf] when it names a kind. */
    suspend fun legacyInventory(
        accessToken: String,
        idsOf: String? = null,
    ): LegacyInventory

    /** Has the server copy the legacy media file [mediaId], which an app encrypted, into the personal space. */
    suspend fun registerMedia(
        accessToken: String,
        migrationId: String,
        mediaId: String,
    ): RegisteredLegacyMedia

    suspend fun switch(
        accessToken: String,
        request: SpaceMigrationSwitchRequest,
    ): SpaceMigrationStatus

    suspend fun switchBack(
        accessToken: String,
        request: SpaceMigrationSwitchBackRequest,
    ): SpaceMigrationStatus
}

/**
 * [SpaceMigrationApi] over the app's HTTP client. Requests go to the server the sync request is
 * bound to, or else to [apiBaseUrl], such as `https://cloud.logdate.app/api/v1`.
 */
class HttpSpaceMigrationApi(
    private val httpClient: HttpClient,
    private val apiBaseUrl: suspend () -> String,
) : SpaceMigrationApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun status(accessToken: String): SpaceMigrationStatus = get(accessToken, "")

    override suspend fun begin(
        accessToken: String,
        migrationId: String,
    ): SpaceMigrationStatus = post(accessToken, "/begin", SpaceMigrationRequest(migrationId))

    override suspend fun hold(
        accessToken: String,
        migrationId: String,
    ): SpaceMigrationStatus = post(accessToken, "/hold", SpaceMigrationRequest(migrationId))

    override suspend fun release(
        accessToken: String,
        migrationId: String,
    ): SpaceMigrationStatus = post(accessToken, "/release", SpaceMigrationRequest(migrationId))

    override suspend fun legacyInventory(
        accessToken: String,
        idsOf: String?,
    ): LegacyInventory = get(accessToken, "/legacy-inventory") { idsOf?.let { parameter("ids", it) } }

    override suspend fun registerMedia(
        accessToken: String,
        migrationId: String,
        mediaId: String,
    ): RegisteredLegacyMedia = post(accessToken, "/media/${mediaId.encodeURLPathPart()}", SpaceMigrationRequest(migrationId))

    override suspend fun switch(
        accessToken: String,
        request: SpaceMigrationSwitchRequest,
    ): SpaceMigrationStatus = post(accessToken, "/switch", request)

    override suspend fun switchBack(
        accessToken: String,
        request: SpaceMigrationSwitchBackRequest,
    ): SpaceMigrationStatus = post(accessToken, "/switch-back", request)

    private suspend inline fun <reified T> get(
        accessToken: String,
        path: String,
        crossinline configure: HttpRequestBuilder.() -> Unit = {},
    ): T =
        decode(
            send {
                httpClient.get("${base()}$path") {
                    bearerAuth(accessToken)
                    configure()
                }
            },
        )

    private suspend inline fun <reified B, reified T> post(
        accessToken: String,
        path: String,
        body: B,
    ): T =
        decode(
            send {
                httpClient.post("${base()}$path") {
                    bearerAuth(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(body))
                }
            },
        )

    private suspend fun base(): String {
        val bound = currentCoroutineContext()[CloudRequestBinding]?.location?.apiBaseUrl
        return "${(bound ?: apiBaseUrl()).trimEnd('/')}/space-migration"
    }

    private suspend fun send(request: suspend () -> HttpResponse): HttpResponse =
        try {
            request()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: CloudApiException) {
            throw error
        } catch (error: Exception) {
            throw CloudApiException("NETWORK_ERROR", "The server couldn't be reached to move the journal", cause = error)
        }

    private suspend inline fun <reified T> decode(response: HttpResponse): T {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // Response bodies are untrusted; only a code the app knows is kept.
            val code = runCatching { json.decodeFromString<ErrorBody>(text).code }.getOrNull()
            throw CloudApiException(
                errorCode = code?.let(::safeCloudErrorCode) ?: "HTTP_${response.status.value}",
                message = "Moving the journal failed (HTTP ${response.status.value})",
                statusCode = response.status.value,
                retryAfterMillis = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.let { it * 1_000 },
            )
        }
        return try {
            json.decodeFromString(text)
        } catch (error: IllegalArgumentException) {
            throw CloudApiException("INVALID_RESPONSE", "The server's answer about the move couldn't be read", response.status.value, error)
        }
    }

    @Serializable
    private class ErrorBody(
        val code: String,
    )
}
