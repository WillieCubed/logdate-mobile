package app.logdate.server.identity

import studio.hypertext.atproto.crypto.Base58Btc
import java.math.BigInteger
import java.security.PrivateKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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

            assertEquals("P-256", generated.algorithm)
            assertTrue(generated.publicKeyMultibase.startsWith("z"))
            assertEquals("EC", decrypted.algorithm)

            val helperClass = Class.forName("app.logdate.server.identity.SigningKeyServiceKt")
            val toFixedWidth =
                helperClass
                    .getDeclaredMethod("toFixedWidth", BigInteger::class.java, Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }

            val equalWidth = toFixedWidth.invoke(null, BigInteger("0102", 16), 2) as ByteArray
            val padded = toFixedWidth.invoke(null, BigInteger("01", 16), 4) as ByteArray
            val truncated = toFixedWidth.invoke(null, BigInteger("0102030405", 16), 4) as ByteArray

            assertContentEquals(byteArrayOf(0x01, 0x02), equalWidth)
            assertContentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x01), padded)
            assertContentEquals(byteArrayOf(0x02, 0x03, 0x04, 0x05), truncated)
        }
}
