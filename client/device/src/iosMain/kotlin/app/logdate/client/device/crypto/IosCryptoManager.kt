package app.logdate.client.device.crypto

import kotlinx.cinterop.*
import okio.Sink
import okio.Source
import platform.CoreCrypto.*
import platform.Foundation.*
import platform.Security.*

@OptIn(ExperimentalForeignApi::class)
actual class IosCryptoManager : CryptoManager {
    
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
    
    override fun validateRecoveryPhrase(phrase: List<String>): Boolean {
        return Bip39.validateMnemonic(phrase)
    }
    
    override fun encryptSink(sink: Sink, key: ByteArray, iv: ByteArray): Sink {
        TODO("Not needed for initial implementation - used for backup streaming")
    }
    
    override fun decryptSource(source: Source, key: ByteArray, iv: ByteArray): Source {
        TODO("Not needed for initial implementation - used for backup streaming")
    }
    
    override fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), bytes.refTo(0))
        return bytes
    }
    
    @OptIn(ExperimentalForeignApi::class)
    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = memScoped {
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
                        outputPinned.addressOf(0)
                    )
                }
            }
        }
        
        output
    }
    
    /**
     * AES-GCM encryption using iOS CommonCrypto.
     */
    fun aesGcmEncrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray
    ): ByteArray = memScoped {
        require(key.size == 32) { "Key must be 32 bytes for AES-256" }
        require(iv.size == 12) { "IV must be 12 bytes for GCM" }
        
        val algorithm = kCCAlgorithmAES
        val tagLength = 16
        
        val ciphertextLength = plaintext.size + tagLength
        val ciphertext = ByteArray(ciphertextLength)
        
        val status = ciphertext.usePinned { ciphertextPinned ->
            plaintext.usePinned { plaintextPinned ->
                key.usePinned { keyPinned ->
                    iv.usePinned { ivPinned ->
                        aad.usePinned { aadPinned ->
                            CCCryptorGCMOneshotEncrypt(
                                algorithm,
                                keyPinned.addressOf(0),
                                key.size.toULong(),
                                ivPinned.addressOf(0),
                                iv.size.toULong(),
                                aadPinned.addressOf(0),
                                aad.size.toULong(),
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
        }
        
        require(status == kCCSuccess) { "AES-GCM encryption failed with status: $status" }
        ciphertext
    }
    
    /**
     * AES-GCM decryption using iOS CommonCrypto.
     */
    fun aesGcmDecrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray
    ): ByteArray = memScoped {
        require(key.size == 32) { "Key must be 32 bytes for AES-256" }
        require(iv.size == 12) { "IV must be 12 bytes for GCM" }
        require(ciphertext.size >= 16) { "Ciphertext too short for GCM tag" }
        
        val algorithm = kCCAlgorithmAES
        val tagLength = 16
        val encryptedDataLength = ciphertext.size - tagLength
        
        val plaintext = ByteArray(encryptedDataLength)
        
        val status = plaintext.usePinned { plaintextPinned ->
            ciphertext.usePinned { ciphertextPinned ->
                key.usePinned { keyPinned ->
                    iv.usePinned { ivPinned ->
                        aad.usePinned { aadPinned ->
                            CCCryptorGCMOneshotDecrypt(
                                algorithm,
                                keyPinned.addressOf(0),
                                key.size.toULong(),
                                ivPinned.addressOf(0),
                                iv.size.toULong(),
                                aadPinned.addressOf(0),
                                aad.size.toULong(),
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
        }
        
        require(status == kCCSuccess) { "AES-GCM decryption failed with status: $status" }
        plaintext
    }
    
    /**
     * HKDF-Extract using iOS CommonCrypto HMAC
     */
    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray = memScoped {
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
                        prkPinned.addressOf(0)
                    )
                }
            }
        }
        
        prk
    }
    
    /**
     * HKDF-Expand using iOS CommonCrypto HMAC
     */
    fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 255 * 32) { "Output length too large for HKDF" }
        
        val result = ByteArray(length)
        var offset = 0
        var counter: Byte = 1
        var t = ByteArray(0)
        
        while (offset < length) {
            val input = t + info + byteArrayOf(counter)
            t = memScoped {
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
                                outputPinned.addressOf(0)
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
}
