package app.logdate.server.crypto

class PayloadCodec(
    private val keyring: EncryptionKeyring,
    private val cipher: AesGcmCipher = AesGcmCipher(),
) {
    fun encryptMedia(
        plaintext: ByteArray,
        userId: String,
        mediaId: String,
        contentId: String,
    ): ByteArray {
        val activeKey = keyring.getActiveKey()
        val iv = cipher.generateIV()

        val ciphertext = cipher.encrypt(plaintext, activeKey.keyBytes, iv, null)
        return PayloadHeaderCodec.encode(PayloadPrefixes.SERVER_MEDIA, activeKey.keyId, iv, ciphertext)
    }

    fun decryptMedia(payload: ByteArray): ByteArray {
        val header = PayloadHeaderCodec.decode(payload, PayloadPrefixes.SERVER_MEDIA)
        val key =
            keyring.getKey(header.keyId)
                ?: throw EncryptionException("Key not found: ${header.keyId}")

        val ciphertext = payload.copyOfRange(header.ciphertextOffset, payload.size)
        return cipher.decrypt(ciphertext, key.keyBytes, header.iv, null)
    }

    fun encryptBackup(
        plaintext: ByteArray,
        userId: String,
        backupId: String,
    ): ByteArray {
        val activeKey = keyring.getActiveKey()
        val iv = cipher.generateIV()

        val ciphertext = cipher.encrypt(plaintext, activeKey.keyBytes, iv, null)
        return PayloadHeaderCodec.encode(PayloadPrefixes.SERVER_BACKUP, activeKey.keyId, iv, ciphertext)
    }

    fun decryptBackup(payload: ByteArray): ByteArray {
        val header = PayloadHeaderCodec.decode(payload, PayloadPrefixes.SERVER_BACKUP)
        val key =
            keyring.getKey(header.keyId)
                ?: throw EncryptionException("Key not found: ${header.keyId}")

        val ciphertext = payload.copyOfRange(header.ciphertextOffset, payload.size)
        return cipher.decrypt(ciphertext, key.keyBytes, header.iv, null)
    }
}

class EncryptionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
