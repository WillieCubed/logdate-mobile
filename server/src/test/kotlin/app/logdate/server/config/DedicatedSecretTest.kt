package app.logdate.server.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DedicatedSecretTest {
    private val secrets = listOf(AtprotoSigningKeyKek, AtprotoSessionSecret)

    @Test
    fun `configured value is used byte for byte`() {
        val copiedSecret = "a-secret-copied-from-another-version-of-at-least-32-chars\n"
        secrets.forEach { secret ->
            val env = mapOf("LOGDATE_ENV" to "production", secret.envVar to copiedSecret)

            assertEquals(copiedSecret, secret.resolve(readEnv = env::get), secret.envVar)
        }
    }

    @Test
    fun `development never falls back to JWT_SECRET`() {
        val env = mapOf("JWT_SECRET" to "a-jwt-secret-that-must-never-stand-in-for-another-secret")
        secrets.forEach { secret ->
            assertEquals(secret.developmentValue, secret.resolve(readEnv = env::get), secret.envVar)
        }
    }

    @Test
    fun `production without the secret refuses to resolve even when JWT_SECRET is set`() {
        val env = mapOf("LOGDATE_ENV" to "production", "JWT_SECRET" to "a-jwt-secret-that-must-never-stand-in-for-another-secret")
        secrets.forEach { secret ->
            val failure = assertFailsWith<IllegalStateException> { secret.resolve(readEnv = env::get) }

            assertTrue(failure.message!!.contains(secret.envVar), secret.envVar)
        }
    }
}
