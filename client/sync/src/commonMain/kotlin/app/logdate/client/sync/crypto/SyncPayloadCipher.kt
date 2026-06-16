package app.logdate.client.sync.crypto

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.EncryptedEnvelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SYNC_PAYLOAD_PREFIX = "LDSE1:"

class SyncPayloadCipher(
    private val contentEncryptionService: ContentEncryptionService,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    suspend fun encryptString(
        fieldId: String,
        plaintext: String,
    ): String {
        if (plaintext.startsWith(SYNC_PAYLOAD_PREFIX)) return plaintext
        val envelope = contentEncryptionService.encryptContent(fieldId, plaintext)
        return SYNC_PAYLOAD_PREFIX + json.encodeToString(envelope)
    }

    suspend fun decryptString(
        fieldId: String,
        value: String,
    ): String {
        if (!value.startsWith(SYNC_PAYLOAD_PREFIX)) return value
        val payload = value.removePrefix(SYNC_PAYLOAD_PREFIX)
        val envelope = json.decodeFromString<EncryptedEnvelope>(payload)
        return contentEncryptionService.decryptContent(fieldId, envelope)
    }
}
