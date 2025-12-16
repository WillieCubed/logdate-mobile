package app.logdate.server.auth

/**
 * Service for generating and validating JWT tokens for LogDate Cloud authentication.
 * 
 * This interface abstracts token operations to allow for different implementations,
 * including production JWT implementations and testing stubs.
 */
interface TokenService {
    /**
     * Generate a new access token for the given account ID.
     * Access tokens are short-lived (1 hour) and used for API authentication.
     */
    fun generateAccessToken(accountId: String): String
    
    /**
     * Generate a new refresh token for the given account ID.
     * Refresh tokens are long-lived (30 days) and used to obtain new access tokens.
     */
    fun generateRefreshToken(accountId: String): String
    
    /**
     * Validate an access token and extract the account ID.
     * Returns null if the token is invalid or expired.
     */
    fun validateAccessToken(token: String): String?
    
    /**
     * Validate a refresh token and extract the account ID.
     * Returns null if the token is invalid or expired.
     */
    fun validateRefreshToken(token: String): String?
    
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