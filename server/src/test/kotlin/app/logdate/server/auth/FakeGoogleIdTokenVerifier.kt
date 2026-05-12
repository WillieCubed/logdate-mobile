package app.logdate.server.auth

class FakeGoogleIdTokenVerifier(
    private val tokens: Map<String, GoogleIdTokenClaims>,
    private val configured: Boolean = true,
) : GoogleIdTokenVerifier {
    override fun isConfigured(): Boolean = configured

    override suspend fun verify(
        idToken: String,
        nonce: String?,
    ): GoogleIdTokenClaims? = tokens[idToken]
}
