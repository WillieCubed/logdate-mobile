package app.logdate.client.sync.crypto

class StoredMediaPayloadCrypto(
    private val keyProvider: MediaPayloadKeyProvider
) : MediaPayloadCrypto {
    private var cachedCrypto: MediaPayloadCrypto? = null

    override suspend fun encrypt(data: ByteArray): ByteArray {
        return getCrypto().encrypt(data)
    }

    override suspend fun decrypt(data: ByteArray): ByteArray {
        return getCrypto().decrypt(data)
    }

    private suspend fun getCrypto(): MediaPayloadCrypto {
        cachedCrypto?.let { return it }
        val key = keyProvider.getOrCreateKey()
        return AesGcmMediaPayloadCrypto(key).also { cachedCrypto = it }
    }
}
