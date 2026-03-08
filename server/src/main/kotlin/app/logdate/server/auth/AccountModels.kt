package app.logdate.server.auth

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Account(
    val id: @Contextual Uuid,
    val username: String,
    val displayName: String,
    val did: String? = null,
    val handle: String? = null,
    val signingKeyPublic: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val bio: String? = null,
    val createdAt: Instant,
    val lastSignInAt: Instant? = null,
    val timezone: String? = null,
    val locale: String? = null,
    val preferences: String? = null,
    val isActive: Boolean = true,
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class DeviceInfo(
    val platform: String, // "android", "ios", "web"
    val deviceName: String,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val capabilities: List<String> = emptyList(), // ["uv", "rk", "up"]
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class TemporarySession(
    val id: String,
    val temporaryUserId: @Contextual Uuid,
    val challenge: String, // Base64URL encoded
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val deviceInfo: DeviceInfo?,
    val sessionType: SessionType,
    val createdAt: Instant,
    val expiresAt: Instant,
    val isUsed: Boolean = false,
)

@Serializable
enum class SessionType {
    ACCOUNT_CREATION,
    AUTHENTICATION,
}
