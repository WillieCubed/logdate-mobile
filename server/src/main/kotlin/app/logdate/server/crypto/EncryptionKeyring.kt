package app.logdate.server.crypto

import java.util.Base64

data class EncryptionKey(val keyId: String, val keyBytes: ByteArray)

interface EncryptionKeyring {
    fun getActiveKey(): EncryptionKey
    fun getKey(keyId: String): EncryptionKey?
}

class EnvironmentKeyring : EncryptionKeyring {
    private val keys: Map<String, EncryptionKey>
    private val activeKeyId: String

    init {
        val rawKey = System.getenv("SERVER_ENCRYPTION_KEY")
            ?: throw IllegalStateException("SERVER_ENCRYPTION_KEY not configured")
        val keyBytes = decodeKey(rawKey)
        activeKeyId = System.getenv("SERVER_ENCRYPTION_KEY_ID") ?: "default"
        keys = mapOf(activeKeyId to EncryptionKey(activeKeyId, keyBytes))
    }

    override fun getActiveKey(): EncryptionKey = keys.getValue(activeKeyId)

    override fun getKey(keyId: String): EncryptionKey? = keys[keyId]

    private fun decodeKey(rawKey: String): ByteArray {
        val decoded = try {
            Base64.getDecoder().decode(rawKey)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("SERVER_ENCRYPTION_KEY must be base64-encoded.", e)
        }

        if (decoded.size !in setOf(16, 24, 32)) {
            throw IllegalArgumentException(
                "SERVER_ENCRYPTION_KEY must decode to 16, 24, or 32 bytes (AES-128/192/256)."
            )
        }

        return decoded
    }
    
    companion object {
        fun fromEnvironmentOrNull(): EnvironmentKeyring? {
            return try {
                EnvironmentKeyring()
            } catch (e: IllegalStateException) {
                null
            }
        }
    }
}

object NoOpKeyring : EncryptionKeyring {
    private val dummyKey = EncryptionKey("noop", ByteArray(32))
    override fun getActiveKey() = dummyKey
    override fun getKey(keyId: String) = dummyKey
}
