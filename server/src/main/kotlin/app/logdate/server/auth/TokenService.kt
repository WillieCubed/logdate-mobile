package app.logdate.server.auth

import app.logdate.shared.model.AccountTokens
import kotlin.time.Instant

/**
 * Service for generating and validating JWT tokens for LogDate Cloud authentication.
 *
 * This interface abstracts token operations to allow production JWT and in-memory
 * test implementations.
 */
interface TokenService {
    /**
     * Generate a new access token for the given account ID.
     * Access tokens are short-lived (1 hour) and used for API authentication.
     */
    fun generateAccessToken(
        accountId: String,
        did: String? = null,
    ): String

    /**
     * Generate a new refresh token for the given account ID.
     * Refresh tokens are long-lived (30 days) and used to obtain new access tokens.
     */
    fun generateRefreshToken(
        accountId: String,
        did: String? = null,
    ): String

    /** Reproduce a pending enrollment's session after a lost response or server restart. */
    fun generateEnrollmentTokens(
        accountId: String,
        did: String?,
        enrollmentId: String,
        issuedAt: Instant,
    ): AccountTokens

    /** A 1-hour access token for linked device session [sessionId]; it carries `sid` and `scp=sync`. */
    fun generateDeviceAccessToken(
        accountId: String,
        did: String?,
        sessionId: String,
    ): String

    /** A 30-day refresh token for linked device session [sessionId]; it carries `sid` and `scp=sync`. */
    fun generateDeviceRefreshToken(
        accountId: String,
        did: String?,
        sessionId: String,
    ): String

    /**
     * Validate a full-access token and extract the account ID.
     * Returns null if the token is invalid or expired, and for every linked device token.
     */
    fun validateAccessToken(token: String): String?

    /**
     * Validate a full-access refresh token and extract the account ID.
     * Returns null if the token is invalid or expired, and for every linked device token.
     */
    fun validateRefreshToken(token: String): String?

    /** Every claim of a valid access token, full or linked device; null when invalid or expired. */
    fun decodeAccessToken(token: String): AccessTokenClaims? = validateAccessToken(token)?.let { AccessTokenClaims.full(it) }

    /** Every claim of a valid refresh token, full or linked device; null when invalid or expired. */
    fun decodeRefreshToken(token: String): AccessTokenClaims? = validateRefreshToken(token)?.let { AccessTokenClaims.full(it) }

    /**
     * Generate a temporary session token for account creation flow.
     * These are very short-lived (15 minutes) and used during passkey registration.
     */
    fun generateSessionToken(sessionId: String): String

    /**
     * Validate a session token and extract the session ID.
     * Returns null if the token is invalid or expired.
     */
    fun validateSessionToken(token: String): String?
}
