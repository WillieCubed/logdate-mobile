package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.server.identity.EncryptedPrivateKey
import app.logdate.server.oauth.StoredOAuthSigningKey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    @Test
    fun `the encrypted private key is replaced only while it still holds the expected value`() =
        withH2Database(OAuthSigningKeysTable) {
            val repository = PostgreSQLOAuthSigningKeyRepository()
            val stored = repository.getOrCreate { key("kid-rewrap") }

            runBlocking {
                assertEquals(listOf(EncryptedPrivateKey("kid-rewrap", stored.privateKeyEncrypted)), repository.encryptedPrivateKeys())
                assertFalse(repository.replaceEncryptedPrivateKey("kid-rewrap", "stale", "ciphertext-new"))
                assertTrue(repository.replaceEncryptedPrivateKey("kid-rewrap", stored.privateKeyEncrypted, "ciphertext-new"))
            }
            assertEquals("ciphertext-new", repository.getOrCreate { error("must not create a key") }.privateKeyEncrypted)
        }

    @Test
    fun `a staged key is promoted once and the previous key retires until its time`() =
        withH2Database(OAuthSigningKeysTable) {
            val repository = PostgreSQLOAuthSigningKeyRepository()
            val active = repository.getOrCreate { key("kid-active") }
            val staged = repository.stageNext { key("kid-next") }
            assertEquals(staged, repository.stageNext { key("kid-other") })
            val retireAfter = Instant.parse("2026-10-10T02:00:00Z")

            assertTrue(repository.promoteNext(active.keyId, staged.keyId, retireAfter))
            assertFalse(repository.promoteNext(active.keyId, staged.keyId, retireAfter))

            val keys = repository.keySet()
            assertEquals("kid-next", keys.active?.keyId)
            assertNull(keys.next)
            assertEquals("kid-active", keys.retiring?.keyId)
            assertEquals(retireAfter, keys.retiring?.retireAfter)
            assertFalse(repository.deleteRetired(Instant.parse("2026-10-10T01:59:59Z")))
            assertTrue(repository.deleteRetired(retireAfter))
            assertNull(repository.keySet().retiring)
        }

    @Test
    fun `a promotion that lost the race changes nothing`() =
        withH2Database(OAuthSigningKeysTable) {
            val repository = PostgreSQLOAuthSigningKeyRepository()
            val first = repository.getOrCreate { key("kid-first") }
            val second = repository.stageNext { key("kid-second") }
            repository.promoteNext(first.keyId, second.keyId, Instant.parse("2026-10-10T02:00:00Z"))
            val third = repository.stageNext { key("kid-third") }
            val before = repository.keySet()

            assertFalse(repository.promoteNext(first.keyId, third.keyId, Instant.parse("2026-10-10T03:00:00Z")))

            assertEquals(before, repository.keySet())
        }

    private fun key(keyId: String) =
        StoredOAuthSigningKey(
            keyId = keyId,
            privateKeyEncrypted = "ciphertext-$keyId",
            publicKeySpki = "spki-$keyId",
            createdAt = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
        )
}
