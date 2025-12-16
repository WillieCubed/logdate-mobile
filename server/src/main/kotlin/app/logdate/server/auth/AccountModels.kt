package app.logdate.server.auth

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import app.logdate.shared.model.PasskeyRegistrationOptions
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyInfo
import app.logdate.shared.model.PasskeyRegistrationResponse
import app.logdate.shared.model.PasskeyAuthenticationResponse

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Account(
    val id: @Contextual Uuid,
    val username: String,
    val displayName: String,
    val email: String? = null,
    val bio: String? = null,
    val createdAt: Instant,
    val lastSignInAt: Instant? = null,
    val timezone: String? = null,
    val locale: String? = null,
    val preferences: String? = null,
    val isActive: Boolean = true
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class AccountInfo(
    val userId: @Contextual Uuid,
    val username: String,
    val displayName: String,
    val createdAt: Instant,
    val lastSignInAt: Instant? = null
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class DeviceInfo(
    val platform: String, // "android", "ios", "web"
    val deviceName: String,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val capabilities: List<String> = emptyList() // ["uv", "rk", "up"]
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class TemporarySession(
    val id: String,
    val temporaryUserId: @Contextual Uuid,
    val challenge: String, // Base64URL encoded
    val username: String,
    val deviceInfo: DeviceInfo?,
    val sessionType: SessionType,
    val createdAt: Instant,
    val expiresAt: Instant,
    val isUsed: Boolean = false
)

@Serializable
enum class SessionType {
    ACCOUNT_CREATION,
    AUTHENTICATION
}

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long // seconds
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class SyncData(
    val serverEndpoint: String,
    val initialSyncRequired: Boolean = false,
    val lastSyncTimestamp: Instant? = null,
    val pendingChanges: Int = 0
)

// Request/Response models for passkey account creation

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class BeginAccountCreationRequest(
    val preferredUsername: String? = null,
    val deviceInfo: DeviceInfo? = null
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class BeginAccountCreationResponse(
    val challenge: String,
    val userId: String, // Base64URL encoded temporary user ID
    val sessionId: String,
    val registrationOptions: PublicKeyCredentialCreationOptions
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class AccountPreferences(
    val displayName: String? = null,
    val timezone: String? = null,
    val locale: String? = null,
    val enableSync: Boolean = true
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class CompleteAccountCreationRequest(
    val sessionId: String,
    val credential: PasskeyRegistrationResponse,
    val accountPreferences: AccountPreferences? = null
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class AccountCreationResponse(
    val success: Boolean,
    val account: AccountInfo,
    val session: SessionTokens,
    val passkey: PasskeySummaryResponse,
    val syncData: SyncData
)

// Request/Response models for passkey authentication

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class BeginAuthenticationRequest(
    val accountHint: String? = null, // username or account ID
    val deviceInfo: DeviceInfo? = null
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class BeginAuthenticationResponse(
    val challenge: String,
    val sessionId: String,
    val authenticationOptions: PublicKeyCredentialRequestOptions
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class CompleteAuthenticationRequest(
    val sessionId: String,
    val credential: PasskeyAuthenticationResponse
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class AuthenticationResponse(
    val success: Boolean,
    val account: AccountInfo,
    val session: SessionTokens,
    val syncData: SyncData
)

// Use shared passkey models
typealias PublicKeyCredentialCreationOptions = PasskeyRegistrationOptions
typealias PublicKeyCredentialRequestOptions = PasskeyAuthenticationOptions
typealias PasskeySummaryResponse = PasskeyInfo

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)