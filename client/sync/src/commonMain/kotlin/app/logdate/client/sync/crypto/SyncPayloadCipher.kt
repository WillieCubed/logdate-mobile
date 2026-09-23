package app.logdate.client.sync.crypto

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.EncryptedEnvelope
import app.logdate.client.device.crypto.IdentityKeyNotFoundException
import kotlinx.coroutines.CancellationException
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
        return try {
            val envelope = json.decodeFromString<EncryptedEnvelope>(payload)
            contentEncryptionService.decryptContent(fieldId, envelope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IdentityKeyNotFoundException) {
            // No key at all is a setup problem for every record, not something one record can be
            // set aside for.
            throw e
        } catch (e: Exception) {
            throw UnreadablePayloadException(fieldId, e)
        }
    }
}

/**
 * A synced value this device cannot read: it was encrypted with a different key (the usual case,
 * after a restore or reinstall replaced this device's key) or it is damaged. The two look the same
 * to AES-GCM, and either way the record can only be set aside, not applied.
 */
class UnreadablePayloadException(
    val fieldId: String,
    cause: Throwable,
) : Exception("Cannot read synced value $fieldId", cause)
