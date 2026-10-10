package app.logdate.server.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AtprotoSigningKeyKekTest {
    @Test
    fun `configured value is used byte for byte`() {
        val copiedSecret = "a-secret-copied-from-another-version-of-at-least-32-chars\n"
        val env = mapOf("LOGDATE_ENV" to "production", "ATPROTO_SIGNING_KEY_KEK" to copiedSecret)

        assertEquals(copiedSecret, AtprotoSigningKeyKek.resolve(readEnv = env::get))
    }

    @Test
    fun `development never falls back to JWT_SECRET`() {
        val env = mapOf("JWT_SECRET" to "a-jwt-secret-that-must-never-encrypt-signing-keys")

        assertEquals(AtprotoSigningKeyKek.DEVELOPMENT_VALUE, AtprotoSigningKeyKek.resolve(readEnv = env::get))
    }

    @Test
    fun `production without the secret refuses to resolve even when JWT_SECRET is set`() {
        val env = mapOf("LOGDATE_ENV" to "production", "JWT_SECRET" to "a-jwt-secret-that-must-never-encrypt-signing-keys")

        val failure = assertFailsWith<IllegalStateException> { AtprotoSigningKeyKek.resolve(readEnv = env::get) }

        assertTrue(failure.message!!.contains("ATPROTO_SIGNING_KEY_KEK"))
    }
}
