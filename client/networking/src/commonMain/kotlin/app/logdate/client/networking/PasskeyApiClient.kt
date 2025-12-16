package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * API client for passkey-based account creation and authentication
 */
class PasskeyApiClient(
    private val httpClient: HttpClient,
    private val configRepository: LogDateConfigRepository,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
) {
    companion object {
        private const val ACCOUNTS_PATH = "/accounts"
    }
    
    private suspend fun getBaseUrl(): String = configRepository.apiBaseUrl.first()

    /**
     * Check if a username is available for registration
     */
    suspend fun checkUsernameAvailability(username: String): Result<UsernameAvailabilityData> {
        return try {
            val baseUrl = getBaseUrl()
            val response = httpClient.get("$baseUrl$ACCOUNTS_PATH/username/$username/available")
            
            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<UsernameAvailabilityResponse>(response.bodyAsText())
                Result.success(apiResponse.data)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.e("Failed to check username availability", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to check username availability", e))
        }
    }

    /**
     * Begin the account creation process
     */
    suspend fun beginAccountCreation(request: BeginAccountCreationRequest): Result<BeginAccountCreationData> {
        return try {
            val baseUrl = getBaseUrl()
            val response = httpClient.post("$baseUrl$ACCOUNTS_PATH/create/begin") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(BeginAccountCreationRequest.serializer(), request))
            }
            
            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<BeginAccountCreationResponse>(response.bodyAsText())
                Result.success(apiResponse.data)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.e("Failed to begin account creation", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to begin account creation", e))
        }
    }

    /**
     * Complete the account creation process with passkey credential
     */
    suspend fun completeAccountCreation(request: CompleteAccountCreationRequest): Result<CompleteAccountCreationData> {
        return try {
            val baseUrl = getBaseUrl()
            val response = httpClient.post("$baseUrl$ACCOUNTS_PATH/create/complete") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(CompleteAccountCreationRequest.serializer(), request))
            }
            
            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<CompleteAccountCreationResponse>(response.bodyAsText())
                Result.success(apiResponse.data)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.e("Failed to complete account creation", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to complete account creation", e))
        }
    }

    /**
     * Begin the authentication process
     */
    suspend fun beginAuthentication(request: BeginAuthenticationRequest): Result<BeginAuthenticationData> {
        return try {
            val baseUrl = getBaseUrl()
            val response = httpClient.post("$baseUrl$ACCOUNTS_PATH/authenticate/begin") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(BeginAuthenticationRequest.serializer(), request))
            }
            
            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<BeginAuthenticationResponse>(response.bodyAsText())
                Result.success(apiResponse.data)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.e("Failed to begin authentication", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to begin authentication", e))
        }
    }

    /**
     * Complete the authentication process with passkey assertion
     */
    suspend fun completeAuthentication(request: CompleteAuthenticationRequest): Result<CompleteAuthenticationData> {
        return try {
            val baseUrl = getBaseUrl()
            val response = httpClient.post("$baseUrl$ACCOUNTS_PATH/authenticate/complete") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(CompleteAuthenticationRequest.serializer(), request))
            }
            
            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<CompleteAuthenticationResponse>(response.bodyAsText())
                Result.success(apiResponse.data)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.e("Failed to complete authentication", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to complete authentication", e))
        }
    }

    /**
     * Get current account information (requires authentication)
     */
    suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount> {
        return try {
            val baseUrl = getBaseUrl()
            val response = httpClient.get("$baseUrl$ACCOUNTS_PATH/me") {
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.value in 200..299) {
                val accountResponse = json.decodeFromString<LogDateAccount>(response.bodyAsText())
                Result.success(accountResponse)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.e("Failed to get account info", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to get account info", e))
        }
    }

    /**
     * Update account profile information (requires authentication)
     */
    suspend fun updateAccountProfile(
        accessToken: String,
        displayName: String? = null,
        username: String? = null,
        bio: String? = null
    ): Result<LogDateAccount> {
        return try {
            val baseUrl = getBaseUrl()
            val updateRequest = UpdateAccountProfileRequest(
                displayName = displayName,
                username = username,
                bio = bio
            )
            val response = httpClient.put("$baseUrl$ACCOUNTS_PATH/me") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $accessToken")
                setBody(json.encodeToString(UpdateAccountProfileRequest.serializer(), updateRequest))
            }
            
            if (response.status.value in 200..299) {
                val accountResponse = json.decodeFromString<LogDateAccount>(response.bodyAsText())
                Result.success(accountResponse)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.e("Failed to update account profile", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to update account profile", e))
        }
    }

    /**
     * Refresh access token using refresh token
     */
    suspend fun refreshToken(refreshToken: String): Result<String> {
        return try {
            val baseUrl = getBaseUrl()
            val response = httpClient.post("$baseUrl$ACCOUNTS_PATH/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refreshToken": "$refreshToken"}""")
            }
            
            if (response.status.value in 200..299) {
                val tokenResponse = json.decodeFromString<RefreshTokenResponse>(response.bodyAsText())
                Result.success(tokenResponse.data.accessToken)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.e("Failed to refresh token", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to refresh token", e))
        }
    }

    /**
     * Delete a specific passkey credential (requires authentication)
     */
    suspend fun deletePasskey(accessToken: String, credentialId: String): Result<Unit> {
        return try {
            val baseUrl = getBaseUrl()
            val response = httpClient.delete("$baseUrl/passkeys/$credentialId") {
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.e("Failed to delete passkey", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to delete passkey", e))
        }
    }
}


/**
 * Exception thrown when passkey API operations fail
 */
class PasskeyApiException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Common API error codes
 */
object PasskeyApiErrorCodes {
    const val USERNAME_TAKEN = "USERNAME_TAKEN"
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val INVALID_SESSION_TOKEN = "INVALID_SESSION_TOKEN"
    const val PASSKEY_VERIFICATION_FAILED = "PASSKEY_VERIFICATION_FAILED"
    const val ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND"
    const val AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED"
    const val NETWORK_ERROR = "NETWORK_ERROR"
    const val INVALID_TOKEN = "INVALID_TOKEN"
    const val INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN"
    const val PASSKEY_NOT_FOUND = "PASSKEY_NOT_FOUND"
    const val PASSKEY_DELETION_FAILED = "PASSKEY_DELETION_FAILED"
}