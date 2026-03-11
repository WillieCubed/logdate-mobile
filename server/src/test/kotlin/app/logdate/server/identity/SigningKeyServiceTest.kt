package app.logdate.server.identity

import studio.hypertext.atproto.crypto.Base58Btc
import java.security.PrivateKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SigningKeyServiceTest {
    @Test
    fun `ensureActiveKey reuses existing key until rotation`() =
        kotlinx.coroutines.test.runTest {
            val repository = InMemorySigningKeyRepository()
            val service = SigningKeyService(repository, "test-kek")
            val accountId = Uuid.random()

            val first = service.ensureActiveKey(accountId)
            val second = service.ensureActiveKey(accountId)
            val rotated = service.rotateKey(accountId)

            assertEquals(first.id, second.id)
            assertNotEquals(first.id, rotated.id)
            assertEquals(rotated.id, repository.findActiveByAccountId(accountId)?.id)
        }

    @Test
    fun `decryptPrivateKey returns EC private key`() =
        kotlinx.coroutines.test.runTest {
            val service = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
            val key = service.ensureActiveKey(Uuid.random())

            val decrypted: PrivateKey = service.decryptPrivateKey(key)

            assertTrue(decrypted.encoded.isNotEmpty())
            assertEquals("EC", decrypted.algorithm)
        }

    @Test
    fun `exported signing keys round trip through passphrase encryption`() =
        kotlinx.coroutines.test.runTest {
            val service = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
            val accountId = Uuid.random()
            val activeKey = service.ensureActiveKey(accountId)

            val exported = service.exportActiveKey(accountId = accountId, passphrase = "correct horse battery staple")
            val decrypted = service.decryptExportedKey(exported, passphrase = "correct horse battery staple")

            assertEquals(activeKey.publicKeyMultibase, exported.publicKeyMultibase)
            assertTrue(exported.publicKeyDidKey.startsWith("did:key:z"))
            assertTrue(exported.encryptedPrivateKey.isNotBlank())
            assertTrue(exported.salt.isNotBlank())
            assertTrue(exported.iv.isNotBlank())
            assertEquals("EC", decrypted.algorithm)
        }

    @Test
    fun `prepared and imported signing keys can be activated safely`() =
        kotlinx.coroutines.test.runTest {
            val repository = InMemorySigningKeyRepository()
            val service = SigningKeyService(repository, "test-kek")
            val accountId = Uuid.random()
            val first = service.ensureActiveKey(accountId)

            val prepared = service.prepareKey(accountId)
            val activated = service.activatePreparedKey(accountId, prepared)
            val exported = service.exportActiveKey(accountId, "import-secret")
            val imported = service.importActiveKey(accountId, exported, "import-secret")

            assertNotEquals(first.id, prepared.id)
            assertEquals(prepared.id, activated.id)
            assertEquals(exported.publicKeyMultibase, imported.publicKeyMultibase)
            assertEquals(imported.id, repository.findActiveByAccountId(accountId)?.id)
        }

    @Test
    fun `importActiveKey rejects mismatched public did key metadata`() =
        kotlinx.coroutines.test.runTest {
            val service = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
            val accountId = Uuid.random()
            val exported = service.exportActiveKey(service.ensureActiveKey(accountId).accountId, "secret")

            assertFailsWith<IllegalArgumentException> {
                service.importActiveKey(
                    accountId = accountId,
                    exportedKey = exported.copy(publicKeyDidKey = "did:key:zDifferent"),
                    passphrase = "secret",
                )
            }
        }

    @Test
    fun `base58 encoder handles empty and leading zero bytes`() {
        assertEquals("", Base58Btc.encode(byteArrayOf()))
        assertEquals("1", Base58Btc.encode(byteArrayOf(0)))
        assertEquals("112", Base58Btc.encode(byteArrayOf(0, 0, 1)))
    }

    @Test
    fun `default constructor seed and fixed width helper branches are covered`() =
        kotlinx.coroutines.test.runTest {
            val defaultSeedService = SigningKeyService(InMemorySigningKeyRepository())

            val generated = defaultSeedService.generateKeyPair()
            val decrypted = defaultSeedService.decryptPrivateKey(defaultSeedService.ensureActiveKey(Uuid.random()))

            assertEquals("K-256", generated.algorithm)
            assertTrue(generated.publicKeyMultibase.startsWith("z"))
            assertEquals("EC", decrypted.algorithm)
        }

    @Test
    fun `service can generate both supported hosted signing key curves`() {
        val service = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")

        val p256 = service.generateKeyPair(studio.hypertext.atproto.crypto.EcCurve.P256)
        val k256 = service.generateKeyPair(studio.hypertext.atproto.crypto.EcCurve.K256)

        assertEquals("P-256", p256.algorithm)
        assertEquals("K-256", k256.algorithm)
        assertTrue(p256.publicKeyMultibase.startsWith("z"))
        assertTrue(k256.publicKeyMultibase.startsWith("z"))
    }
}
