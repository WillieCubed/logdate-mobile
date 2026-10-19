package app.logdate.server.space

import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.space.SealedPadding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

private const val NONCE_BYTES = 12
private const val TAG_BYTES = 16
private const val MAX_KEY_ID = 128

/**
 * Returns null when [record] is a well-formed `app.logdate.crypto.sealed` envelope, or the reason it
 * isn't. A space stores nothing else, so the server never holds a readable record in one.
 */
internal fun checkSealedRecord(record: JsonObject): String? {
    val sealed =
        try {
            strictJson.decodeFromJsonElement<Sealed>(record)
        } catch (_: IllegalArgumentException) {
            // SerializationException, and malformed base64 in a bytes field, are both IllegalArgumentExceptions.
            return "Not an app.logdate.crypto.sealed record"
        }
    // The generated class defaults its type, so an untyped record decodes; check what was sent.
    if ((record["\$type"] as? JsonPrimitive)?.contentOrNull != Sealed.TYPE || sealed.lexiconType != Sealed.TYPE) {
        return "Only app.logdate.crypto.sealed records can be stored in a space"
    }
    if (sealed.version != 1L) return "Envelope version ${sealed.version} isn't supported"
    if (sealed.keyId.isEmpty() || sealed.keyId.length > MAX_KEY_ID) return "keyId must be 1 to $MAX_KEY_ID characters"
    if (sealed.nonce.size != NONCE_BYTES) return "nonce must be $NONCE_BYTES bytes"
    if (!SealedPadding.isPaddedSize(sealed.ciphertext.size - TAG_BYTES)) return "ciphertext isn't padded to a size bucket"
    return null
}

private val strictJson = Json { ignoreUnknownKeys = false }
