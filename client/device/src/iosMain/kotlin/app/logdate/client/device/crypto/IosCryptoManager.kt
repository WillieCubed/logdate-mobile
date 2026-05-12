package app.logdate.client.device.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.Sink
import okio.Source
import okio.Timeout
import okio.buffer
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class, DelicateCryptographyApi::class)
class IosCryptoManager : CryptoManager {
    private val aesGcm = CryptographyProvider.Default.get(AES.GCM)

    override suspend fun generateRecoveryPhrase(): List<String> {
        // Generate 128 bits of entropy
        val entropy = ByteArray(16)
        SecRandomCopyBytes(kSecRandomDefault, entropy.size.toULong(), entropy.refTo(0))

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
    ): Sink = IosAesGcmEncryptingSink(sink, key, iv)

    override fun decryptSource(
        source: Source,
        key: ByteArray,
        iv: ByteArray,
    ): Source {
        val bufferedSource = source.buffer()
        val ciphertext =
            try {
                bufferedSource.readByteArray()
            } finally {
                bufferedSource.close()
            }
        val plaintext = aesGcmDecrypt(key, iv, ByteArray(0), ciphertext)
        return Buffer().write(plaintext)
    }

    override fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), bytes.refTo(0))
        return bytes
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray =
        memScoped {
            val output = ByteArray(32) // SHA-256 = 32 bytes

            key.usePinned { keyPinned ->
                data.usePinned { dataPinned ->
                    output.usePinned { outputPinned ->
                        CCHmac(
                            kCCHmacAlgSHA256,
                            keyPinned.addressOf(0),
                            key.size.toULong(),
                            dataPinned.addressOf(0),
                            data.size.toULong(),
                            outputPinned.addressOf(0),
                        )
                    }
                }
            }

            output
        }

    /**
     * Performs AES-GCM encryption for iOS.
     */
    override fun aesGcmEncrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        val importedKey = runBlocking { aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key) }
        val cipher = importedKey.cipher()
        return runBlocking { cipher.encryptWithIv(iv = iv, plaintext = plaintext, associatedData = aad) }
    }

    /**
     * Performs AES-GCM decryption for iOS.
     */
    override fun aesGcmDecrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val importedKey = runBlocking { aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key) }
        val cipher = importedKey.cipher()
        return runBlocking { cipher.decryptWithIv(iv = iv, ciphertext = ciphertext, associatedData = aad) }
    }

    /**
     * HKDF-Extract using iOS CommonCrypto HMAC
     */
    fun hkdfExtract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray =
        memScoped {
            val prk = ByteArray(32) // SHA-256 output

            salt.usePinned { saltPinned ->
                ikm.usePinned { ikmPinned ->
                    prk.usePinned { prkPinned ->
                        CCHmac(
                            kCCHmacAlgSHA256,
                            saltPinned.addressOf(0),
                            salt.size.toULong(),
                            ikmPinned.addressOf(0),
                            ikm.size.toULong(),
                            prkPinned.addressOf(0),
                        )
                    }
                }
            }

            prk
        }

    /**
     * HKDF-Expand using iOS CommonCrypto HMAC
     */
    fun hkdfExpand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length <= 255 * 32) { "Output length too large for HKDF" }

        val result = ByteArray(length)
        var offset = 0
        var counter: Byte = 1
        var t = ByteArray(0)

        while (offset < length) {
            val input = t + info + byteArrayOf(counter)
            t =
                memScoped {
                    val output = ByteArray(32)
                    input.usePinned { inputPinned ->
                        prk.usePinned { prkPinned ->
                            output.usePinned { outputPinned ->
                                CCHmac(
                                    kCCHmacAlgSHA256,
                                    prkPinned.addressOf(0),
                                    prk.size.toULong(),
                                    inputPinned.addressOf(0),
                                    input.size.toULong(),
                                    outputPinned.addressOf(0),
                                )
                            }
                        }
                    }
                    output
                }

            val copyLength = minOf(t.size, length - offset)
            t.copyInto(result, offset, 0, copyLength)
            offset += copyLength
            counter++
        }

        return result
    }

    private inner class IosAesGcmEncryptingSink(
        private val sink: Sink,
        private val key: ByteArray,
        private val iv: ByteArray,
    ) : Sink {
        private val plaintext = Buffer()
        private var closed = false

        override fun write(
            source: Buffer,
            byteCount: Long,
        ) {
            check(!closed) { "Sink is closed." }
            plaintext.write(source, byteCount)
        }

        override fun flush() {
            check(!closed) { "Sink is closed." }
        }

        override fun timeout(): Timeout = sink.timeout()

        override fun close() {
            if (closed) return
            closed = true
            val ciphertext = aesGcmEncrypt(key, iv, ByteArray(0), plaintext.readByteArray())
            val bufferedSink = sink.buffer()
            try {
                bufferedSink.write(ciphertext)
            } finally {
                bufferedSink.close()
            }
        }
    }
}
