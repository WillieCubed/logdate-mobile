package app.logdate.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Wire types for the `/api/v1/auth/me/email/verify/{begin,complete}` endpoints.
 *
 * Single source of truth for both the server and Kotlin Multiplatform clients —
 * the server uses these directly from `AuthV1Routes.kt` and the client uses them
 * through `EmailVerificationApiClient`.
 */

@Serializable
data class BeginEmailVerificationResponse(
    /** Opaque server-issued id the client must echo on /complete. */
    val transactionId: String,
    /** Base64url nonce the client embeds in the Credential Manager request. */
    val nonce: String,
    /** Audience the Key Binding JWT must declare. Server-published, not hardcoded. */
    val audience: String,
)

@Serializable
data class CompleteEmailVerificationRequest(
    val transactionId: String,
    /** Raw `DigitalCredential.credentialJson` returned by Android CredentialManager. */
    val credentialJson: String,
)

@Serializable
data class EmailVerifiedResponse(
    val email: String,
    val emailVerifiedAt: Instant,
)

@Serializable
data class EmailVerificationConflictResponse(
    /** Stable code; clients must not localize this. */
    val code: String = "email_already_attached",
    val message: String,
)

@Serializable
data class EmailVerificationErrorResponse(
    /** Stable reason code from DigitalCredentialVerifier or EmailVerificationService. */
    val reason: String,
)
