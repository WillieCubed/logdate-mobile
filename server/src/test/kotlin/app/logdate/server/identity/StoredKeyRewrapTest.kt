package app.logdate.server.identity

import app.logdate.server.oauth.InMemoryOAuthSigningKeyRepository
import app.logdate.server.oauth.StoredOAuthSigningKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class StoredKeyRewrapTest {
    private val oldCipher = AtRestKeyCipher(OLD_KEK)
    private val newCipher = AtRestKeyCipher(NEW_KEK)
    private val rewrapCipher = AtRestKeyCipher(NEW_KEK, decryptOnlySeeds = listOf(OLD_KEK))

    @Test
    fun `re-encrypts signing keys and the OAuth key under the new key-encryption key`() =
        runTest {
            val signingKeys = InMemorySigningKeyRepository()
            val signingKey = signingKeys.save(signingKey(oldCipher.encrypt(SIGNING_KEY)))
            val oauthKeys = oauthRepository(oldCipher.encrypt(OAUTH_KEY))

            val report = rewrap(signingKeys, oauthKeys).run()

            assertEquals(StoredKeyRewrap.Report(rewrapped = 2, alreadyCurrent = 0, unreadable = emptyList()), report)
            assertContentEquals(SIGNING_KEY, newCipher.decrypt(signingKeys.encryptedValueOf(signingKey.id.toString())))
            assertContentEquals(OAUTH_KEY, newCipher.decrypt(oauthKeys.encryptedValueOf(OAUTH_KEY_ID)))
        }

    @Test
    fun `running again changes nothing`() =
        runTest {
            val signingKeys = InMemorySigningKeyRepository().apply { save(signingKey(oldCipher.encrypt(SIGNING_KEY))) }
            val oauthKeys = oauthRepository(oldCipher.encrypt(OAUTH_KEY))
            rewrap(signingKeys, oauthKeys).run()
            val afterFirstRun = signingKeys.encryptedPrivateKeys() + oauthKeys.encryptedPrivateKeys()

            val report = rewrap(signingKeys, oauthKeys).run()

            assertEquals(StoredKeyRewrap.Report(rewrapped = 0, alreadyCurrent = 2, unreadable = emptyList()), report)
            assertEquals(afterFirstRun, signingKeys.encryptedPrivateKeys() + oauthKeys.encryptedPrivateKeys())
        }

    @Test
    fun `keys no known key-encryption key can read are reported and left unchanged`() =
        runTest {
            val unreadable = AtRestKeyCipher(OTHER_KEK).encrypt(SIGNING_KEY)
            val signingKeys = InMemorySigningKeyRepository()
            val stored = signingKeys.save(signingKey(unreadable))

            val report = rewrap(signingKeys, InMemoryOAuthSigningKeyRepository()).run()

            assertEquals(listOf("signing_keys/${stored.id}"), report.unreadable)
            assertEquals(0, report.rewrapped)
            assertEquals(unreadable, signingKeys.encryptedValueOf(stored.id.toString()))
        }

    @Test
    fun `a key another instance already replaced isn't overwritten`() =
        runTest {
            val signingKeys = InMemorySigningKeyRepository()
            val stored = signingKeys.save(signingKey(oldCipher.encrypt(SIGNING_KEY)))
            val replacedElsewhere = newCipher.encrypt(SIGNING_KEY)
            val racingStore =
                object : EncryptedPrivateKeyStore by signingKeys {
                    override suspend fun encryptedPrivateKeys(): List<EncryptedPrivateKey> {
                        val snapshot = signingKeys.encryptedPrivateKeys()
                        signingKeys.replaceEncryptedPrivateKey(stored.id.toString(), stored.privateKeyEncrypted, replacedElsewhere)
                        return snapshot
                    }
                }

            val report = StoredKeyRewrap(mapOf("signing_keys" to racingStore), rewrapCipher).run()

            assertEquals(StoredKeyRewrap.Report(rewrapped = 0, alreadyCurrent = 1, unreadable = emptyList()), report)
            assertEquals(replacedElsewhere, signingKeys.encryptedValueOf(stored.id.toString()))
        }

    private fun rewrap(
        signingKeys: SigningKeyRepository,
        oauthKeys: InMemoryOAuthSigningKeyRepository,
    ) = StoredKeyRewrap(mapOf("signing_keys" to signingKeys, "oauth_signing_keys" to oauthKeys), rewrapCipher)

    private fun oauthRepository(privateKeyEncrypted: String) =
        InMemoryOAuthSigningKeyRepository().apply {
            getOrCreate {
                StoredOAuthSigningKey(
                    keyId = OAUTH_KEY_ID,
                    privateKeyEncrypted = privateKeyEncrypted,
                    publicKeySpki = "spki",
                    createdAt = Clock.System.now(),
                )
            }
        }

    private fun signingKey(privateKeyEncrypted: String) =
        StoredSigningKey(
            id = Uuid.random(),
            accountId = Uuid.random(),
            publicKeyMultibase = "zPublicKey",
            privateKeyEncrypted = privateKeyEncrypted,
            createdAt = Clock.System.now(),
        )

    private suspend fun EncryptedPrivateKeyStore.encryptedValueOf(id: String): String =
        encryptedPrivateKeys().single { it.id == id }.privateKeyEncrypted

    private companion object {
        const val OLD_KEK = "old-key-encryption-key-0123456789abcdef"
        const val NEW_KEK = "new-key-encryption-key-0123456789abcdef"
        const val OTHER_KEK = "unrelated-key-encryption-key-0123456789"
        const val OAUTH_KEY_ID = "oauth-kid"
        val SIGNING_KEY = "signing key".encodeToByteArray()
        val OAUTH_KEY = "oauth key".encodeToByteArray()
    }
}
