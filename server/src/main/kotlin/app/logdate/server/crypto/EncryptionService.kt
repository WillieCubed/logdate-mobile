package app.logdate.server.crypto

class EncryptionService(
    private val policy: EncryptionPolicy,
    private val codec: PayloadCodec
) {
    fun processMediaUpload(
        payload: ByteArray,
        userId: String,
        mediaId: String,
        contentId: String
    ): ProcessedPayload {
        val decision = policy.evaluate(payload)
        return applyUploadDecision(decision, payload) { 
            codec.encryptMedia(payload, userId, mediaId, contentId)
        }
    }

    fun processMediaDownload(payload: ByteArray, shouldDecrypt: Boolean): ByteArray {
        if (!shouldDecrypt) return payload
        return if (payload.hasPrefix(PayloadPrefixes.SERVER_MEDIA)) {
            codec.decryptMedia(payload)
        } else {
            payload
        }
    }

    fun processBackupUpload(
        payload: ByteArray,
        userId: String,
        backupId: String
    ): ProcessedPayload {
        val decision = policy.evaluate(payload)
        return applyUploadDecision(decision, payload) { 
            codec.encryptBackup(payload, userId, backupId)
        }
    }

    fun processBackupDownload(payload: ByteArray, shouldDecrypt: Boolean): ByteArray {
        if (!shouldDecrypt) return payload
        return if (payload.hasPrefix(PayloadPrefixes.SERVER_BACKUP)) {
            codec.decryptBackup(payload)
        } else {
            payload
        }
    }

    private fun applyUploadDecision(
        decision: PolicyDecision,
        payload: ByteArray,
        encrypt: () -> ByteArray
    ): ProcessedPayload {
        return when (decision) {
            is PolicyDecision.Reject -> throw EncryptionPolicyException(decision.reason)
            PolicyDecision.AcceptPlaintext -> ProcessedPayload(payload, encrypted = false)
            PolicyDecision.AcceptClientCiphertext -> ProcessedPayload(payload, encrypted = true)
            PolicyDecision.AcceptServerCiphertext -> ProcessedPayload(payload, encrypted = true)
            PolicyDecision.EncryptAtRest -> ProcessedPayload(encrypt(), encrypted = true)
        }
    }

    companion object {
        fun fromEnvironment(): EncryptionService {
            val policy = EncryptionPolicy.fromEnvironment()
            val keyring = EnvironmentKeyring()
            val codec = PayloadCodec(keyring)
            return EncryptionService(policy, codec)
        }
    }
}

data class ProcessedPayload(val data: ByteArray, val encrypted: Boolean)

class EncryptionPolicyException(message: String) : Exception(message)
