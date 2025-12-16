package app.logdate.client.domain.account.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to check if a username is available.
 */
@Serializable
data class CheckUsernameAvailabilityRequest(
    val username: String
)

/**
 * Response indicating if a username is available.
 */
@Serializable
data class CheckUsernameAvailabilityResponse(
    val username: String,
    val available: Boolean
)

/**
 * Request to begin account creation with a passkey.
 */
@Serializable
data class BeginAccountCreationRequest(
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val deviceInfo: DeviceInfoDto? = null
)

/**
 * Device information for API requests.
 */
@Serializable
data class DeviceInfoDto(
    val platform: String,
    val deviceName: String? = null,
    val deviceType: String = "MOBILE"
)

/**
 * Response containing registration options for passkey creation.
 */
@Serializable
data class BeginAccountCreationResponse(
    val sessionToken: String,
    val registrationOptions: RegistrationOptionsDto
)

/**
 * WebAuthn registration options.
 */
@Serializable
data class RegistrationOptionsDto(
    val challenge: String,
    val rp: RelyingPartyDto,
    val user: PasskeyUserDto,
    val pubKeyCredParams: List<PublicKeyCredentialParametersDto>,
    val timeout: Long,
    val authenticatorSelection: AuthenticatorSelectionDto,
    val attestation: String
)

/**
 * Relying party information for WebAuthn.
 */
@Serializable
data class RelyingPartyDto(
    val id: String,
    val name: String
)

/**
 * User information for WebAuthn.
 */
@Serializable
data class PasskeyUserDto(
    val id: String,
    val name: String,
    val displayName: String
)

/**
 * Public key credential parameters for WebAuthn.
 */
@Serializable
data class PublicKeyCredentialParametersDto(
    val type: String,
    val alg: Int
)

/**
 * Authenticator selection criteria for WebAuthn.
 */
@Serializable
data class AuthenticatorSelectionDto(
    val authenticatorAttachment: String?,
    val requireResidentKey: Boolean,
    val residentKey: String,
    val userVerification: String
)

/**
 * Request to complete account creation with passkey credential.
 */
@Serializable
data class CompleteAccountCreationRequest(
    val sessionToken: String,
    val credential: PublicKeyCredentialDto
)

/**
 * Public key credential data from WebAuthn.
 */
@Serializable
data class PublicKeyCredentialDto(
    val id: String,
    val rawId: String,
    val response: AuthenticatorResponseDto,
    val type: String
)

/**
 * Authenticator response for WebAuthn.
 */
@Serializable
data class AuthenticatorResponseDto(
    val clientDataJSON: String,
    val attestationObject: String
)

/**
 * Response after successful account creation.
 */
@Serializable
data class CompleteAccountCreationResponse(
    val success: Boolean,
    val data: AccountCreationDataDto
)

/**
 * Account creation data returned from the server.
 */
@Serializable
data class AccountCreationDataDto(
    val account: AccountDto,
    val tokens: TokensDto,
    val passkey: PasskeyDto,
    val syncData: SyncDataDto
)

/**
 * Account information returned from the server.
 */
@Serializable
data class AccountDto(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val passkeyCredentialIds: List<String>,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Authentication tokens returned from the server.
 */
@Serializable
data class TokensDto(
    val accessToken: String,
    val refreshToken: String,
    @SerialName("expiresIn")
    val expiresInSeconds: Long
)

/**
 * Passkey information returned from the server.
 */
@Serializable
data class PasskeyDto(
    val credentialId: String,
    val nickname: String,
    val createdAt: String
)

/**
 * Sync data information returned from the server.
 */
@Serializable
data class SyncDataDto(
    val serverEndpoint: String,
    val initialSyncRequired: Boolean
)