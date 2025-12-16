package app.logdate.client.sync.cloud

import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAccountCreationResponse
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationResponse
import app.logdate.shared.model.RefreshTokenResponse
import app.logdate.shared.model.RefreshTokenData
import app.logdate.client.networking.httpClient
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * Implementation of [CloudApiClient] for communicating with the LogDate Cloud API.
 * 
 * This client uses Ktor for HTTP requests and handles authentication, serialization,
 * and error handling for all LogDate Cloud API interactions.
 *
 * @param baseUrl The base URL for the LogDate Cloud API.
 * @param httpClient The Ktor HTTP client instance to use for requests. Defaults to the application's shared client.
 */
class LogDateCloudApiClient(
    private val baseUrl: String,
    private val httpClient: HttpClient
) : CloudApiClient {

    /**
     * Checks if a username is available for registration by attempting to get an account
     * with the username. If a 404 is returned, the username is available. If a 200 is returned,
     * the username is taken.
     *
     * This is a RESTful approach following proper HTTP semantics.
     *
     * @param username The username to check availability for.
     * @return Response indicating if the username is available.
     * @throws CloudApiException If the request fails.
     */
    override suspend fun checkUsernameAvailability(username: String): Result<CheckUsernameAvailabilityResponse> {
        return try {
            val response = httpClient.get("$baseUrl/accounts/username/$username")
            
            when (response.status) {
                HttpStatusCode.OK -> {
                    // Username exists, so it's not available
                    Result.success(CheckUsernameAvailabilityResponse(
                        username = username,
                        available = false
                    ))
                }
                HttpStatusCode.NotFound -> {
                    // Username doesn't exist, so it's available
                    Result.success(CheckUsernameAvailabilityResponse(
                        username = username,
                        available = true
                    ))
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to check username availability", e)
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to check username availability: ${e.message}",
                cause = e
            ))
        }
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
    override suspend fun beginAccountCreation(
        request: BeginAccountCreationRequest
    ): Result<BeginAccountCreationResponse> {
        return try {
            val response = httpClient.post("$baseUrl/accounts/create/begin") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<ApiResponse<BeginAccountCreationResponse>>()
                    if (responseBody.success) {
                        Result.success(responseBody.data)
                    } else {
                        handleApiError(response)
                    }
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to begin account creation", e)
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to begin account creation: ${e.message}",
                cause = e
            ))
        }
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
    override suspend fun completeAccountCreation(
        request: CompleteAccountCreationRequest
    ): Result<CompleteAccountCreationResponse> {
        return try {
            val response = httpClient.post("$baseUrl/accounts/create/complete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            when (response.status) {
                HttpStatusCode.Created -> {
                    val responseBody = response.body<ApiResponse<CompleteAccountCreationResponse>>()
                    if (responseBody.success) {
                        Result.success(responseBody.data)
                    } else {
                        handleApiError(response)
                    }
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to complete account creation", e)
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to complete account creation: ${e.message}",
                cause = e
            ))
        }
    }

    /**
     * Refreshes an expired access token.
     *
     * @param refreshToken The refresh token to use.
     * @return A new access token if successful.
     * @throws CloudApiException If the request fails.
     */
    override suspend fun refreshAccessToken(refreshToken: String): Result<String> {
        return try {
            val response = httpClient.post("$baseUrl/accounts/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshTokenRequest(refreshToken))
            }
            
            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<RefreshTokenResponse>()
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
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to refresh access token: ${e.message}",
                cause = e
            ))
        }
    }

    /**
     * Gets the current account information.
     *
     * @param accessToken The access token for authentication.
     * @return The account information if the request is successful.
     * @throws CloudApiException If the request fails.
     */
    override suspend fun getAccountInfo(accessToken: String): Result<AccountInfoResponse> {
        return try {
            Napier.d("Getting account info with token: ${accessToken.take(5)}...")
            
            val response = httpClient.get("$baseUrl/accounts/me") {
                // Add Authorization header with Bearer token scheme
                headers.append("Authorization", "Bearer $accessToken")
            }
            
            Napier.d("Account info response received with status: ${response.status}")
            
            when (response.status) {
                HttpStatusCode.OK -> {
                    try {
                        val responseBody = response.body<ApiResponse<AccountInfoResponse>>()
                        Napier.d("Parsed response body with success=${responseBody.success}")
                        
                        if (responseBody.success) {
                            Result.success(responseBody.data)
                        } else {
                            handleApiError(response)
                        }
                    } catch (e: Exception) {
                        Napier.e("Failed to parse account info response", e)
                        Result.failure(CloudApiException(
                            errorCode = "PARSE_ERROR",
                            message = "Failed to parse account info response: ${e.message}",
                            cause = e
                        ))
                    }
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to get account info", e)
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to get account info: ${e.message}",
                cause = e
            ))
        }
    }
    
    /**
     * Handles API error responses.
     * 
     * @param response The HTTP response containing the error.
     * @return A Result.failure with appropriate error information.
     */
    private suspend fun <T> handleApiError(response: HttpResponse): Result<T> {
        return try {
            val errorBody = response.body<ApiErrorResponse>()
            Result.failure(CloudApiException(
                errorCode = errorBody.error.code,
                message = errorBody.error.message,
                statusCode = response.status.value
            ))
        } catch (e: Exception) {
            Result.failure(CloudApiException(
                errorCode = "UNKNOWN_ERROR",
                message = "An unknown error occurred: ${response.status.description}",
                statusCode = response.status.value
            ))
        }
    }

    // Content Sync Operations
    override suspend fun uploadContent(accessToken: String, content: ContentUploadRequest): Result<ContentUploadResponse> {
        return try {
            val response = httpClient.post("$baseUrl/sync/content") {
                headers.append("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(content)
            }
            
            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<ContentUploadResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to upload content", e)
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to upload content: ${e.message}",
                cause = e
            ))
        }
    }
    
    override suspend fun getContentChanges(accessToken: String, since: Long): Result<ContentChangesResponse> {
        return try {
            val response = httpClient.get("$baseUrl/sync/content/changes?since=$since") {
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
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to get content changes: ${e.message}",
                cause = e
            ))
        }
    }
    
    override suspend fun updateContent(accessToken: String, contentId: String, content: ContentUpdateRequest): Result<ContentUpdateResponse> {
        return try {
            val response = httpClient.post("$baseUrl/sync/content/$contentId") {
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
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to update content: ${e.message}",
                cause = e
            ))
        }
    }
    
    override suspend fun deleteContent(accessToken: String, contentId: String): Result<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/sync/content/$contentId/delete") {
                headers.append("Authorization", "Bearer $accessToken")
            }
            
            when (response.status) {
                HttpStatusCode.OK -> Result.success(Unit)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to delete content", e)
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to delete content: ${e.message}",
                cause = e
            ))
        }
    }
    
    // Journal Sync Operations
    override suspend fun uploadJournal(accessToken: String, journal: JournalUploadRequest): Result<JournalUploadResponse> {
        return try {
            val response = httpClient.post("$baseUrl/sync/journals") {
                headers.append("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(journal)
            }
            
            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<JournalUploadResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to upload journal", e)
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to upload journal: ${e.message}",
                cause = e
            ))
        }
    }
    
    override suspend fun getJournalChanges(accessToken: String, since: Long): Result<JournalChangesResponse> {
        return try {
            val response = httpClient.get("$baseUrl/sync/journals/changes?since=$since") {
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
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to get journal changes: ${e.message}",
                cause = e
            ))
        }
    }
    
    override suspend fun updateJournal(accessToken: String, journalId: String, journal: JournalUpdateRequest): Result<JournalUpdateResponse> {
        return try {
            val response = httpClient.post("$baseUrl/sync/journals/$journalId") {
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
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to update journal: ${e.message}",
                cause = e
            ))
        }
    }
    
    override suspend fun deleteJournal(accessToken: String, journalId: String): Result<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/sync/journals/$journalId/delete") {
                headers.append("Authorization", "Bearer $accessToken")
            }
            
            when (response.status) {
                HttpStatusCode.OK -> Result.success(Unit)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to delete journal", e)
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to delete journal: ${e.message}",
                cause = e
            ))
        }
    }
    
    // Association Sync Operations
    override suspend fun uploadAssociations(accessToken: String, associations: AssociationUploadRequest): Result<AssociationUploadResponse> {
        return try {
            val response = httpClient.post("$baseUrl/sync/associations") {
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
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to upload associations: ${e.message}",
                cause = e
            ))
        }
    }
    
    override suspend fun getAssociationChanges(accessToken: String, since: Long): Result<AssociationChangesResponse> {
        return try {
            val response = httpClient.get("$baseUrl/sync/associations/changes?since=$since") {
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
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to get association changes: ${e.message}",
                cause = e
            ))
        }
    }
    
    override suspend fun deleteAssociations(accessToken: String, associations: AssociationDeleteRequest): Result<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/sync/associations/delete") {
                headers.append("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(associations)
            }
            
            when (response.status) {
                HttpStatusCode.OK -> Result.success(Unit)
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to delete associations", e)
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to delete associations: ${e.message}",
                cause = e
            ))
        }
    }
    
    // Media Operations
    override suspend fun uploadMedia(accessToken: String, media: MediaUploadRequest): Result<MediaUploadResponse> {
        return try {
            val response = httpClient.post("$baseUrl/sync/media") {
                headers.append("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(media)
            }
            
            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<MediaUploadResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to upload media", e)
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to upload media: ${e.message}",
                cause = e
            ))
        }
    }
    
    override suspend fun downloadMedia(accessToken: String, mediaId: String): Result<MediaDownloadResponse> {
        return try {
            val response = httpClient.get("$baseUrl/sync/media/$mediaId") {
                headers.append("Authorization", "Bearer $accessToken")
            }
            
            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBody = response.body<MediaDownloadResponse>()
                    Result.success(responseBody)
                }
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            Napier.e("Failed to download media", e)
            Result.failure(CloudApiException(
                errorCode = "NETWORK_ERROR",
                message = "Failed to download media: ${e.message}",
                cause = e
            ))
        }
    }

    // No custom HttpClient needed as we use the app's shared httpClient
}

/**
 * Generic API response wrapper.
 */
@kotlinx.serialization.Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T
)

/**
 * API error response.
 */
@kotlinx.serialization.Serializable
data class ApiErrorResponse(
    val error: ApiError
)

/**
 * API error details.
 */
@kotlinx.serialization.Serializable
data class ApiError(
    val code: String,
    val message: String
)

/**
 * Request to refresh an access token.
 */
@kotlinx.serialization.Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)