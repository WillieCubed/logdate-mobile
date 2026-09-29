package app.logdate.feature.core.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Seals Cloud archive bytes with a key derived from the person's recovery identity. */
class CloudArchiveCipher(
    private val keyProvider: suspend () -> ByteArray,
) {
    suspend fun encrypt(archive: File): ByteArray {
        val key = keyProvider().also { require(it.size == KEY_SIZE) }
        val nonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))

        val output = ByteArrayOutputStream()
        output.write(PREFIX)
        output.write(nonce)
        CipherOutputStream(output, cipher).use { encrypted -> archive.inputStream().use { it.copyTo(encrypted) } }
        return output.toByteArray()
    }

    suspend fun decrypt(
        payload: ByteArray,
        destination: File,
    ) {
        try {
            if (!payload.startsWith(PREFIX)) {
                require(payload.isZip()) { "Cloud archive has an unknown format" }
                destination.writeBytes(payload)
                return
            }
            require(payload.size > PREFIX.size + NONCE_SIZE + TAG_BITS / 8) { "Cloud archive is incomplete" }
            val key = keyProvider().also { require(it.size == KEY_SIZE) }
            val nonce = payload.copyOfRange(PREFIX.size, PREFIX.size + NONCE_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))

            val bodyOffset = PREFIX.size + NONCE_SIZE
            ByteArrayInputStream(payload, bodyOffset, payload.size - bodyOffset).use { source ->
                CipherInputStream(source, cipher).use { decrypted ->
                    destination.outputStream().use { decrypted.copyTo(it) }
                }
            }
            require(destination.inputStream().use { input -> input.read() == 'P'.code && input.read() == 'K'.code }) {
                "Cloud archive did not decrypt to a ZIP"
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    companion object {
        const val MANIFEST = "{\"format\":\"logdate-cloud-backup\",\"encryption\":\"identity-aes-gcm-v1\"}"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 32
        private const val NONCE_SIZE = 12
        private const val TAG_BITS = 128
        private val PREFIX = "LDCB1".encodeToByteArray()
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun ByteArray.isZip(): Boolean = size >= 2 && this[0] == 'P'.code.toByte() && this[1] == 'K'.code.toByte()
