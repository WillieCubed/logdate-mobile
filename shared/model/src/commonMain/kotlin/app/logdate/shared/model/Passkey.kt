package app.logdate.shared.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class PasskeyInfo(
    val id: @Contextual Uuid,
    val credentialId: String,
    val nickname: String?,
    val deviceType: String, // "platform", "cross-platform"
    val createdAt: kotlinx.datetime.Instant,
    val lastUsedAt: kotlinx.datetime.Instant?,
    val isActive: Boolean = true
)

@Serializable
data class PasskeyRegistrationOptions(
    val challenge: String,
    val user: PasskeyUser,
    val excludeCredentials: List<String> = emptyList(),
    val timeout: Long = 300_000
)

@Serializable
data class PasskeyAuthenticationOptions(
    val challenge: String,
    val allowCredentials: List<String> = emptyList(),
    val timeout: Long = 300_000
)

@Serializable
data class PasskeyUser(
    val id: String,
    val name: String,
    val displayName: String
)

@Serializable
data class PasskeyCapabilities(
    val isSupported: Boolean,
    val isPlatformAuthenticatorAvailable: Boolean,
    val supportedAlgorithms: List<String> = emptyList()
)

@Serializable
data class PasskeyRegistrationResponse(
    val id: String,
    val rawId: String,
    val response: AuthenticatorAttestationResponse,
    val type: String = "public-key"
)

@Serializable
data class PasskeyAuthenticationResponse(
    val id: String,
    val rawId: String,
    val response: AuthenticatorAssertionResponse,
    val type: String = "public-key"
)

@Serializable
data class AuthenticatorAttestationResponse(
    val clientDataJSON: String,
    val attestationObject: String
)

@Serializable
data class AuthenticatorAssertionResponse(
    val clientDataJSON: String,
    val authenticatorData: String,
    val signature: String,
    val userHandle: String?
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class PasskeyChallenge(
    val challenge: String, // Base64URL encoded
    val userId: @Contextual Uuid,
    val type: String, // "registration", "authentication"
    val expiresAt: String,
    val isUsed: Boolean = false
)