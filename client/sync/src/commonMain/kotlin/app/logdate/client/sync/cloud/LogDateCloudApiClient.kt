package app.logdate.client.sync.cloud

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.ApiErrorResponse
import app.logdate.shared.model.BeginAccountCreationData
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAccountCreationResponse
import app.logdate.shared.model.CompleteAccountCreationData
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationResponse
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyRegistrationOptions
import app.logdate.shared.model.RefreshTokenRequest
import app.logdate.shared.model.UsernameAvailabilityResponse
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Implementation of [CloudApiClient] for communicating with the LogDate Cloud API.
 *
 * This client uses Ktor for HTTP requests and handles authentication, serialization,
 * and error handling for all LogDate Cloud API interactions.
 *
 * @param configRepository The runtime server configuration for the selected LogDate server.
 * @param httpClient The Ktor HTTP client instance to use for requests. Defaults to the application's shared client.
 */
class LogDateCloudApiClient(
    private val configRepository: LogDateConfigRepository,
    private val httpClient: HttpClient,
) : CloudApiClient {
    private val errorJson = Json { ignoreUnknownKeys = true }

    private suspend fun getBaseUrl(): String = configRepository.apiBaseUrl.first()

    /**
     * Checks if a username is available for registration using the availability endpoint.
     *
     * @param username The username to check availability for.
     * @return Response indicating if the username is available.
     * @throws CloudApiException If the request fails.
     */
    override suspend fun checkUsernameAvailability(username: String): Result<CheckUsernameAvailabilityResponse> =
        try {
            val baseUrl = getBaseUrl()
            val response = httpClient.get("$baseUrl/auth/signup/username/$username/available")

            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<UsernameAvailabilityResponse>()
                    if (responseBody.success) {
                        Result.success(
                            CheckUsernameAvailabilityResponse(
                                username = responseBody.data.username,
                                available = responseBody.data.available,
                            ),
                        )
                    } else {
                        handleApiError(response)
                    }
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to check username availability", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to check username availability: ${e.message}",
                    cause = e,
                ),
            )
        }

    /**
     * Begins the account creation process.
     *
     * This initiates the passkey registration process and returns
     * the necessary challenge and options for creating a WebAuthn credential.
     *
     * @param request The account creation request containing user details.
     * @return Response with session token and registration options.
     * @throws CloudApiException If the request fails.
     */
    override suspend fun beginAccountCreation(request: BeginAccountCreationRequest): Result<BeginAccountCreationResponse> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl/auth/signup/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        SignupPasskeyBeginRequestDto(
                            username = request.username,
                            displayName = request.displayName,
                            bio = request.bio,
                        ),
                    )
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<SignupPasskeyBeginResponseDto>()
                    if (responseBody.success) {
                        Result.success(
                            BeginAccountCreationResponse(
                                success = true,
                                data =
                                    BeginAccountCreationData(
                                        sessionToken = responseBody.data.sessionToken,
                                        registrationOptions = responseBody.data.registrationOptions,
                                    ),
                            ),
                        )
                    } else {
                        handleApiError(response)
                    }
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to begin account creation", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to begin account creation: ${e.message}",
                    cause = e,
                ),
            )
        }

    /**
     * Completes the account creation process.
     *
     * This submits the passkey credential created by the client to finalize
     * the account creation process.
     *
     * @param request The request containing the passkey credential.
     * @return Response with the created account details and authentication tokens.
     * @throws CloudApiException If the request fails.
     */
    override suspend fun completeAccountCreation(request: CompleteAccountCreationRequest): Result<CompleteAccountCreationResponse> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl/auth/signup/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.Created -> {
                    val responseBody = response.body<AuthResponseDto>()
                    if (responseBody.success) {
                        Result.success(
                            CompleteAccountCreationResponse(
                                success = true,
                                data =
                                    CompleteAccountCreationData(
                                        account = responseBody.data.account.toLogDateAccount(),
                                        tokens = responseBody.data.tokens,
                                    ),
                            ),
                        )
                    } else {
                        handleApiError(response)
                    }
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to complete account creation", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to complete account creation: ${e.message}",
                    cause = e,
                ),
            )
        }

    /**
     * Refreshes an expired access token.
     *
     * @param refreshToken The refresh token to use.
     * @return A new access token if successful.
     * @throws CloudApiException If the request fails.
     */
    override suspend fun refreshAccessToken(refreshToken: String): Result<String> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl/auth/token/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(refreshToken))
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<RefreshTokenResponseV1Dto>()
                    if (responseBody.success) {
                        Result.success(responseBody.data.accessToken)
                    } else {
                        handleApiError(response)
                    }
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to refresh access token", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to refresh access token: ${e.message}",
                    cause = e,
                ),
            )
        }

    /**
     * Gets the current account information.
     *
     * @param accessToken The access token for authentication.
     * @return The account information if the request is successful.
     * @throws CloudApiException If the request fails.
     */
    override suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount> =
        try {
            val baseUrl = getBaseUrl()
            Napier.d("Getting account info with token: ${accessToken.take(5)}...")

            val response =
                httpClient.get("$baseUrl/auth/me") {
                    // Add Authorization header with Bearer token scheme
                    headers.append("Authorization", "Bearer $accessToken")
                }

            Napier.d("Account info response received with status: ${response.status}")

            when (response.status) {
                HttpStatusCode.OK -> {
                    try {
                        val responseBody = response.body<AuthResponseDto>()
                        Napier.d("Parsed response body with success=${responseBody.success}")

                        if (responseBody.success) {
                            Result.success(responseBody.data.account.toLogDateAccount())
                        } else {
                            handleApiError(response)
                        }
                    } catch (e: Exception) {
                        Napier.e("Failed to parse account info response", e)
                        Result.failure(
                            CloudApiException(
                                errorCode = "PARSE_ERROR",
                                message = "Failed to parse account info response: ${e.message}",
                                cause = e,
                            ),
                        )
                    }
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to get account info", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to get account info: ${e.message}",
                    cause = e,
                ),
            )
        }

    /**
     * Handles API error responses.
     *
     * @param response The HTTP response containing the error.
     * @return A Result.failure with appropriate error information.
     */
    private suspend fun <T> handleApiError(response: HttpResponse): Result<T> {
        val statusCode = response.status.value
        val errorPayload = runCatching { response.bodyAsText() }.getOrDefault("")
        val parsedError = parseErrorPayload(errorPayload)
        return if (parsedError != null) {
            Result.failure(
                CloudApiException(
                    errorCode = parsedError.code,
                    message = parsedError.message,
                    statusCode = statusCode,
                ),
            )
        } else {
            Result.failure(
                CloudApiException(
                    errorCode = "UNKNOWN_ERROR",
                    message = "An unknown error occurred: ${response.status.description}",
                    statusCode = statusCode,
                ),
            )
        }
    }

    private fun parseErrorPayload(payload: String): ParsedErrorBody? {
        if (payload.isBlank()) {
            return null
        }
        val authErrorBody = runCatching { errorJson.decodeFromString<ApiErrorResponse>(payload) }.getOrNull()
        if (authErrorBody != null) {
            return ParsedErrorBody(
                code = authErrorBody.error.code,
                message = authErrorBody.error.message,
            )
        }
        val syncErrorBody = runCatching { errorJson.decodeFromString<SyncErrorResponseBody>(payload) }.getOrNull()
        if (syncErrorBody != null && syncErrorBody.code.isNotBlank()) {
            return ParsedErrorBody(
                code = syncErrorBody.code,
                message = syncErrorBody.message,
            )
        }
        return null
    }

    // Content Sync Operations
    override suspend fun uploadContent(
        accessToken: String,
        content: ContentUploadRequest,
    ): Result<ContentUploadResponse> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.put("$baseUrl/contents/${content.id}") {
                    headers.append("Authorization", "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(content)
                }

            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.Created -> {
                    val responseBody = response.body<ContentUploadResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to upload content", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to upload content: ${e.message}",
                    cause = e,
                ),
            )
        }

    override suspend fun getContentChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<ContentChangesResponse> =
        try {
            val baseUrl = getBaseUrl()
            val limitParam = limit?.let { "&limit=$it" }.orEmpty()
            val response =
                httpClient.get("$baseUrl/contents?since=$since$limitParam") {
                    headers.append("Authorization", "Bearer $accessToken")
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<ContentChangesResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to get content changes", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to get content changes: ${e.message}",
                    cause = e,
                ),
            )
        }

    override suspend fun updateContent(
        accessToken: String,
        contentId: String,
        content: ContentUpdateRequest,
    ): Result<ContentUpdateResponse> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.patch("$baseUrl/contents/$contentId") {
                    headers.append("Authorization", "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(content)
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<ContentUpdateResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to update content", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to update content: ${e.message}",
                    cause = e,
                ),
            )
        }

    override suspend fun deleteContent(
        accessToken: String,
        contentId: String,
    ): Result<Unit> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.delete("$baseUrl/contents/$contentId") {
                    headers.append("Authorization", "Bearer $accessToken")
                }

            when (response.status) {
                HttpStatusCode.NoContent -> Result.success(Unit)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to delete content", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to delete content: ${e.message}",
                    cause = e,
                ),
            )
        }

    // Journal Sync Operations
    override suspend fun uploadJournal(
        accessToken: String,
        journal: JournalUploadRequest,
    ): Result<JournalUploadResponse> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.put("$baseUrl/journals/${journal.id}") {
                    headers.append("Authorization", "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(journal)
                }

            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.Created -> {
                    val responseBody = response.body<JournalUploadResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to upload journal", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to upload journal: ${e.message}",
                    cause = e,
                ),
            )
        }

    override suspend fun getJournalChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<JournalChangesResponse> =
        try {
            val baseUrl = getBaseUrl()
            val limitParam = limit?.let { "&limit=$it" }.orEmpty()
            val response =
                httpClient.get("$baseUrl/journals?since=$since$limitParam") {
                    headers.append("Authorization", "Bearer $accessToken")
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<JournalChangesResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to get journal changes", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to get journal changes: ${e.message}",
                    cause = e,
                ),
            )
        }

    override suspend fun updateJournal(
        accessToken: String,
        journalId: String,
        journal: JournalUpdateRequest,
    ): Result<JournalUpdateResponse> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.patch("$baseUrl/journals/$journalId") {
                    headers.append("Authorization", "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(journal)
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<JournalUpdateResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to update journal", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to update journal: ${e.message}",
                    cause = e,
                ),
            )
        }

    override suspend fun deleteJournal(
        accessToken: String,
        journalId: String,
    ): Result<Unit> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.delete("$baseUrl/journals/$journalId") {
                    headers.append("Authorization", "Bearer $accessToken")
                }

            when (response.status) {
                HttpStatusCode.NoContent -> Result.success(Unit)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to delete journal", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to delete journal: ${e.message}",
                    cause = e,
                ),
            )
        }

    // Association Sync Operations
    override suspend fun uploadAssociations(
        accessToken: String,
        associations: AssociationUploadRequest,
    ): Result<AssociationUploadResponse> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.put("$baseUrl/associations") {
                    headers.append("Authorization", "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(associations)
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<AssociationUploadResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to upload associations", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to upload associations: ${e.message}",
                    cause = e,
                ),
            )
        }

    override suspend fun getAssociationChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<AssociationChangesResponse> =
        try {
            val baseUrl = getBaseUrl()
            val limitParam = limit?.let { "&limit=$it" }.orEmpty()
            val response =
                httpClient.get("$baseUrl/associations?since=$since$limitParam") {
                    headers.append("Authorization", "Bearer $accessToken")
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<AssociationChangesResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to get association changes", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to get association changes: ${e.message}",
                    cause = e,
                ),
            )
        }

    override suspend fun deleteAssociations(
        accessToken: String,
        associations: AssociationDeleteRequest,
    ): Result<Unit> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.delete("$baseUrl/associations") {
                    headers.append("Authorization", "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(associations)
                }

            when (response.status) {
                HttpStatusCode.NoContent -> Result.success(Unit)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to delete associations", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to delete associations: ${e.message}",
                    cause = e,
                ),
            )
        }

    // Media Operations
    override suspend fun uploadMedia(
        accessToken: String,
        media: MediaUploadRequest,
    ): Result<MediaUploadResponse> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl/media") {
                    headers.append("Authorization", "Bearer $accessToken")
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("contentId", media.contentId)
                                append("fileName", media.fileName)
                                append("mimeType", media.mimeType)
                                append("sizeBytes", media.sizeBytes.toString())
                                append("deviceId", media.deviceId.value)
                                append(
                                    key = "data",
                                    value = media.data,
                                    headers =
                                        Headers.build {
                                            append(HttpHeaders.ContentDisposition, "filename=\"${media.fileName}\"")
                                            append(HttpHeaders.ContentType, media.mimeType)
                                        },
                                )
                            },
                        ),
                    )
                }

            when (response.status) {
                HttpStatusCode.Created -> {
                    val responseBody = response.body<MediaUploadResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to upload media", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to upload media: ${e.message}",
                    cause = e,
                ),
            )
        }

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaDownloadResponse> =
        try {
            val baseUrl = getBaseUrl()
            val metadataResponse =
                httpClient.get("$baseUrl/media/$mediaId") {
                    headers.append("Authorization", "Bearer $accessToken")
                }

            when (metadataResponse.status) {
                HttpStatusCode.OK -> {
                    val metadata = metadataResponse.body<MediaMetadataResponse>()
                    val binaryResponse =
                        httpClient.get("$baseUrl/media/$mediaId/binary") {
                            headers.append("Authorization", "Bearer $accessToken")
                        }
                    when (binaryResponse.status) {
                        HttpStatusCode.OK ->
                            Result.success(
                                MediaDownloadResponse(
                                    contentId = metadata.contentId,
                                    fileName = metadata.fileName,
                                    mimeType = metadata.mimeType,
                                    sizeBytes = metadata.sizeBytes,
                                    data = binaryResponse.body<ByteArray>(),
                                    downloadUrl = metadata.downloadUrl,
                                ),
                            )
                        else -> handleApiError(binaryResponse)
                    }
                }
                else -> handleApiError(metadataResponse)
            }
        } catch (e: Exception) {
            Napier.e("Failed to download media", e)
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to download media: ${e.message}",
                    cause = e,
                ),
            )
        }

    // No custom HttpClient needed as we use the app's shared httpClient
}

@Serializable
private data class SignupPasskeyBeginRequestDto(
    val username: String,
    val displayName: String,
    val bio: String? = null,
)

@Serializable
private data class SignupPasskeyBeginResponseDto(
    val success: Boolean,
    val data: SignupPasskeyBeginDataDto,
)

@Serializable
private data class SignupPasskeyBeginDataDto(
    val sessionToken: String,
    val registrationOptions: PasskeyRegistrationOptions,
)

@Serializable
private data class AuthResponseDto(
    val success: Boolean,
    val data: AuthResponseDataDto,
)

@Serializable
private data class AuthResponseDataDto(
    val account: AuthAccountDto,
    val tokens: AccountTokens,
)

@Serializable
private data class AuthAccountDto(
    val id: String,
    val username: String,
    val displayName: String,
    val did: String? = null,
    val handle: String? = null,
    val bio: String? = null,
    val passkeyCredentialIds: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
private data class RefreshTokenResponseV1Dto(
    val success: Boolean,
    val data: RefreshTokenDataV1Dto,
)

@Serializable
private data class RefreshTokenDataV1Dto(
    val accessToken: String,
)

@Serializable
private data class SyncErrorResponseBody(
    val code: String,
    val message: String,
)

private data class ParsedErrorBody(
    val code: String,
    val message: String,
)

private fun AuthAccountDto.toLogDateAccount(): LogDateAccount =
    LogDateAccount(
        id = Uuid.parse(id),
        username = username,
        displayName = displayName,
        did = did,
        handle = handle,
        bio = bio,
        passkeyCredentialIds = passkeyCredentialIds,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
    )
