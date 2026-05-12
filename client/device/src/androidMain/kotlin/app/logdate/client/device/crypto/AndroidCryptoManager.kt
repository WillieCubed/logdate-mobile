package app.logdate.client.device.crypto

import okio.CipherSink
import okio.CipherSource
import okio.Sink
import okio.Source
import okio.buffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AndroidCryptoManager : CryptoManager {
    override suspend fun generateRecoveryPhrase(): List<String> {
        val entropy = ByteArray(16) // 128 bits = 12 words
        SecureRandom().nextBytes(entropy)

        return Bip39.generateMnemonic(entropy)
    }

    override suspend fun deriveMasterKey(phrase: List<String>): ByteArray {
        require(validateRecoveryPhrase(phrase)) {
            "Invalid recovery phrase"
        }

        val mnemonicString = phrase.joinToString(" ")
        val seed = Bip39.mnemonicToSeed(mnemonicString)

        // Use first 32 bytes as master key (AES-256)
        return seed.copyOfRange(0, 32)
    }

    override fun validateRecoveryPhrase(phrase: List<String>): Boolean = Bip39.validateMnemonic(phrase)

    override fun encryptSink(
        sink: Sink,
        key: ByteArray,
        iv: ByteArray,
    ): Sink {
        require(key.size == 32) { "Key must be 32 bytes for AES-256" }
        require(iv.size == 12) { "IV must be 12 bytes for GCM" }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return CipherSink(sink.buffer(), cipher)
    }

    override fun decryptSource(
        source: Source,
        key: ByteArray,
        iv: ByteArray,
    ): Source {
        require(key.size == 32) { "Key must be 32 bytes for AES-256" }
        require(iv.size == 12) { "IV must be 12 bytes for GCM" }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return CipherSource(source.buffer(), cipher)
    }

    override fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    override fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * AES-GCM encryption for content.
     */
    override fun aesGcmEncrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes for AES-256" }
        require(iv.size == 12) { "IV must be 12 bytes for GCM" }

        val secretKey: SecretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv) // 128-bit auth tag

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        cipher.updateAAD(aad)

        return cipher.doFinal(plaintext)
    }

    /**
     * AES-GCM decryption for content.
     */
    override fun aesGcmDecrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes for AES-256" }
        require(iv.size == 12) { "IV must be 12 bytes for GCM" }

        val secretKey: SecretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        cipher.updateAAD(aad)

        return cipher.doFinal(ciphertext)
    }

    /**
     * HKDF-Extract using HMAC-SHA256
     */
    fun hkdfExtract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    /**
     * HKDF-Expand using HMAC-SHA256
     */
    fun hkdfExpand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length <= 255 * 32) { "Output length too large for HKDF" }

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        val result = ByteArray(length)
        var offset = 0
        var counter: Byte = 1
        var t = ByteArray(0)

        while (offset < length) {
            mac.update(t)
            mac.update(info)
            mac.update(counter)
            t = mac.doFinal()

            val copyLength = minOf(t.size, length - offset)
            t.copyInto(result, offset, 0, copyLength)
            offset += copyLength
            counter++
        }

        return result
    }
}
