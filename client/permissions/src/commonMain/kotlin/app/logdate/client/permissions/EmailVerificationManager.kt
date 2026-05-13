package app.logdate.client.permissions

import kotlin.time.Instant

/**
 * Cross-platform contract for the Android Digital Credentials email-verification
 * flow. The Android implementation drives the system Credential Manager picker
 * and then completes verification against the server; other platforms inherit
 * [UnavailableEmailVerificationManager] until they grow an equivalent flow.
 *
 * Mirrors the shape of [PasskeyManager] — single coarse-grained `suspend fun`
 * with a sealed result type rather than throwing.
 */
interface EmailVerificationManager {
    /**
     * `true` only on platforms with a working Credential Manager + Digital
     * Credentials integration at runtime (today: Android 14+). Callers must
     * gate UI entry points on this in addition to the server-side
     * `email_verification` entitlement flag.
     */
    val isSupported: Boolean

    /**
     * Drive the full begin -> system picker -> complete round-trip. Returns a
     * [EmailVerificationOutcome] for every defined outcome; no exceptions
     * escape the implementation.
     *
     * @param accessToken Bearer access token for the signed-in account that
     * will receive the verified email.
     */
    suspend fun verifyEmail(accessToken: String): EmailVerificationOutcome
}

sealed interface EmailVerificationOutcome {
    data class Success(
        val email: String,
        val verifiedAt: Instant,
    ) : EmailVerificationOutcome

    /** The verified email is already attached to a different LogDate account. */
    data class Conflict(
        val message: String,
    ) : EmailVerificationOutcome

    /** User dismissed the Credential Manager picker. */
    data object UserCancelled : EmailVerificationOutcome

    /** Wallet returned no eligible credential (no Google account, etc.). */
    data object NoCredentialAvailable : EmailVerificationOutcome

    /** Platform does not implement Digital Credentials. */
    data object Unsupported : EmailVerificationOutcome

    /**
     * Anything else — server-side verifier rejection (signature, nonce,
     * expiry, ...) or transport failure. Reason is a short stable code; UI
     * should not localise it.
     */
    data class Failed(
        val reason: String,
    ) : EmailVerificationOutcome
}

/**
 * No-op fallback used on platforms (iOS, Desktop, Wear, Android <34) that have
 * no Digital Credentials path. Always reports [EmailVerificationOutcome.Unsupported].
 *
 * Named to match the codebase's `Unavailable*` convention (see commit `8dbbf95e`).
 */
class UnavailableEmailVerificationManager : EmailVerificationManager {
    override val isSupported: Boolean get() = false

    override suspend fun verifyEmail(accessToken: String): EmailVerificationOutcome = EmailVerificationOutcome.Unsupported
}
