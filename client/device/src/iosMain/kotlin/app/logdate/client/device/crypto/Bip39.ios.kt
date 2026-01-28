package app.logdate.client.device.crypto

import kotlinx.cinterop.*
import platform.CoreCrypto.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun Bip39.sha256(data: ByteArray): ByteArray = memScoped {
    val hash = ByteArray(32) // SHA-256 = 32 bytes
    data.usePinned { dataPinned ->
        CC_SHA256(dataPinned.addressOf(0), data.size.toUInt(), hash.refTo(0))
    }
    hash
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun Bip39.mnemonicToSeed(mnemonic: String, passphrase: String): ByteArray = memScoped {
    val password = mnemonic.encodeToByteArray()
    val salt = ("mnemonic" + passphrase).encodeToByteArray()
    
    val derivedKey = ByteArray(64) // 512 bits
    
    val status = derivedKey.usePinned { keyPinned ->
        password.usePinned { passwordPinned ->
            salt.usePinned { saltPinned ->
                CCKeyDerivationPBKDF(
                    kCCPBKDF2,
                    passwordPinned.addressOf(0)?.reinterpret(),
                    password.size.toULong(),
                    saltPinned.addressOf(0)?.reinterpret(),
                    salt.size.toULong(),
                    kCCPRFHmacAlgSHA512,
                    2048u,
                    keyPinned.addressOf(0)?.reinterpret(),
                    64u
                )
            }
        }
    }
    
    require(status == kCCSuccess) { "PBKDF2 derivation failed with status: $status" }
    derivedKey
}
