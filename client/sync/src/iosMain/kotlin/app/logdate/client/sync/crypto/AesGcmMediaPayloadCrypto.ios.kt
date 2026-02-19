package app.logdate.client.sync.crypto

import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual class AesGcmMediaPayloadCrypto actual constructor(key: ByteArray) : MediaPayloadCrypto {
    private val keyBytes = key.copyOf()

    init {
        require(keyBytes.size in setOf(16, 24, 32)) {
            "Media encryption key must be 16, 24, or 32 bytes."
        }
    }

    actual override suspend fun encrypt(data: ByteArray): ByteArray {
        if (data.hasClientMediaPrefix()) return data
        error("AES-GCM encryption is not available on iOS yet.")
    }

    actual override suspend fun decrypt(data: ByteArray): ByteArray {
        if (!data.hasClientMediaPrefix()) return data
        error("AES-GCM decryption is not available on iOS yet.")
    }

}
