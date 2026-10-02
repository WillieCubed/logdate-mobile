package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

@Serializable
data class DeviceEnrollmentRequest(
    val id: String,
    val deviceName: String,
    val publicKey: String,
    val confirmationCode: String,
    val expiresAt: Long,
    val status: String,
)

/** Credentials the server minted for the device being connected, never for the approving phone. */
@Serializable
data class DeviceSessionTokens(
    val accessToken: String,
    val refreshToken: String,
) {
    override fun toString(): String = "DeviceSessionTokens(<redacted>)"
}

/**
 * A non-success response from the device enrollment API.
 *
 * [code] is the server's machine-readable error code when the body carried one, such as
 * [SESSION_ALREADY_ISSUED].
 */
class DeviceEnrollmentApiException(
    val status: Int,
    val code: String? = null,
) : Exception("Device enrollment request failed (HTTP $status${code?.let { ", $it" } ?: ""})") {
    companion object {
        const val SESSION_ALREADY_ISSUED = "ENROLLMENT_SESSION_ISSUED"
    }
}

/** The device enrollment endpoints a signed-in phone uses to approve another device. */
interface DeviceEnrollmentApiClientContract {
    suspend fun createFromPhone(
        deviceName: String,
        publicKey: String,
        claimSecret: String,
        confirmationCode: String,
        accessToken: String,
    ): DeviceEnrollmentRequest

    suspend fun get(
        id: String,
        accessToken: String,
    ): DeviceEnrollmentRequest

    /**
     * Mints a separate session for the device behind request [id], owned by the approving account.
     *
     * Fails with [DeviceEnrollmentApiException] status 404 when the request is missing or expired,
     * and 409 with [DeviceEnrollmentApiException.SESSION_ALREADY_ISSUED] when a session was already
     * minted for it.
     */
    suspend fun createDeviceSession(
        id: String,
        accessToken: String,
    ): Result<DeviceSessionTokens>

    suspend fun approve(
        id: String,
        confirmationCode: String,
        encryptedEnvelope: String,
        accessToken: String,
    )

    suspend fun reject(
        id: String,
        accessToken: String,
    )
}

class DeviceEnrollmentApiClient(
    private val httpClient: HttpClient,
    private val configRepository: LogDateConfigRepository,
) : DeviceEnrollmentApiClientContract {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createFromPhone(
        deviceName: String,
        publicKey: String,
        claimSecret: String,
        confirmationCode: String,
        accessToken: String,
    ): DeviceEnrollmentRequest {
        val baseURL = configRepository.apiBaseUrl.first()
        val response =
            httpClient.post("$baseURL/device-enrollments") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(CreateBody(deviceName, publicKey, claimSecret, confirmationCode)))
            }
        ensureSuccess(response)
        return json.decodeFromString<DeviceEnrollmentRequest>(response.bodyAsText())
    }

    override suspend fun get(
        id: String,
        accessToken: String,
    ): DeviceEnrollmentRequest {
        val baseURL = configRepository.apiBaseUrl.first()
        val response =
            httpClient.get("$baseURL/device-enrollments/$id") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        ensureSuccess(response)
        return json.decodeFromString<DeviceEnrollmentRequest>(response.bodyAsText())
    }

    override suspend fun createDeviceSession(
        id: String,
        accessToken: String,
    ): Result<DeviceSessionTokens> =
        try {
            val baseURL = configRepository.apiBaseUrl.first()
            val response =
                httpClient.post("$baseURL/device-enrollments/$id/session") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            ensureSuccess(response)
            Result.success(json.decodeFromString<DeviceSessionTokens>(response.bodyAsText()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Result.failure(failure)
        }

    override suspend fun approve(
        id: String,
        confirmationCode: String,
        encryptedEnvelope: String,
        accessToken: String,
    ) {
        val baseURL = configRepository.apiBaseUrl.first()
        val response =
            httpClient.post("$baseURL/device-enrollments/$id/approve") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ApprovalBody(confirmationCode, encryptedEnvelope)))
            }
        ensureSuccess(response)
    }

    override suspend fun reject(
        id: String,
        accessToken: String,
    ) {
        val baseURL = configRepository.apiBaseUrl.first()
        val response =
            httpClient.post("$baseURL/device-enrollments/$id/reject") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        ensureSuccess(response)
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        val status = response.status.value
        if (status in 200..299) return
        val code = runCatching { json.decodeFromString<ErrorBody>(response.bodyAsText()).code }.getOrNull()
        throw DeviceEnrollmentApiException(status, code)
    }

    @Serializable
    private data class ApprovalBody(
        val confirmationCode: String,
        val encryptedEnvelope: String,
    )

    @Serializable
    private data class CreateBody(
        val deviceName: String,
        val publicKey: String,
        val claimSecret: String,
        val confirmationCode: String,
    )

    @Serializable
    private data class ErrorBody(
        val code: String? = null,
    )
}
