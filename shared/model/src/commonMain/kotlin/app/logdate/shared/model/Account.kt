package app.logdate.shared.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class LogDateAccount(
    val id: @Contextual Uuid = Uuid.random(),
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val passkeyCredentialIds: List<String> = emptyList(),
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now()
)

@Serializable
data class AccountTokens(
    val accessToken: String,
    val refreshToken: String
)

@Serializable
data class BeginAccountCreationRequest(
    val username: String,
    val displayName: String,
    val bio: String? = null
)

@Serializable
data class BeginAccountCreationResponse(
    val success: Boolean,
    val data: BeginAccountCreationData
)

@Serializable
data class BeginAccountCreationData(
    val sessionToken: String,
    val registrationOptions: PasskeyRegistrationOptions
)

@Serializable
data class CompleteAccountCreationRequest(
    val sessionToken: String,
    val credential: PasskeyCredentialResponse
)

@Serializable
data class CompleteAccountCreationResponse(
    val success: Boolean,
    val data: CompleteAccountCreationData
)

@Serializable
data class CompleteAccountCreationData(
    val account: LogDateAccount,
    val tokens: AccountTokens
)

@Serializable
data class PasskeyCredentialResponse(
    val id: String,
    val rawId: String,
    val response: PasskeyAuthenticatorResponse,
    val type: String = "public-key"
)

@Serializable
data class PasskeyAuthenticatorResponse(
    val clientDataJSON: String,
    val attestationObject: String
)

@Serializable
data class BeginAuthenticationRequest(
    val username: String? = null
)

@Serializable
data class BeginAuthenticationResponse(
    val success: Boolean,
    val data: BeginAuthenticationData
)

@Serializable
data class BeginAuthenticationData(
    val challenge: String,
    val rpId: String,
    val allowCredentials: List<PasskeyAllowCredential>,
    val timeout: Long,
    val userVerification: String
)

@Serializable
data class PasskeyAllowCredential(
    val type: String = "public-key",
    val id: String,
    val transports: List<String>
)

@Serializable
data class CompleteAuthenticationRequest(
    val credential: PasskeyAssertionResponse,
    val challenge: String
)

@Serializable
data class PasskeyAssertionResponse(
    val id: String,
    val rawId: String,
    val response: PasskeyAssertionAuthenticatorResponse,
    val type: String = "public-key"
)

@Serializable
data class PasskeyAssertionAuthenticatorResponse(
    val clientDataJSON: String,
    val authenticatorData: String,
    val signature: String,
    val userHandle: String
)

@Serializable
data class CompleteAuthenticationResponse(
    val success: Boolean,
    val data: CompleteAuthenticationData
)

@Serializable
data class CompleteAuthenticationData(
    val account: LogDateAccount,
    val tokens: AccountTokens
)

@Serializable
data class UsernameAvailabilityResponse(
    val success: Boolean,
    val data: UsernameAvailabilityData
)

@Serializable
data class UsernameAvailabilityData(
    val username: String,
    val available: Boolean
)

@Serializable
data class ApiErrorResponse(
    val error: ApiError
)

@Serializable
data class UpdateAccountProfileRequest(
    val displayName: String? = null,
    val username: String? = null,
    val bio: String? = null
)

@Serializable
data class ApiError(
    val code: String,
    val message: String
)

@Serializable
data class RefreshTokenResponse(
    val success: Boolean,
    val data: RefreshTokenData
)

@Serializable
data class RefreshTokenData(
    val accessToken: String
)