package app.logdate.client.device.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA512
import platform.CoreCrypto.kCCSuccess

@OptIn(ExperimentalForeignApi::class)
internal actual fun Bip39.sha256(data: ByteArray): ByteArray =
    memScoped {
        val hash = UByteArray(32) // SHA-256 = 32 bytes
        data.usePinned { dataPinned ->
            hash.usePinned { hashPinned ->
                CC_SHA256(
                    dataPinned.addressOf(0),
                    data.size.toUInt(),
                    hashPinned.addressOf(0),
                )
            }
        }
        hash.toByteArray()
    }

@OptIn(ExperimentalForeignApi::class)
internal actual fun Bip39.mnemonicToSeed(
    mnemonic: String,
    passphrase: String,
): ByteArray =
    memScoped {
        val passwordBytes = mnemonic.encodeToByteArray()
        val salt = ("mnemonic" + passphrase).encodeToByteArray().toUByteArray()
        val derivedKey = UByteArray(64) // 512 bits

        val status =
            derivedKey.usePinned { keyPinned ->
                salt.usePinned { saltPinned ->
                    CCKeyDerivationPBKDF(
                        kCCPBKDF2,
                        mnemonic,
                        passwordBytes.size.toULong(),
                        saltPinned.addressOf(0),
                        salt.size.toULong(),
                        kCCPRFHmacAlgSHA512,
                        2048u,
                        keyPinned.addressOf(0),
                        derivedKey.size.toULong(),
                    )
                }
            }

        require(status == kCCSuccess) { "PBKDF2 derivation failed with status: $status" }
        derivedKey.toByteArray()
    }
