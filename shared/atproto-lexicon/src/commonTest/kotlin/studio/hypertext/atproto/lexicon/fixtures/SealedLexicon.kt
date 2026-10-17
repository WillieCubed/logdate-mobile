@file:OptIn(ExperimentalSerializationApi::class)

package studio.hypertext.atproto.lexicon.fixtures

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import studio.hypertext.atproto.lexicon.LexiconRecordKey
import studio.hypertext.atproto.lexicon.data.Bytes

public object SealedLexicon {
    public const val ID: String = "app.logdate.crypto.sealed"
}

/**
 * An encrypted record. Every record in a personal space is stored this way, so the server can't tell what kind of
 * record it is, when it was written, or how big it is.
 */
@Serializable
public data class Sealed(
    /** The encrypted #payload, padded to a fixed size bucket. */
    val ciphertext: Bytes,
    /** Which of the owner's content keys encrypted it. */
    val keyId: String,
    val nonce: Bytes,
    /** Envelope format version. */
    val version: Long,
    @SerialName("\$type")
    @EncodeDefault
    val lexiconType: String = TYPE,
) {
    public companion object {
        public const val TYPE: String = "app.logdate.crypto.sealed"
        public val KEY: LexiconRecordKey = LexiconRecordKey.Any
    }
}

/** What a sealed record contains once decrypted. */
@Serializable
public data class SealedPayload(
    /** The inner record's type. */
    val collection: String,
    /** The inner record, such as an app.logdate.journal.entry. */
    val record: JsonElement,
    /** The inner record's key. The sealed record's own key is random and unrelated. */
    val rkey: String,
)
