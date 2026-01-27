package app.logdate.server.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PayloadHeader(val keyId: String, val iv: ByteArray, val ciphertextOffset: Int)

object PayloadHeaderCodec {
    private const val VERSION_1: Byte = 0x01
    private const val MIN_KEYID_LENGTH = 1
    private const val MAX_KEYID_LENGTH = 128

    fun encode(prefix: ByteArray, keyId: String, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val keyIdBytes = keyId.toByteArray(Charsets.UTF_8)
        validateKeyId(keyIdBytes)
        validateIV(iv)

        val totalSize = prefix.size + 1 + 2 + keyIdBytes.size + iv.size + ciphertext.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        buffer.put(prefix)
        buffer.put(VERSION_1)
        buffer.putShort(keyIdBytes.size.toShort())
        buffer.put(keyIdBytes)
        buffer.put(iv)
        buffer.put(ciphertext)

        return buffer.array()
    }

    fun decode(payload: ByteArray, expectedPrefix: ByteArray): PayloadHeader {
        validatePrefix(payload, expectedPrefix)
        validateMinimumSize(payload, expectedPrefix.size)

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        buffer.position(expectedPrefix.size)

        val version = buffer.get()
        validateVersion(version)

        val keyIdLength = buffer.short.toInt() and 0xFFFF
        validateKeyIdLength(keyIdLength)

        val keyIdBytes = ByteArray(keyIdLength)
        buffer.get(keyIdBytes)
        val keyId = String(keyIdBytes, Charsets.UTF_8)

        val iv = ByteArray(AesGcmCipher.IV_SIZE_BYTES)
        buffer.get(iv)

        return PayloadHeader(keyId, iv, buffer.position())
    }

    private fun validatePrefix(payload: ByteArray, expectedPrefix: ByteArray) {
        if (!payload.hasPrefix(expectedPrefix)) {
            throw EncryptionException("Invalid payload prefix")
        }
    }

    private fun validateMinimumSize(payload: ByteArray, prefixSize: Int) {
        val minSize = prefixSize + 1 + 2 + MIN_KEYID_LENGTH + AesGcmCipher.IV_SIZE_BYTES + AesGcmCipher.GCM_TAG_BYTES
        if (payload.size < minSize) {
            throw EncryptionException("Payload too short: ${payload.size} bytes (minimum: $minSize)")
        }
    }

    private fun validateVersion(version: Byte) {
        if (version != VERSION_1) {
            throw EncryptionException("Unsupported version: $version")
        }
    }

    private fun validateKeyIdLength(length: Int) {
        if (length < MIN_KEYID_LENGTH || length > MAX_KEYID_LENGTH) {
            throw EncryptionException("Invalid keyId length: $length")
        }
    }

    private fun validateKeyId(keyIdBytes: ByteArray) {
        if (keyIdBytes.size < MIN_KEYID_LENGTH || keyIdBytes.size > MAX_KEYID_LENGTH) {
            throw EncryptionException("Invalid keyId size: ${keyIdBytes.size}")
        }
    }

    private fun validateIV(iv: ByteArray) {
        if (iv.size != AesGcmCipher.IV_SIZE_BYTES) {
            throw EncryptionException("Invalid IV size: ${iv.size}")
        }
    }
}
