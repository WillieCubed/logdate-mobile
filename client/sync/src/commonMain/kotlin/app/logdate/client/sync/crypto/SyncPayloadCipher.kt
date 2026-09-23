package app.logdate.client.sync.crypto

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.EncryptedEnvelope
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.IdentityKeyNotFoundException
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val SYNC_PAYLOAD_PREFIX_V1 = "LDSE1:"
private const val SYNC_PAYLOAD_PREFIX_V2 = "LDSE2:"

/**
 * A record encrypted under one key and a record simply damaged in transit look identical to
 * AES-GCM: both just fail to decrypt. [FingerprintedPayload.keyFingerprint] records which
 * identity key produced the ciphertext, so a mismatch against the current key is diagnosed as
 * "this was made with a different key" up front, before decryption is even attempted, rather
 * than inferred after the fact from a generic decrypt failure.
 */
@Serializable
private data class FingerprintedPayload(
    @SerialName("fp")
    val keyFingerprint: String,
    @SerialName("env")
    val envelope: EncryptedEnvelope,
)

@OptIn(ExperimentalEncodingApi::class)
class SyncPayloadCipher(
    private val contentEncryptionService: ContentEncryptionService,
    private val identityKeyManager: IdentityKeyManager,
    private val cryptoManager: CryptoManager,
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
        if (plaintext.startsWith(SYNC_PAYLOAD_PREFIX_V1) || plaintext.startsWith(SYNC_PAYLOAD_PREFIX_V2)) return plaintext
        val envelope = contentEncryptionService.encryptContent(fieldId, plaintext)
        val fingerprinted = FingerprintedPayload(keyFingerprint = currentKeyFingerprint(), envelope = envelope)
        return SYNC_PAYLOAD_PREFIX_V2 + json.encodeToString(fingerprinted)
    }

    suspend fun decryptString(
        fieldId: String,
        value: String,
    ): String =
        when {
            value.startsWith(SYNC_PAYLOAD_PREFIX_V2) -> decryptV2(fieldId, value.removePrefix(SYNC_PAYLOAD_PREFIX_V2))
            value.startsWith(SYNC_PAYLOAD_PREFIX_V1) -> decryptV1(fieldId, value.removePrefix(SYNC_PAYLOAD_PREFIX_V1))
            else -> value
        }

    private suspend fun decryptV2(
        fieldId: String,
        payload: String,
    ): String =
        try {
            val fingerprinted = json.decodeFromString<FingerprintedPayload>(payload)
            val currentFingerprint = currentKeyFingerprint()
            if (currentFingerprint != fingerprinted.keyFingerprint) {
                throw WrongKeyPayloadException(fieldId)
            }
            contentEncryptionService.decryptContent(fieldId, fingerprinted.envelope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IdentityKeyNotFoundException) {
            throw e
        } catch (e: WrongKeyPayloadException) {
            Napier.w("Setting aside synced value $fieldId: it was made with a different identity key")
            throw UnreadablePayloadException(fieldId, e)
        } catch (e: Exception) {
            throw UnreadablePayloadException(fieldId, e)
        }

    private suspend fun decryptV1(
        fieldId: String,
        payload: String,
    ): String =
        try {
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

    /**
     * A short, non-reversible marker for the current identity key: not the key itself (this
     * value travels to the server inside every synced field), just enough to tell two keys apart.
     */
    private suspend fun currentKeyFingerprint(): String {
        val mac = cryptoManager.hmacSha256(identityKeyManager.getIdentityKey(), FINGERPRINT_CONTEXT)
        return Base64.encode(mac.copyOf(FINGERPRINT_BYTES))
    }

    private companion object {
        val FINGERPRINT_CONTEXT = "sync-payload-fingerprint".encodeToByteArray()
        const val FINGERPRINT_BYTES = 8
    }
}

/** A synced value made with an identity key this device no longer has. */
class WrongKeyPayloadException(
    val fieldId: String,
) : Exception("Synced value $fieldId was encrypted with a different identity key")

/**
 * A synced value this device cannot read: it was encrypted with a different key (the usual case,
 * after a restore or reinstall replaced this device's key) or it is damaged. The two look the same
 * to AES-GCM, and either way the record can only be set aside, not applied.
 */
class UnreadablePayloadException(
    val fieldId: String,
    cause: Throwable,
) : Exception("Cannot read synced value $fieldId", cause)
