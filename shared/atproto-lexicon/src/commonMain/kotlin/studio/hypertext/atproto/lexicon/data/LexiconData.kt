package studio.hypertext.atproto.lexicon.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.DagCborCodec
import kotlin.io.encoding.Base64

/**
 * A link to content by its CID, written in JSON as `{"$link": "<cid>"}` and in DAG-CBOR as a
 * native link.
 */
@Serializable(with = CidLinkSerializer::class)
public data class CidLink(
    val cid: Cid,
)

/**
 * Raw bytes, written in JSON as `{"$bytes": "<base64>"}` and in DAG-CBOR as a native byte
 * string. Two values are equal when their bytes are.
 */
@Serializable(with = BytesSerializer::class)
public class Bytes(
    bytes: ByteArray,
) {
    private val bytes: ByteArray = bytes.copyOf()

    /** A copy of the bytes. */
    public fun toByteArray(): ByteArray = bytes.copyOf()

    /** The number of bytes. */
    public val size: Int
        get() = bytes.size

    override fun equals(other: Any?): Boolean = other is Bytes && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "Bytes(size=$size)"
}

/**
 * A reference to an uploaded file: its content [ref], the file's [mimeType] and its [size] in
 * bytes. In private LogDate spaces the referenced content is ciphertext, and [mimeType] still
 * describes the decrypted file.
 */
@Serializable(with = BlobSerializer::class)
public data class Blob(
    val ref: CidLink,
    val mimeType: String,
    val size: Long,
)

/**
 * Encodes generated models to and from DAG-CBOR, the AT Protocol's binary record encoding, by way
 * of their JSON form.
 */
public object LexiconData {
    /**
     * The JSON configuration generated models use. Unknown fields from newer schema versions are
     * ignored. A field that's required but nullable is always written, as null when it's null,
     * while optional fields left at their null default are omitted.
     */
    public val json: Json = Json { ignoreUnknownKeys = true }

    /** Encodes [value] as deterministic DAG-CBOR bytes. */
    public fun <T> encodeToDagCbor(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray = DagCborCodec.encode(json.encodeToJsonElement(serializer, value))

    /** Decodes a value previously encoded with [encodeToDagCbor]. */
    public fun <T> decodeFromDagCbor(
        serializer: KSerializer<T>,
        bytes: ByteArray,
    ): T = json.decodeFromJsonElement(serializer, DagCborCodec.decode(bytes))
}

internal object CidLinkSerializer : KSerializer<CidLink> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("studio.hypertext.atproto.lexicon.data.CidLink")

    override fun serialize(
        encoder: Encoder,
        value: CidLink,
    ) {
        encoder.json().encodeJsonElement(value.toJson())
    }

    override fun deserialize(decoder: Decoder): CidLink = decoder.json().decodeJsonElement().toCidLink()
}

internal object BytesSerializer : KSerializer<Bytes> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("studio.hypertext.atproto.lexicon.data.Bytes")

    override fun serialize(
        encoder: Encoder,
        value: Bytes,
    ) {
        encoder.json().encodeJsonElement(buildJsonObject { put(BYTES_KEY, bytesBase64.encode(value.toByteArray())) })
    }

    override fun deserialize(decoder: Decoder): Bytes {
        val encoded =
            decoder.json().decodeJsonElement().singleString(BYTES_KEY)
                ?: throw SerializationException("Bytes must be an object with a single \$bytes string")
        return try {
            Bytes(bytesBase64.decode(encoded))
        } catch (error: IllegalArgumentException) {
            throw SerializationException("Bytes must be standard base64: ${error.message}")
        }
    }
}

internal object BlobSerializer : KSerializer<Blob> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("studio.hypertext.atproto.lexicon.data.Blob")

    override fun serialize(
        encoder: Encoder,
        value: Blob,
    ) {
        encoder.json().encodeJsonElement(
            buildJsonObject {
                put(TYPE_KEY, BLOB_TYPE)
                put("ref", value.ref.toJson())
                put("mimeType", value.mimeType)
                put("size", value.size)
            },
        )
    }

    override fun deserialize(decoder: Decoder): Blob {
        val source =
            decoder.json().decodeJsonElement() as? JsonObject
                ?: throw SerializationException("A blob must be an object")
        val type = source.string(TYPE_KEY)
        if (type == null && "cid" in source && "ref" !in source) {
            throw SerializationException("Legacy blobs (cid and mimeType without a size) aren't supported")
        }
        if (type != BLOB_TYPE) throw SerializationException("A blob's \$type must be \"$BLOB_TYPE\", not \"$type\"")
        val ref = source["ref"]?.toCidLink() ?: throw SerializationException("A blob must have a ref")
        val mimeType =
            source.string("mimeType")?.takeIf { it.isNotEmpty() }
                ?: throw SerializationException("A blob must have a mimeType")
        val size = (source["size"] as? JsonPrimitive)?.longOrNull ?: throw SerializationException("A blob must have a size")
        if (size < 0) throw SerializationException("A blob's size must not be negative")
        return Blob(ref = ref, mimeType = mimeType, size = size)
    }
}

private fun Encoder.json(): JsonEncoder =
    this as? JsonEncoder ?: throw SerializationException("Lexicon data types encode only through JSON; use LexiconData for DAG-CBOR")

private fun Decoder.json(): JsonDecoder =
    this as? JsonDecoder ?: throw SerializationException("Lexicon data types decode only through JSON; use LexiconData for DAG-CBOR")

private fun CidLink.toJson(): JsonObject = buildJsonObject { put(LINK_KEY, cid.toString()) }

private fun JsonElement.toCidLink(): CidLink {
    val value = singleString(LINK_KEY) ?: throw SerializationException("A content link must be an object with a single \$link string")
    return CidLink(Cid.require(value))
}

private fun JsonElement.singleString(key: String): String? = (this as? JsonObject)?.takeIf { it.size == 1 }?.string(key)

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private const val LINK_KEY = "\$link"
private const val BYTES_KEY = "\$bytes"
private const val TYPE_KEY = "\$type"
private const val BLOB_TYPE = "blob"

// The data model encodes bytes with the standard base64 alphabet; padding is optional on read.
private val bytesBase64 = Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
