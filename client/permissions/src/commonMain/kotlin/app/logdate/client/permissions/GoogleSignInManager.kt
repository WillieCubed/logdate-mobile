package app.logdate.client.permissions

/**
 * Obtains a Google ID token through the platform credential UI for "Sign in with Google".
 *
 * The returned token is forwarded to the server's `/auth/signup/google` and `/auth/signin/google`
 * endpoints, which validate it against the configured `GOOGLE_OIDC_CLIENT_IDS`.
 */
interface GoogleSignInManager {
    /**
     * Launches the platform Google credential flow and returns a fresh Google ID token (a JWT).
     *
     * @param serverClientId the web OAuth client ID the ID token is issued for — the audience the
     *   server validates. Sourced from build config; blank means Google sign-in is not configured.
     * @param nonce optional nonce echoed into the ID token to bind it to this request.
     */
    suspend fun getGoogleIdToken(
        serverClientId: String,
        nonce: String? = null,
    ): Result<String>
}

/**
 * Failure raised when a Google credential flow cannot complete (cancelled, no account, not
 * configured, or the platform does not support it).
 */
class GoogleSignInException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * No-op [GoogleSignInManager] for platforms without Google credential support (desktop, iOS, or
 * Android without Google Play services). Always fails so callers can surface "Google sign-in is
 * unavailable" rather than crash.
 */
class NoOpGoogleSignInManager : GoogleSignInManager {
    override suspend fun getGoogleIdToken(
        serverClientId: String,
        nonce: String?,
    ): Result<String> = Result.failure(GoogleSignInException("Google sign-in is not available on this platform"))
}
