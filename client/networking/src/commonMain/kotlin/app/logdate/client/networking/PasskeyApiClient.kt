package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.AccountInfoResponse
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.ApiErrorResponse
import app.logdate.shared.model.BeginAccountCreationData
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAuthenticationData
import app.logdate.shared.model.BeginAuthenticationRequest
import app.logdate.shared.model.CompleteAccountCreationData
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAuthenticationData
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.EntitlementResponse
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyAllowCredential
import app.logdate.shared.model.PasskeyRegistrationOptions
import app.logdate.shared.model.RefreshTokenRequest
import app.logdate.shared.model.UpdateAccountProfileRequest
import app.logdate.shared.model.UsernameAvailabilityData
import app.logdate.shared.model.UsernameAvailabilityResponse
import app.logdate.util.UuidSerializer
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Interface for passkey API operations.
 */
interface PasskeyApiClientContract {
    suspend fun checkUsernameAvailability(username: String): Result<UsernameAvailabilityData>

    suspend fun beginAccountCreation(request: BeginAccountCreationRequest): Result<BeginAccountCreationData>

    suspend fun completeAccountCreation(request: CompleteAccountCreationRequest): Result<CompleteAccountCreationData>

    suspend fun beginAuthentication(request: BeginAuthenticationRequest): Result<BeginAuthenticationData>

    suspend fun completeAuthentication(request: CompleteAuthenticationRequest): Result<CompleteAuthenticationData>

    suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount>

    /** Fetch the caller's current cloud entitlement (plan + quota + status). */
    suspend fun getEntitlement(accessToken: String): Result<EntitlementResponse>

    suspend fun updateAccountProfile(
        accessToken: String,
        displayName: String? = null,
        username: String? = null,
        bio: String? = null,
    ): Result<LogDateAccount>

    suspend fun refreshToken(refreshToken: String): Result<String>

    suspend fun deletePasskey(
        accessToken: String,
        credentialId: String,
    ): Result<Unit>

    /**
     * Begin restore key registration. Returns WebAuthn registration options for creating a restore key.
     * Requires an authenticated session.
     */
    suspend fun beginRestoreKeyRegistration(accessToken: String): Result<PasskeyRegistrationOptions>

    /**
     * Complete restore key registration by sending the credential JSON back to the server.
     * Requires an authenticated session.
     *
     * @param credentialJson The WebAuthn registration response JSON from [RestoreCredentialManager]
     * @param challenge The challenge that was used during registration (from [beginRestoreKeyRegistration])
     */
    suspend fun completeRestoreKeyRegistration(
        accessToken: String,
        credentialJson: String,
        challenge: String,
    ): Result<Unit>

    /**
     * Begin restore sign-in. Returns a WebAuthn authentication challenge.
     * Does not require authentication — called on first launch after device restore.
     */
    suspend fun beginRestoreSignIn(): Result<BeginAuthenticationData>

    /**
     * Complete restore sign-in. Verifies the restore credential and returns tokens.
     */
    suspend fun completeRestoreSignIn(request: CompleteAuthenticationRequest): Result<CompleteAuthenticationData>

    /**
     * Delete the authenticated account, including all sync data, media blobs, and backup blobs.
     *
     * Returns success on `204 No Content`. The caller should clear all local state immediately
     * after success — every other authenticated request will start returning 401 because the
     * server-side account no longer exists.
     *
     * Returns a `PasskeyApiException` with code `DELETION_UNAVAILABLE` (HTTP 503) when the
     * server hasn't been configured with an `AccountDeletionService`. Surface that as
     * "Account deletion is temporarily unavailable" rather than a generic error.
     */
    suspend fun deleteAccount(accessToken: String): Result<Unit>
}

/**
 * API client for auth v1 passkey-based account creation and authentication.
 */
class PasskeyApiClient(
    private val httpClient: HttpClient,
    private val configRepository: LogDateConfigRepository,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            serializersModule =
                SerializersModule {
                    contextual(Uuid::class, UuidSerializer)
                }
        },
) : PasskeyApiClientContract {
    companion object {
        private const val AUTH_PATH = "/auth"
    }

    private suspend fun getBaseUrl(): String = configRepository.apiBaseUrl.first()

    override suspend fun checkUsernameAvailability(username: String): Result<UsernameAvailabilityData> =
        try {
            val baseUrl = getBaseUrl()
            val response = httpClient.get("$baseUrl$AUTH_PATH/signup/username/$username/available")

            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<UsernameAvailabilityResponse>(response.bodyAsText())
                Result.success(apiResponse.data)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to check username availability", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to check username availability", e))
        }

    override suspend fun beginAccountCreation(request: BeginAccountCreationRequest): Result<BeginAccountCreationData> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl$AUTH_PATH/signup/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        SignupPasskeyBeginRequestDto(
                            username = request.username,
                            displayName = request.displayName,
                            bio = request.bio,
                        ),
                    )
                }

            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<SignupPasskeyBeginResponseDto>(response.bodyAsText())
                Result.success(
                    BeginAccountCreationData(
                        sessionToken = apiResponse.data.sessionToken,
                        registrationOptions = apiResponse.data.registrationOptions,
                    ),
                )
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to begin account creation", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to begin account creation", e))
        }

    override suspend fun completeAccountCreation(request: CompleteAccountCreationRequest): Result<CompleteAccountCreationData> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl$AUTH_PATH/signup/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<AuthResponseDto>(response.bodyAsText())
                Result.success(
                    CompleteAccountCreationData(
                        account = apiResponse.data.account.toLogDateAccount(),
                        tokens = apiResponse.data.tokens,
                    ),
                )
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to complete account creation", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to complete account creation", e))
        }

    override suspend fun beginAuthentication(request: BeginAuthenticationRequest): Result<BeginAuthenticationData> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl$AUTH_PATH/signin/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<SigninPasskeyBeginResponseDto>(response.bodyAsText())
                Result.success(
                    BeginAuthenticationData(
                        challenge = apiResponse.data.challenge,
                        rpId = apiResponse.data.rpId,
                        allowCredentials = apiResponse.data.allowCredentials,
                        timeout = apiResponse.data.timeout,
                        userVerification = apiResponse.data.userVerification,
                    ),
                )
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to begin authentication", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to begin authentication", e))
        }

    override suspend fun completeAuthentication(request: CompleteAuthenticationRequest): Result<CompleteAuthenticationData> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl$AUTH_PATH/signin/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<AuthResponseDto>(response.bodyAsText())
                Result.success(
                    CompleteAuthenticationData(
                        account = apiResponse.data.account.toLogDateAccount(),
                        tokens = apiResponse.data.tokens,
                    ),
                )
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to complete authentication", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to complete authentication", e))
        }

    override suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.get("$baseUrl$AUTH_PATH/me") {
                    header("Authorization", "Bearer $accessToken")
                }

            if (response.status.value in 200..299) {
                val authResponse = json.decodeFromString<AuthResponseDto>(response.bodyAsText())
                Result.success(authResponse.data.account.toLogDateAccount())
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to get account info", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to get account info", e))
        }

    override suspend fun getEntitlement(accessToken: String): Result<EntitlementResponse> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.get("$baseUrl$AUTH_PATH/me/entitlement") {
                    header("Authorization", "Bearer $accessToken")
                }
            if (response.status.value in 200..299) {
                Result.success(json.decodeFromString<EntitlementResponse>(response.bodyAsText()))
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to get entitlement", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to get entitlement", e))
        }

    override suspend fun updateAccountProfile(
        accessToken: String,
        displayName: String?,
        username: String?,
        bio: String?,
    ): Result<LogDateAccount> =
        try {
            val baseUrl = getBaseUrl()
            val updateRequest = UpdateAccountProfileRequest(displayName = displayName, username = username, bio = bio)
            val response =
                httpClient.put("$baseUrl$AUTH_PATH/me") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $accessToken")
                    setBody(updateRequest)
                }

            if (response.status.value in 200..299) {
                val accountResponse = json.decodeFromString<AccountInfoResponse>(response.bodyAsText())
                Result.success(accountResponse.data)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to update account profile", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to update account profile", e))
        }

    override suspend fun refreshToken(refreshToken: String): Result<String> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl$AUTH_PATH/token/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(refreshToken))
                }

            if (response.status.value in 200..299) {
                val tokenResponse = json.decodeFromString<RefreshTokenResponseV1Dto>(response.bodyAsText())
                Result.success(tokenResponse.data.accessToken)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to refresh token", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to refresh token", e))
        }

    override suspend fun deletePasskey(
        accessToken: String,
        credentialId: String,
    ): Result<Unit> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.delete("$baseUrl$AUTH_PATH/me/passkeys/$credentialId") {
                    header("Authorization", "Bearer $accessToken")
                }

            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to delete passkey", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to delete passkey", e))
        }

    override suspend fun deleteAccount(accessToken: String): Result<Unit> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.delete("$baseUrl$AUTH_PATH/me") {
                    header("Authorization", "Bearer $accessToken")
                }

            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to delete account", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to delete account", e))
        }

    override suspend fun beginRestoreKeyRegistration(accessToken: String): Result<PasskeyRegistrationOptions> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl$AUTH_PATH/restore/register/begin") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $accessToken")
                }

            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<RestoreRegisterBeginResponseDto>(response.bodyAsText())
                Result.success(apiResponse.data)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to begin restore key registration", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to begin restore key registration", e))
        }

    override suspend fun completeRestoreKeyRegistration(
        accessToken: String,
        credentialJson: String,
        challenge: String,
    ): Result<Unit> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl$AUTH_PATH/restore/register/complete") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $accessToken")
                    setBody(RestoreRegisterCompleteRequestDto(credentialJson, challenge))
                }

            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.w("Failed to complete restore key registration", e)
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to complete restore key registration", e))
        }

    override suspend fun beginRestoreSignIn(): Result<BeginAuthenticationData> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl$AUTH_PATH/restore/begin") {
                    contentType(ContentType.Application.Json)
                }

            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<SigninPasskeyBeginResponseDto>(response.bodyAsText())
                Result.success(
                    BeginAuthenticationData(
                        challenge = apiResponse.data.challenge,
                        rpId = apiResponse.data.rpId,
                        allowCredentials = apiResponse.data.allowCredentials,
                        timeout = apiResponse.data.timeout,
                        userVerification = apiResponse.data.userVerification,
                    ),
                )
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.i("Failed to begin restore sign-in: ${e.message ?: "unknown error"}")
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to begin restore sign-in", e))
        }

    override suspend fun completeRestoreSignIn(request: CompleteAuthenticationRequest): Result<CompleteAuthenticationData> =
        try {
            val baseUrl = getBaseUrl()
            val response =
                httpClient.post("$baseUrl$AUTH_PATH/restore/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            if (response.status.value in 200..299) {
                val apiResponse = json.decodeFromString<AuthResponseDto>(response.bodyAsText())
                Result.success(
                    CompleteAuthenticationData(
                        account = apiResponse.data.account.toLogDateAccount(),
                        tokens = apiResponse.data.tokens,
                    ),
                )
            } else {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
                Result.failure(PasskeyApiException(errorResponse.error.code, errorResponse.error.message))
            }
        } catch (e: Exception) {
            Napier.i("Failed to complete restore sign-in: ${e.message ?: "unknown error"}")
            Result.failure(PasskeyApiException("NETWORK_ERROR", "Failed to complete restore sign-in", e))
        }
}

/**
 * Exception thrown when passkey API operations fail.
 */
class PasskeyApiException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Common API error codes.
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

    // Codes the server emits that the client can usefully distinguish.
    const val RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
    const val ACCOUNT_LINK_CONFLICT = "ACCOUNT_LINK_CONFLICT"
    const val EMAIL_BINDING_INVALID = "EMAIL_BINDING_INVALID"
    const val GOOGLE_AUTH_NOT_CONFIGURED = "GOOGLE_AUTH_NOT_CONFIGURED"
    const val GOOGLE_TOKEN_INVALID = "GOOGLE_TOKEN_INVALID"
    const val DELETION_UNAVAILABLE = "DELETION_UNAVAILABLE"
    const val SERVER_ERROR = "SERVER_ERROR"
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
private data class SigninPasskeyBeginResponseDto(
    val success: Boolean,
    val data: SigninPasskeyBeginDataDto,
)

@Serializable
private data class SigninPasskeyBeginDataDto(
    val challenge: String,
    val rpId: String,
    val allowCredentials: List<PasskeyAllowCredential>,
    val timeout: Long,
    val userVerification: String,
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
private data class RestoreRegisterBeginResponseDto(
    val success: Boolean,
    val data: PasskeyRegistrationOptions,
)

@Serializable
private data class RestoreRegisterCompleteRequestDto(
    val credentialJson: String,
    val challenge: String,
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
