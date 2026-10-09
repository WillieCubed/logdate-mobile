package app.logdate.server.auth

/**
 * Which routes a token may call.
 *
 * [FULL] tokens come from signing in. [SYNC] tokens belong to a linked device session and reach only the
 * sync data routes. [claim] is the JWT `scp` value; full tokens carry none.
 */
enum class TokenScope(
    val claim: String?,
) {
    FULL(null),
    SYNC("sync"),
    ;

    companion object {
        /** The scope an `scp` claim names, or null for a scope this build does not know. */
        fun fromClaim(claim: String?): TokenScope? = entries.firstOrNull { it.claim == claim }
    }
}

/** What a verified access or refresh token says about its holder. */
data class AccessTokenClaims(
    val accountId: String,
    val did: String?,
    val sessionId: String?,
    val scope: TokenScope,
) {
    companion object {
        fun full(
            accountId: String,
            did: String? = null,
        ): AccessTokenClaims = AccessTokenClaims(accountId, did, sessionId = null, scope = TokenScope.FULL)
    }
}
