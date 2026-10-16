package app.logdate.client.sync.crypto

class StoredMediaPayloadCrypto(
    private val keyProvider: MediaPayloadKeyProvider,
) : MediaPayloadCrypto {
    override suspend fun decrypt(data: ByteArray): ByteArray = getCrypto().decrypt(data)

    override suspend fun streamEncryptor(): MediaStreamEncryptor = getCrypto().streamEncryptor()

    private suspend fun getCrypto(): MediaPayloadCrypto {
        val key = keyProvider.getOrCreateKey()
        return try {
            AesGcmMediaPayloadCrypto(key)
        } finally {
            key.fill(0)
        }
    }
}
