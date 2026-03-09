package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Deterministic subset DAG-CBOR encoder and decoder for repo primitives.
 */
public object DagCborCodec {
    /**
     * Encodes [element] into deterministic DAG-CBOR bytes.
     */
    public fun encode(element: JsonElement): ByteArray = encodeJsonElement(element)

    /**
     * Decodes [bytes] produced by this codec back into a JSON element.
     */
    public fun decode(bytes: ByteArray): JsonElement = Decoder(bytes).decodeElement()

    private fun encodeJsonElement(element: JsonElement): ByteArray =
        when (element) {
            is JsonObject -> encodeMap(element)
            is JsonArray -> encodeArray(element)
            JsonNull -> byteArrayOf(NULL_SIMPLE_VALUE)
            is JsonPrimitive -> encodePrimitive(element)
        }

    private fun encodeMap(map: JsonObject): ByteArray {
        val entries =
            map.entries.sortedWith(compareBy<Map.Entry<String, JsonElement>>({ it.key.encodeToByteArray().size }, { it.key }))
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
            primitive.booleanOrNull != null -> byteArrayOf(if (primitive.booleanOrNull == true) TRUE_SIMPLE_VALUE else FALSE_SIMPLE_VALUE)
            primitive.longOrNull != null -> encodeLong(primitive.content.toLong())
            else -> error("Unsupported DAG-CBOR primitive: ${primitive.content}")
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
        require(value >= 0L) { "CBOR values must be non-negative" }
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
        val output = ByteArray(size)
        var cursor = 0
        forEach { bytes ->
            bytes.copyInto(output, destinationOffset = cursor)
            cursor += bytes.size
        }
        return output
    }

    private class Decoder(
        private val bytes: ByteArray,
    ) {
        private var cursor: Int = 0

        fun decodeElement(): JsonElement {
            val header = readByte().toInt() and 0xff
            val majorType = header shr MAJOR_TYPE_SHIFT
            val info = header and INFO_MASK
            return when (majorType) {
                MAJOR_TYPE_UNSIGNED_INT -> JsonPrimitive(readLength(info))
                MAJOR_TYPE_NEGATIVE_INT -> JsonPrimitive(-(readLength(info) + 1))
                MAJOR_TYPE_TEXT -> JsonPrimitive(readText(readLength(info).toInt()))
                MAJOR_TYPE_ARRAY -> {
                    val size = readLength(info).toInt()
                    JsonArray(List(size) { decodeElement() })
                }

                MAJOR_TYPE_MAP -> {
                    val size = readLength(info).toInt()
                    val entries =
                        buildMap(size) {
                            repeat(size) {
                                val key = decodeElement() as JsonPrimitive
                                put(key.content, decodeElement())
                            }
                        }
                    JsonObject(entries)
                }

                MAJOR_TYPE_SIMPLE ->
                    when (header.toByte()) {
                        FALSE_SIMPLE_VALUE -> JsonPrimitive(false)
                        TRUE_SIMPLE_VALUE -> JsonPrimitive(true)
                        NULL_SIMPLE_VALUE -> JsonNull
                        else -> error("Unsupported simple value 0x${header.toString(16)}")
                    }

                else -> error("Unsupported DAG-CBOR major type $majorType")
            }
        }

        private fun readLength(info: Int): Long =
            when (info) {
                in 0 until SMALL_VALUE_THRESHOLD.toInt() -> info.toLong()
                ONE_BYTE_INFO -> readUnsigned(1)
                TWO_BYTE_INFO -> readUnsigned(2)
                FOUR_BYTE_INFO -> readUnsigned(4)
                EIGHT_BYTE_INFO -> readUnsigned(8)
                else -> error("Unsupported CBOR length info $info")
            }

        private fun readUnsigned(width: Int): Long {
            var value = 0L
            repeat(width) {
                value = (value shl BITS_PER_BYTE) or ((readByte().toInt() and 0xff).toLong())
            }
            return value
        }

        private fun readText(length: Int): String {
            val end = cursor + length
            require(end <= bytes.size) { "Unexpected end of DAG-CBOR input" }
            val result = bytes.copyOfRange(cursor, end).decodeToString()
            cursor = end
            return result
        }

        private fun readByte(): Byte {
            require(cursor < bytes.size) { "Unexpected end of DAG-CBOR input" }
            return bytes[cursor++]
        }
    }
}

private const val MAJOR_TYPE_SHIFT: Int = 5
private const val MAJOR_TYPE_UNSIGNED_INT: Int = 0
private const val MAJOR_TYPE_NEGATIVE_INT: Int = 1
private const val MAJOR_TYPE_TEXT: Int = 3
private const val MAJOR_TYPE_ARRAY: Int = 4
private const val MAJOR_TYPE_MAP: Int = 5
private const val MAJOR_TYPE_SIMPLE: Int = 7
private const val INFO_MASK: Int = 0x1f
private const val SMALL_VALUE_THRESHOLD: Long = 24L
private const val ONE_BYTE_INFO: Int = 24
private const val TWO_BYTE_INFO: Int = 25
private const val FOUR_BYTE_INFO: Int = 26
private const val EIGHT_BYTE_INFO: Int = 27
private const val TRUE_SIMPLE_VALUE: Byte = 0xf5.toByte()
private const val FALSE_SIMPLE_VALUE: Byte = 0xf4.toByte()
private const val NULL_SIMPLE_VALUE: Byte = 0xf6.toByte()
private const val BITS_PER_BYTE: Int = 8
private const val BYTE_MASK: Long = 0xff
private const val UBYTE_MAX: Long = 0xff
private const val USHORT_MAX: Long = 0xffff
private const val UINT_MAX: Long = 0xffff_ffff
