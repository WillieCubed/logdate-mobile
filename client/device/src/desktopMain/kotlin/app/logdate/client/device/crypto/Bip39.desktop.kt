package app.logdate.client.device.crypto

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal actual fun Bip39.sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

internal actual fun Bip39.mnemonicToSeed(
    mnemonic: String,
    passphrase: String,
): ByteArray {
    val password = mnemonic.toCharArray()
    val salt = ("mnemonic" + passphrase).toByteArray(Charsets.UTF_8)

    val spec = PBEKeySpec(password, salt, 2048, 512)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")

    return factory.generateSecret(spec).encoded
}
