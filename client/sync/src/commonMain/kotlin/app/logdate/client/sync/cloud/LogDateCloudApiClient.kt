package app.logdate.client.sync.cloud

import app.logdate.client.datastore.SessionStorage
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
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
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
    private val sessionStorage: SessionStorage? = null,
    diagnostics: (app.logdate.shared.model.diagnostics.SyncDiagnosticEvent) -> Unit = {},
    diagnosticSource: suspend () -> app.logdate.client.sync.diagnostics.DiagnosticSource? = { null },
    scopedDiagnostics: (
        app.logdate.shared.model.diagnostics.SyncDiagnosticEvent,
        app.logdate.client.sync.diagnostics.DiagnosticSource?,
    ) -> Unit = { _, _ -> },
) : CloudApiClient {
    private val transport = SafeCloudTransport(httpClient, diagnostics, diagnosticSource, scopedDiagnostics)
    private val errorJson = Json { ignoreUnknownKeys = true }

    private suspend fun getBaseUrl(
        accessToken: String? = null,
        refreshToken: String? = null,
    ): String {
        val pinned = currentCoroutineContext()[CloudRequestBinding]
        if (pinned != null) {
            if ((accessToken != null && accessToken != pinned.authorizedAccessToken) ||
                (refreshToken != null && refreshToken != pinned.session.refreshToken)
            ) {
                throw scopeChanged()
            }
            return pinned.location.apiBaseUrl
        }

        val origin = configRepository.getCurrentBackendUrl()
        val selectedBaseUrl = configRepository.getCurrentApiBaseUrl()
        val bound = if (accessToken != null || refreshToken != null) sessionStorage?.getOriginBoundSession() else null
        if (sessionStorage != null &&
            (accessToken != null || refreshToken != null) &&
            (
                bound?.origin != origin ||
                    (accessToken != null && bound.session.accessToken != accessToken) ||
                    (refreshToken != null && bound.session.refreshToken != refreshToken)
            )
        ) {
            throw scopeChanged()
        }
        val baseUrl = configRepository.apiBaseUrl.first()
        if (baseUrl != selectedBaseUrl ||
            configRepository.getCurrentBackendUrl() != origin ||
            (bound != null && sessionStorage?.getOriginBoundSession() != bound)
        ) {
            throw scopeChanged()
        }
        return baseUrl
    }

    private fun scopeChanged() =
        CloudApiException(
            "CLOUD_SCOPE_CHANGED",
            "Cloud account changed during request",
            statusCode = 401,
        )

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
            val response = transport.get("$baseUrl/auth/signup/username/$username/available")

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
            if (e is CancellationException) throw e
            Napier.e("Failed to check username availability")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to check username availability",
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
                transport.post("$baseUrl/auth/signup/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        SignupPasskeyBeginRequestDto(
                            username = request.username,
                            displayName = request.displayName,
                            bio = request.bio,
                            originalBio = request.originalBio,
                            requestedOwnerId = request.requestedOwnerId,
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
            if (e is CancellationException) throw e
            Napier.e("Failed to begin account creation")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to begin account creation",
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
                transport.post("$baseUrl/auth/signup/passkey/complete") {
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
            if (e is CancellationException) throw e
            Napier.e("Failed to complete account creation")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to complete account creation",
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
            val baseUrl = getBaseUrl(refreshToken = refreshToken)
            val response =
                transport.post("$baseUrl/auth/token/refresh") {
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
            if (e is CancellationException) throw e
            Napier.e("Failed to refresh access token")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to refresh access token",
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
            val baseUrl = getBaseUrl(accessToken)
            Napier.d("Requesting account info")

            val response =
                transport.get("$baseUrl/auth/me") {
                    // Add Authorization header with Bearer token scheme
                    headers.append("Authorization", "Bearer $accessToken")
                }

            Napier.d("Account info response received")

            when (response.status) {
                HttpStatusCode.OK -> {
                    try {
                        val responseBody = response.body<AuthResponseDto>()
                        Napier.d("Parsed account info response body")

                        if (responseBody.success) {
                            Result.success(responseBody.data.account.toLogDateAccount())
                        } else {
                            handleApiError(response)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Napier.e("Failed to parse account info response")
                        Result.failure(
                            CloudApiException(
                                errorCode = "PARSE_ERROR",
                                message = "Failed to parse account info response",
                            ),
                        )
                    }
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to get account info")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to get account info",
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
        val errorPayload =
            runCatching { response.bodyAsText() }
                .onFailure { Napier.w("Could not read error body for HTTP") }
                .getOrDefault("")
        // Response bodies are untrusted and can echo journal content or credentials.
        Napier.w("Sync API request failed: HTTP")
        val parsedError = parseErrorPayload(errorPayload)
        return if (parsedError != null) {
            Result.failure(
                CloudApiException(
                    errorCode = safeCloudErrorCode(parsedError.code),
                    message = "Request failed (HTTP $statusCode)",
                    statusCode = statusCode,
                ),
            )
        } else {
            Result.failure(
                CloudApiException(
                    errorCode = "UNKNOWN_ERROR",
                    message = "Request failed (HTTP $statusCode)",
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
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.put("$baseUrl/contents/${content.id}") {
                    headers.append("Authorization", "Bearer $accessToken")
                    headers.append(HttpHeaders.IfNoneMatch, "*")
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
            if (e is CancellationException) throw e
            Napier.e("Failed to upload content")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to upload content",
                ),
            )
        }

    override suspend fun getContentChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<ContentChangesResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val limitParam = limit?.let { "&limit=$it" }.orEmpty()
            val response =
                transport.get("$baseUrl/contents?since=$since$limitParam") {
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
            if (e is CancellationException) throw e
            Napier.e("Failed to get content changes")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to get content changes",
                ),
            )
        }

    override suspend fun updateContent(
        accessToken: String,
        contentId: String,
        content: ContentUpdateRequest,
    ): Result<ContentUpdateResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.patch("$baseUrl/contents/$contentId") {
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
            if (e is CancellationException) throw e
            Napier.e("Failed to update content")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to update content",
                ),
            )
        }

    override suspend fun deleteContent(
        accessToken: String,
        contentId: String,
    ): Result<Unit> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.delete("$baseUrl/contents/$contentId") {
                    headers.append("Authorization", "Bearer $accessToken")
                }

            when (response.status) {
                HttpStatusCode.NoContent -> Result.success(Unit)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to delete content")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to delete content",
                ),
            )
        }

    // Journal Sync Operations
    override suspend fun uploadJournal(
        accessToken: String,
        journal: JournalUploadRequest,
    ): Result<JournalUploadResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.put("$baseUrl/journals/${journal.id}") {
                    headers.append("Authorization", "Bearer $accessToken")
                    headers.append(HttpHeaders.IfNoneMatch, "*")
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
            if (e is CancellationException) throw e
            Napier.e("Failed to upload journal")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to upload journal",
                ),
            )
        }

    override suspend fun getJournalChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<JournalChangesResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val limitParam = limit?.let { "&limit=$it" }.orEmpty()
            val response =
                transport.get("$baseUrl/journals?since=$since$limitParam") {
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
            if (e is CancellationException) throw e
            Napier.e("Failed to get journal changes")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to get journal changes",
                ),
            )
        }

    override suspend fun updateJournal(
        accessToken: String,
        journalId: String,
        journal: JournalUpdateRequest,
    ): Result<JournalUpdateResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.patch("$baseUrl/journals/$journalId") {
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
            if (e is CancellationException) throw e
            Napier.e("Failed to update journal")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to update journal",
                ),
            )
        }

    override suspend fun deleteJournal(
        accessToken: String,
        journalId: String,
    ): Result<Unit> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.delete("$baseUrl/journals/$journalId") {
                    headers.append("Authorization", "Bearer $accessToken")
                }

            when (response.status) {
                HttpStatusCode.NoContent -> Result.success(Unit)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to delete journal")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to delete journal",
                ),
            )
        }

    // Association Sync Operations
    override suspend fun uploadAssociations(
        accessToken: String,
        associations: AssociationUploadRequest,
    ): Result<AssociationUploadResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.post("$baseUrl/associations") {
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
            if (e is CancellationException) throw e
            Napier.e("Failed to upload associations")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to upload associations",
                ),
            )
        }

    override suspend fun getAssociationChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<AssociationChangesResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val limitParam = limit?.let { "&limit=$it" }.orEmpty()
            val response =
                transport.get("$baseUrl/associations?since=$since$limitParam") {
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
            if (e is CancellationException) throw e
            Napier.e("Failed to get association changes")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to get association changes",
                ),
            )
        }

    override suspend fun deleteAssociations(
        accessToken: String,
        associations: AssociationDeleteRequest,
    ): Result<Unit> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.delete("$baseUrl/associations") {
                    headers.append("Authorization", "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(associations)
                }

            when (response.status) {
                HttpStatusCode.NoContent -> Result.success(Unit)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to delete associations")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to delete associations",
                ),
            )
        }

    // Draft Operations
    override suspend fun uploadDraft(
        accessToken: String,
        draft: DraftUploadRequest,
    ): Result<DraftUploadResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.put("$baseUrl/drafts/${draft.id}") {
                    headers.append("Authorization", "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(draft)
                }
            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.Created ->
                    Result.success(response.body<DraftUploadResponse>())
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to upload draft")
            Result.failure(CloudApiException("NETWORK_ERROR", "Failed to upload draft"))
        }

    override suspend fun getDraftChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<DraftChangesResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.get("$baseUrl/drafts/changes") {
                    headers.append("Authorization", "Bearer $accessToken")
                    parameter("since", since)
                    limit?.let { parameter("limit", it) }
                }
            when (response.status) {
                HttpStatusCode.OK ->
                    Result.success(response.body<DraftChangesResponse>())
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to get draft changes")
            Result.failure(CloudApiException("NETWORK_ERROR", "Failed to get draft changes"))
        }

    override suspend fun deleteDraft(
        accessToken: String,
        draftId: String,
    ): Result<Unit> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.delete("$baseUrl/drafts/$draftId") {
                    headers.append("Authorization", "Bearer $accessToken")
                }
            when (response.status) {
                HttpStatusCode.NoContent -> Result.success(Unit)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to delete draft")
            Result.failure(CloudApiException("NETWORK_ERROR", "Failed to delete draft"))
        }

    // Media Operations
    override suspend fun uploadMedia(
        accessToken: String,
        media: MediaUpload,
    ): Result<MediaUploadResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.post("$baseUrl/media") {
                    headers.append("Authorization", "Bearer $accessToken")
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("contentId", media.contentId)
                                append("fileName", media.fileName)
                                append("mimeType", media.mimeType)
                                append("sizeBytes", media.sizeBytes.toString())
                                append("deviceId", media.deviceId.value)
                                // Read from disk as the request is written, so the heap never holds
                                // the whole file.
                                append(
                                    key = "data",
                                    value = InputProvider(media.sizeBytes) { media.openBody().buffered() },
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
            if (e is CancellationException) throw e
            Napier.e("Failed to upload media")
            // The body is read from disk while the request is written, so a failure there reaches
            // this catch too. It is the media's fault, not the network's, and retrying it as a
            // network error would hide that.
            val mediaFailure = e.mediaReadFailure()
            Result.failure(
                mediaFailure ?: CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to upload media",
                ),
            )
        }

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaDownloadResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val metadataResponse =
                transport.get("$baseUrl/media/$mediaId") {
                    headers.append("Authorization", "Bearer $accessToken")
                }

            when (metadataResponse.status) {
                HttpStatusCode.OK -> {
                    val metadata = metadataResponse.body<MediaMetadataResponse>()
                    val binaryResponse =
                        transport.get("$baseUrl/media/$mediaId/binary") {
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
            if (e is CancellationException) throw e
            Napier.e("Failed to download media")
            Result.failure(
                CloudApiException(
                    errorCode = "NETWORK_ERROR",
                    message = "Failed to download media",
                ),
            )
        }

    override suspend fun uploadBackupFile(
        accessToken: String,
        backup: BackupUploadFileRequest,
    ): Result<BackupUploadResponse> =
        try {
            val response =
                transport.post("${getBaseUrl(accessToken)}/backups") {
                    headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("deviceId", backup.deviceId)
                                append("manifest", backup.manifest)
                                append(
                                    "data",
                                    InputProvider(backup.sizeBytes) { SystemFileSystem.source(backup.sourcePath).buffered() },
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"backup.bin\"")
                                        append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                                    },
                                )
                            },
                        ),
                    )
                }
            if (response.status == HttpStatusCode.Created) Result.success(response.body()) else handleApiError(response)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure(CloudApiException("NETWORK_ERROR", "Backup upload failed"))
        }

    override suspend fun downloadBackupToFile(
        accessToken: String,
        backupId: String,
        destination: Path,
    ): Result<BackupInfoResponse> {
        var complete = false
        try {
            val baseUrl = getBaseUrl(accessToken)
            val metadataResponse =
                transport.get("$baseUrl/backups/$backupId") {
                    headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            if (metadataResponse.status != HttpStatusCode.OK) return handleApiError(metadataResponse)
            val metadata = metadataResponse.body<BackupInfoResponse>()
            require(metadata.sizeBytes >= 0)
            return transport.streamGet("$baseUrl/backups/$backupId/binary", {
                headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
            }) { response ->
                if (response.status != HttpStatusCode.OK) return@streamGet handleApiError<BackupInfoResponse>(response)
                var transferred = 0L
                SystemFileSystem.sink(destination).buffered().use { sink ->
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val bytes = channel.readRemaining(64 * 1024L).readByteArray()
                        transferred += bytes.size
                        require(transferred <= metadata.sizeBytes) { "Backup length mismatch" }
                        sink.write(bytes)
                    }
                }
                require(transferred == metadata.sizeBytes) { "Backup length mismatch" }
                complete = true
                Result.success(metadata)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.failure(CloudApiException("NETWORK_ERROR", "Backup download failed"))
        } finally {
            if (!complete) runCatching { SystemFileSystem.delete(destination) }
        }
    }

    override suspend fun uploadBackup(
        accessToken: String,
        backup: BackupUploadRequest,
    ): Result<BackupUploadResponse> =
        try {
            val baseUrl = getBaseUrl(accessToken)
            val response =
                transport.post("$baseUrl/backups") {
                    headers.append("Authorization", "Bearer $accessToken")
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("deviceId", backup.deviceId)
                                append("manifest", backup.manifest)
                                append(
                                    key = "data",
                                    value = backup.data,
                                    headers =
                                        Headers.build {
                                            append(HttpHeaders.ContentDisposition, "filename=\"backup.bin\"")
                                            append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                                        },
                                )
                            },
                        ),
                    )
                }

            when (response.status) {
                HttpStatusCode.Created -> Result.success(response.body<BackupUploadResponse>())
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to upload backup")
            Result.failure(CloudApiException("NETWORK_ERROR", "Failed to upload backup"))
        }

    override suspend fun listBackups(accessToken: String): Result<BackupListResponse> =
        try {
            val response =
                transport.get("${getBaseUrl(accessToken)}/backups") {
                    headers.append("Authorization", "Bearer $accessToken")
                }
            when (response.status) {
                HttpStatusCode.OK -> Result.success(response.body<BackupListResponse>())
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to list backups")
            Result.failure(CloudApiException("NETWORK_ERROR", "Failed to list backups"))
        }

    override suspend fun getBackup(
        accessToken: String,
        backupId: String,
    ): Result<BackupInfoResponse> =
        try {
            val response =
                transport.get("${getBaseUrl(accessToken)}/backups/$backupId") {
                    headers.append("Authorization", "Bearer $accessToken")
                }
            when (response.status) {
                HttpStatusCode.OK -> Result.success(response.body<BackupInfoResponse>())
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to get backup metadata")
            Result.failure(CloudApiException("NETWORK_ERROR", "Failed to get backup metadata"))
        }

    override suspend fun downloadBackup(
        accessToken: String,
        backupId: String,
    ): Result<BackupDownloadResponse> =
        try {
            // Resolve the backend once so a user changing server settings cannot combine metadata
            // from one server with bytes from another during this two-request operation.
            val baseUrl = getBaseUrl(accessToken)
            val metadataResponse =
                transport.get("$baseUrl/backups/$backupId") {
                    headers.append("Authorization", "Bearer $accessToken")
                }
            if (metadataResponse.status != HttpStatusCode.OK) {
                return handleApiError(metadataResponse)
            }
            val metadata = metadataResponse.body<BackupInfoResponse>()
            val binaryResponse =
                transport.get("$baseUrl/backups/$backupId/binary") {
                    headers.append("Authorization", "Bearer $accessToken")
                }
            when (binaryResponse.status) {
                HttpStatusCode.OK -> Result.success(BackupDownloadResponse(metadata, binaryResponse.body<ByteArray>()))
                else -> handleApiError(binaryResponse)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to download backup")
            Result.failure(CloudApiException("NETWORK_ERROR", "Failed to download backup"))
        }

    override suspend fun deleteBackup(
        accessToken: String,
        backupId: String,
    ): Result<Unit> =
        try {
            val response =
                transport.delete("${getBaseUrl(accessToken)}/backups/$backupId") {
                    headers.append("Authorization", "Bearer $accessToken")
                }
            when (response.status) {
                HttpStatusCode.NoContent -> Result.success(Unit)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to delete backup")
            Result.failure(CloudApiException("NETWORK_ERROR", "Failed to delete backup"))
        }

    // No custom HttpClient needed as we use the app's shared httpClient
}

@Serializable
private data class SignupPasskeyBeginRequestDto(
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val originalBio: String? = null,
    val requestedOwnerId: String? = null,
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
    val originalBio: String? = null,
    val passkeyCredentialIds: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val emailVerifiedAt: String? = null,
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
        originalBio = originalBio,
        passkeyCredentialIds = passkeyCredentialIds,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        email = email,
        emailVerified = emailVerified,
        emailVerifiedAt = emailVerifiedAt?.let { Instant.parse(it) },
    )
