package app.logdate.client.sync.crypto

import kotlinx.cinterop.*
import platform.CoreCrypto.*
import platform.Foundation.*
import platform.Security.*

@OptIn(ExperimentalForeignApi::class)
actual class AesGcmMediaPayloadCrypto actual constructor(key: ByteArray) : MediaPayloadCrypto {
    private val keyBytes = key.copyOf()

    init {
        require(keyBytes.size in setOf(16, 24, 32)) {
            "Media encryption key must be 16, 24, or 32 bytes."
        }
    }

    actual override fun encrypt(data: ByteArray): ByteArray {
        if (data.hasClientMediaPrefix()) return data

        val iv = ByteArray(CLIENT_MEDIA_IV_SIZE_BYTES)
        SecRandomCopyBytes(kSecRandomDefault, iv.size.toULong(), iv.refTo(0))

        val ciphertext = aesGcmEncrypt(data, keyBytes, iv)
        val output = ByteArray(CLIENT_MEDIA_PREFIX_BYTES.size + iv.size + ciphertext.size)
        CLIENT_MEDIA_PREFIX_BYTES.copyInto(output, 0)
        iv.copyInto(output, CLIENT_MEDIA_PREFIX_BYTES.size)
        ciphertext.copyInto(output, CLIENT_MEDIA_PREFIX_BYTES.size + iv.size)
        return output
    }

    actual override fun decrypt(data: ByteArray): ByteArray {
        if (!data.hasClientMediaPrefix()) return data
        require(data.size > CLIENT_MEDIA_PREFIX_BYTES.size + CLIENT_MEDIA_IV_SIZE_BYTES) {
            "Encrypted media payload is too short."
        }

        val ivStart = CLIENT_MEDIA_PREFIX_BYTES.size
        val ivEnd = ivStart + CLIENT_MEDIA_IV_SIZE_BYTES
        val iv = data.copyOfRange(ivStart, ivEnd)
        val ciphertext = data.copyOfRange(ivEnd, data.size)
        return aesGcmDecrypt(ciphertext, keyBytes, iv)
    }

    private fun aesGcmEncrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return memScoped {
            val plaintextData = plaintext.toNSData()
            val keyData = key.toNSData()
            val ivData = iv.toNSData()

            val algorithm = kCCAlgorithmAES
            val options = kCCOptionECBMode.toUInt()
            val tagLength = 16

            val ciphertextLength = plaintext.size + tagLength
            val ciphertext = ByteArray(ciphertextLength)

            val status = ciphertext.usePinned { ciphertextPinned ->
                plaintext.usePinned { plaintextPinned ->
                    key.usePinned { keyPinned ->
                        iv.usePinned { ivPinned ->
                            val dataOutMoved = alloc<size_tVar>()
                            CCCryptorGCMOneshotEncrypt(
                                algorithm,
                                keyPinned.addressOf(0),
                                key.size.toULong(),
                                ivPinned.addressOf(0),
                                iv.size.toULong(),
                                null,
                                0u,
                                plaintextPinned.addressOf(0),
                                plaintext.size.toULong(),
                                ciphertextPinned.addressOf(0),
                                ciphertextPinned.addressOf(plaintext.size),
                                tagLength.toULong()
                            )
                        }
                    }
                }
            }

            require(status == kCCSuccess) { "AES-GCM encryption failed with status: $status" }
            ciphertext
        }
    }

    private fun aesGcmDecrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return memScoped {
            require(ciphertext.size >= 16) { "Ciphertext too short for GCM tag" }

            val algorithm = kCCAlgorithmAES
            val tagLength = 16
            val encryptedDataLength = ciphertext.size - tagLength

            val plaintext = ByteArray(encryptedDataLength)

            val status = plaintext.usePinned { plaintextPinned ->
                ciphertext.usePinned { ciphertextPinned ->
                    key.usePinned { keyPinned ->
                        iv.usePinned { ivPinned ->
                            CCCryptorGCMOneshotDecrypt(
                                algorithm,
                                keyPinned.addressOf(0),
                                key.size.toULong(),
                                ivPinned.addressOf(0),
                                iv.size.toULong(),
                                null,
                                0u,
                                ciphertextPinned.addressOf(0),
                                encryptedDataLength.toULong(),
                                plaintextPinned.addressOf(0),
                                ciphertextPinned.addressOf(encryptedDataLength),
                                tagLength.toULong()
                            )
                        }
                    }
                }
            }

            require(status == kCCSuccess) { "AES-GCM decryption failed with status: $status" }
            plaintext
        }
    }

    private fun ByteArray.toNSData(): NSData {
        return this.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
        }
    }
}
