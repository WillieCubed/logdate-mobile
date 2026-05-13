package app.logdate.server.routes

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Wire types for `/api/v1/auth/me/email/verify/{begin,complete}`. All transactions
 * begin with the client asking the server for a fresh nonce + opaque transaction id,
 * then complete by handing back the Android Digital Credentials response.
 */

@Serializable
data class BeginEmailVerificationResponse(
    /** Opaque server-issued id the client must echo on /complete. */
    val transactionId: String,
    /** Base64url nonce the client embeds in the Credential Manager request. */
    val nonce: String,
    /** Audience the Key Binding JWT must declare. Surfaced so clients don't hardcode it. */
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
    /** Stable code; clients should not localize this. */
    val code: String = "email_already_attached",
    val message: String,
)

@Serializable
data class EmailVerificationErrorResponse(
    /** Stable reason code from DigitalCredentialVerifier or EmailVerificationService. */
    val reason: String,
)
