package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.server.oauth.StoredOAuthSigningKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Integration tests for [PostgreSQLOAuthSigningKeyRepository], which stores the one key that every
 * server instance uses to sign OAuth access tokens.
 */
class PostgreSQLOAuthSigningKeyRepositoryTest {
    @Test
    fun `first stored key wins and every later caller receives it`() =
        withH2Database(OAuthSigningKeysTable) {
            val repository = PostgreSQLOAuthSigningKeyRepository()
            val first = key("kid-first")

            val stored = repository.getOrCreate { first }
            val again = repository.getOrCreate { key("kid-second") }

            assertEquals(first, stored)
            assertEquals(first, again)
        }

    @Test
    fun `a key stored by another instance is returned without creating a new one`() =
        withH2Database(OAuthSigningKeysTable) {
            val existing = key("kid-existing")
            PostgreSQLOAuthSigningKeyRepository().getOrCreate { existing }

            val restarted = PostgreSQLOAuthSigningKeyRepository()

            assertEquals(existing, restarted.getOrCreate { error("must not create a second key") })
        }

    private fun key(keyId: String) =
        StoredOAuthSigningKey(
            keyId = keyId,
            privateKeyEncrypted = "ciphertext-$keyId",
            publicKeySpki = "spki-$keyId",
            createdAt = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
        )
}
