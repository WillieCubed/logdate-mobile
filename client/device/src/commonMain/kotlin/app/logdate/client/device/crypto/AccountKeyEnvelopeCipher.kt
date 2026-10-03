@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.client.device.crypto

import kotlin.uuid.Uuid

class UnlockedAccountKeys(
    val identity: ByteArray,
    val media: ByteArray,
)

class AccountKeyEnvelopeCipher(
    private val crypto: CryptoManager,
) {
    fun seal(
        accountId: String,
        credentialId: String,
        unlockSecret: ByteArray,
        identity: ByteArray,
        media: ByteArray,
    ): ByteArray {
        require(identity.size == 32 && media.size == 32) { "Invalid journal keys" }
        val binding = binding(accountId, credentialId)
        val key = key(unlockSecret, binding)
        val plaintext = identity + media
        return try {
            val nonce = crypto.generateRandomBytes(12)
            PREFIX + nonce + crypto.aesGcmEncrypt(key, nonce, PREFIX + binding.encodeToByteArray(), plaintext)
        } finally {
            key.fill(0)
            plaintext.fill(0)
        }
    }

    fun open(
        accountId: String,
        credentialId: String,
        unlockSecret: ByteArray,
        envelope: ByteArray,
    ): UnlockedAccountKeys {
        require(envelope.size == 97 && envelope.copyOfRange(0, 5).contentEquals(PREFIX)) { "Invalid key envelope" }
        val binding = binding(accountId, credentialId)
        val key = key(unlockSecret, binding)
        val plaintext =
            try {
                crypto.aesGcmDecrypt(key, envelope.copyOfRange(5, 17), PREFIX + binding.encodeToByteArray(), envelope.copyOfRange(17, 97))
            } finally {
                key.fill(0)
            }
        return try {
            require(plaintext.size == 64) { "Invalid journal keys" }
            UnlockedAccountKeys(plaintext.copyOfRange(0, 32), plaintext.copyOfRange(32, 64))
        } finally {
            plaintext.fill(0)
        }
    }

    private fun key(
        secret: ByteArray,
        binding: String,
    ): ByteArray = KeyDerivation(crypto).deriveKey(secret, CONTEXT, binding)

    private fun binding(
        accountId: String,
        credentialId: String,
    ): String {
        require(credentialId.matches(Regex("[A-Za-z0-9_-]{1,2048}"))) { "Invalid credential binding" }
        return "account-key:v1:${Uuid.parse(accountId)}:$credentialId"
    }

    private companion object {
        val PREFIX = "LDKE1".encodeToByteArray()
        const val CONTEXT = "logdate-account-key-envelope-v1"
    }
}
