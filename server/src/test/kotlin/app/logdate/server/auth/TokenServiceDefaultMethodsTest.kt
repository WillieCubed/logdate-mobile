package app.logdate.server.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class TokenServiceDefaultMethodsTest {
    @Test
    fun `token service default methods pass null did`() {
        val recording = RecordingTokenService()
        val defaultImpls = Class.forName("app.logdate.server.auth.TokenService\$DefaultImpls")
        val generateAccessToken =
            defaultImpls.getDeclaredMethod(
                "generateAccessToken\$default",
                TokenService::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        val generateRefreshToken =
            defaultImpls.getDeclaredMethod(
                "generateRefreshToken\$default",
                TokenService::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )

        val access = generateAccessToken.invoke(null, recording, "account-1", null, 2, null) as String
        val refresh = generateRefreshToken.invoke(null, recording, "account-1", null, 2, null) as String

        assertEquals("access:account-1:null", access)
        assertEquals("refresh:account-1:null", refresh)
        assertEquals(null, recording.lastAccessDid)
        assertEquals(null, recording.lastRefreshDid)
    }

    private class RecordingTokenService : TokenService {
        var lastAccessDid: String? = "uninitialized"
        var lastRefreshDid: String? = "uninitialized"

        override fun generateAccessToken(
            accountId: String,
            did: String?,
        ): String {
            lastAccessDid = did
            return "access:$accountId:$did"
        }

        override fun generateRefreshToken(
            accountId: String,
            did: String?,
        ): String {
            lastRefreshDid = did
            return "refresh:$accountId:$did"
        }

        override fun validateAccessToken(token: String): String? = null

        override fun validateRefreshToken(token: String): String? = null

        override fun generateSessionToken(sessionId: String): String = sessionId

        override fun validateSessionToken(token: String): String? = null
    }
}
