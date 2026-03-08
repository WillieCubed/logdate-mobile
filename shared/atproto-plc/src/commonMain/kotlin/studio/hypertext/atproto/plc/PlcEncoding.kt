package studio.hypertext.atproto.plc

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import okio.ByteString.Companion.toByteString
import studio.hypertext.atproto.identity.AtprotoDid

/**
 * Deterministic PLC encoding helpers for signing and DID derivation.
 */
public object PlcEncoding {
    private val json: Json =
        Json {
            explicitNulls = true
            encodeDefaults = true
        }

    /**
     * Encodes [operation] as canonical DAG-CBOR bytes, excluding [PlcOperation.sig].
     */
    public fun encodeUnsigned(operation: PlcUnsignedOperation): ByteArray = encodeCanonicalJson(json.encodeToString(operation))

    /**
     * Encodes [operation] as canonical DAG-CBOR bytes, including [PlcOperation.sig].
     */
    public fun encodeSigned(operation: PlcOperation): ByteArray {
        require(operation.isSigned) { "PLC operation must be signed before signed encoding" }
        return encodeCanonicalJson(json.encodeToString(operation))
    }

    /**
     * Derives the PLC DID represented by a signed genesis [operation].
     */
    public fun deriveDid(operation: PlcOperation): AtprotoDid {
        require(operation.isGenesis) { "Only signed PLC genesis operations can derive a DID" }
        require(operation.isSigned) { "Only signed PLC genesis operations can derive a DID" }
        val digest = encodeSigned(operation).toByteString().sha256().toByteArray()
        val suffix = encodeBase32(digest).take(PLC_SUFFIX_LENGTH)
        return AtprotoDid.require("did:plc:$suffix")
    }

    private fun encodeCanonicalJson(encoded: String): ByteArray = encodeJsonElement(json.parseToJsonElement(encoded))

    private fun encodeJsonElement(element: JsonElement): ByteArray =
        when (element) {
            is JsonObject -> encodeMap(element)
            is JsonArray -> encodeArray(element)
            JsonNull -> byteArrayOf(NULL_SIMPLE_VALUE)
            is JsonPrimitive -> encodePrimitive(element)
        }

    private fun encodeMap(map: JsonObject): ByteArray {
        val entries =
            map.entries
                .sortedWith(compareBy<Map.Entry<String, JsonElement>>({ it.key.encodeToByteArray().size }, { it.key }))

        val encodedEntries =
            buildList(entries.size * 2) {
                entries.forEach { (key, value) ->
                    add(encodeText(key))
                    add(encodeJsonElement(value))
                }
            }
        return encodeHead(MAJOR_TYPE_MAP, entries.size.toLong()) + encodedEntries.flatten()
    }

    private fun encodeArray(array: JsonArray): ByteArray =
        encodeHead(MAJOR_TYPE_ARRAY, array.size.toLong()) + array.map(::encodeJsonElement).flatten()

    private fun encodePrimitive(primitive: JsonPrimitive): ByteArray =
        when {
            primitive.isString -> encodeText(primitive.content)
            primitive.booleanOrNull != null -> byteArrayOf(if (primitive.content == "true") TRUE_SIMPLE_VALUE else FALSE_SIMPLE_VALUE)
            primitive.longOrNull != null -> encodeLong(primitive.content.toLong())
            else -> error("Unsupported PLC primitive: ${primitive.content}")
        }

    private fun encodeText(value: String): ByteArray {
        val bytes = value.encodeToByteArray()
        return encodeHead(MAJOR_TYPE_TEXT, bytes.size.toLong()) + bytes
    }

    private fun encodeLong(value: Long): ByteArray =
        when {
            value >= 0L -> encodeHead(MAJOR_TYPE_UNSIGNED_INT, value)
            else -> encodeHead(MAJOR_TYPE_NEGATIVE_INT, -(value + 1))
        }

    private fun encodeHead(
        majorType: Int,
        value: Long,
    ): ByteArray {
        require(value >= 0L) { "CBOR lengths must be non-negative" }
        return when {
            value < SMALL_VALUE_THRESHOLD -> byteArrayOf(((majorType shl MAJOR_TYPE_SHIFT) or value.toInt()).toByte())
            value <= UBYTE_MAX -> byteArrayOf(((majorType shl MAJOR_TYPE_SHIFT) or ONE_BYTE_INFO).toByte(), value.toByte())
            value <= USHORT_MAX -> byteArrayOf(((majorType shl MAJOR_TYPE_SHIFT) or TWO_BYTE_INFO).toByte()) + encodeBigEndian(value, 2)
            value <= UINT_MAX -> byteArrayOf(((majorType shl MAJOR_TYPE_SHIFT) or FOUR_BYTE_INFO).toByte()) + encodeBigEndian(value, 4)
            else -> byteArrayOf(((majorType shl MAJOR_TYPE_SHIFT) or EIGHT_BYTE_INFO).toByte()) + encodeBigEndian(value, 8)
        }
    }

    private fun encodeBigEndian(
        value: Long,
        width: Int,
    ): ByteArray =
        ByteArray(width) { index ->
            val shift = (width - index - 1) * BITS_PER_BYTE
            ((value shr shift) and BYTE_MASK).toByte()
        }

    private fun List<ByteArray>.flatten(): ByteArray {
        val size = sumOf(ByteArray::size)
        val buffer = ByteArray(size)
        var cursor = 0
        forEach { bytes ->
            bytes.copyInto(buffer, destinationOffset = cursor)
            cursor += bytes.size
        }
        return buffer
    }

    private fun encodeBase32(bytes: ByteArray): String {
        if (bytes.isEmpty()) {
            return ""
        }
        val output = StringBuilder(((bytes.size * 8) + BASE32_BITS - 1) / BASE32_BITS)
        var buffer = 0
        var bitsInBuffer = 0
        bytes.forEach { byte ->
            buffer = (buffer shl BITS_PER_BYTE) or (byte.toInt() and BYTE_MASK.toInt())
            bitsInBuffer += BITS_PER_BYTE
            while (bitsInBuffer >= BASE32_BITS) {
                bitsInBuffer -= BASE32_BITS
                output.append(BASE32_ALPHABET[(buffer shr bitsInBuffer) and BASE32_MASK])
            }
        }
        if (bitsInBuffer > 0) {
            output.append(BASE32_ALPHABET[(buffer shl (BASE32_BITS - bitsInBuffer)) and BASE32_MASK])
        }
        return output.toString()
    }

    private const val PLC_SUFFIX_LENGTH: Int = 24
    private const val MAJOR_TYPE_SHIFT: Int = 5
    private const val MAJOR_TYPE_UNSIGNED_INT: Int = 0
    private const val MAJOR_TYPE_NEGATIVE_INT: Int = 1
    private const val MAJOR_TYPE_TEXT: Int = 3
    private const val MAJOR_TYPE_ARRAY: Int = 4
    private const val MAJOR_TYPE_MAP: Int = 5
    private const val SMALL_VALUE_THRESHOLD: Long = 24L
    private const val ONE_BYTE_INFO: Int = 24
    private const val TWO_BYTE_INFO: Int = 25
    private const val FOUR_BYTE_INFO: Int = 26
    private const val EIGHT_BYTE_INFO: Int = 27
    private const val TRUE_SIMPLE_VALUE: Byte = 0xf5.toByte()
    private const val FALSE_SIMPLE_VALUE: Byte = 0xf4.toByte()
    private const val NULL_SIMPLE_VALUE: Byte = 0xf6.toByte()
    private const val BITS_PER_BYTE: Int = 8
    private const val BASE32_BITS: Int = 5
    private const val BASE32_MASK: Int = 0x1f
    private const val BYTE_MASK: Long = 0xff
    private const val UBYTE_MAX: Long = 0xff
    private const val USHORT_MAX: Long = 0xffff
    private const val UINT_MAX: Long = 0xffff_ffff
    private const val BASE32_ALPHABET: String = "abcdefghijklmnopqrstuvwxyz234567"
}
