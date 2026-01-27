package app.logdate.server.sync

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

class MediaEncryptionServiceTest {

    @Test
    fun `encryption disabled returns original bytes`() {
        val service = MediaEncryptionService(null)
        val payload = "media-bytes".encodeToByteArray()

        val encrypted = service.encryptIfConfigured(payload)
        val decrypted = service.decryptIfNeeded(encrypted)

        assertContentEquals(payload, encrypted)
        assertContentEquals(payload, decrypted)
    }

    @Test
    fun `encryption round trip restores original bytes`() {
        val key = ByteArray(32) { index -> (index + 1).toByte() }
        val service = MediaEncryptionService.fromKeyBytes(key)
        val payload = "secure-media".encodeToByteArray()

        val encrypted = service.encryptIfConfigured(payload)
        val decrypted = service.decryptIfNeeded(encrypted)

        assertNotEquals(payload.size, encrypted.size)
        assertTrue(encrypted.size > payload.size)
        assertContentEquals(payload, decrypted)
    }

    @Test
    fun `decrypting encrypted bytes without key fails`() {
        val key = ByteArray(32) { index -> (index + 1).toByte() }
        val encrypted = MediaEncryptionService.fromKeyBytes(key)
            .encryptIfConfigured("payload".encodeToByteArray())

        val service = MediaEncryptionService(null)

        assertFailsWith<IllegalArgumentException> {
            service.decryptIfNeeded(encrypted)
        }
    }

    @Test
    fun `legacy plaintext remains readable when key is set`() {
        val key = ByteArray(32) { index -> (index + 1).toByte() }
        val service = MediaEncryptionService.fromKeyBytes(key)
        val payload = "legacy".encodeToByteArray()

        val decrypted = service.decryptIfNeeded(payload)

        assertContentEquals(payload, decrypted)
    }

    @Test
    fun `server encryption skips client-encrypted payloads`() {
        val key = ByteArray(32) { index -> (index + 1).toByte() }
        val service = MediaEncryptionService.fromKeyBytes(key)
        val prefix = MediaEncryptionService.clientPrefixBytes()
        val payload = prefix + byteArrayOf(1, 2, 3, 4)

        val encrypted = service.encryptIfConfigured(payload)

        assertContentEquals(payload, encrypted)
    }
}
