package studio.hypertext.atproto.lexicon.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Support for the serializers the code generator writes for Lexicon unions.
 *
 * A union member is a JSON object with a `$type` naming its schema. Generated serializers use
 * this to read that type, decode or encode the member, and keep members of open unions they don't
 * recognize.
 */
public object LexiconUnions {
    /** A descriptor for the union named [name], such as `app.logdate.journal.entry#main.blocks`. */
    public fun descriptor(name: String): SerialDescriptor = buildClassSerialDescriptor(name)

    /** Reads the next union member from [decoder]. */
    public fun decodeMember(decoder: Decoder): LexiconUnionMember {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("Lexicon unions decode only through JSON; use LexiconData for DAG-CBOR")
        val source =
            jsonDecoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("A union member must be an object")
        val type =
            (source[TYPE_KEY] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                ?: throw SerializationException("A union member must have a \$type")
        return LexiconUnionMember(type = type, value = JsonObject(source - TYPE_KEY), json = jsonDecoder.json)
    }

    /** Writes [value], a member of type [type], with its `$type`. */
    public fun <T> encodeMember(
        encoder: Encoder,
        type: String,
        serializer: KSerializer<T>,
        value: T,
    ) {
        val jsonEncoder = encoder.json()
        val element =
            jsonEncoder.json.encodeToJsonElement(serializer, value) as? JsonObject
                ?: throw SerializationException("Union member $type must encode as an object")
        jsonEncoder.encodeJsonElement(element.withType(type))
    }

    /** Writes an open union's member that this app doesn't recognize, exactly as it was read. */
    public fun encodeUnknown(
        encoder: Encoder,
        type: String,
        value: JsonObject,
    ) {
        encoder.json().encodeJsonElement(value.withType(type))
    }

    /** The error for a member whose `$type` a closed union doesn't allow. */
    public fun notAllowed(
        union: String,
        type: String,
    ): SerializationException = SerializationException("\$type $type isn't allowed in closed union $union")

    private fun Encoder.json(): JsonEncoder =
        this as? JsonEncoder ?: throw SerializationException("Lexicon unions encode only through JSON; use LexiconData for DAG-CBOR")

    private fun JsonObject.withType(type: String): JsonObject = JsonObject(mapOf(TYPE_KEY to JsonPrimitive(type)) + (this - TYPE_KEY))
}

/**
 * A union member as read from JSON: its [type] and the rest of its fields, without `$type`.
 */
public class LexiconUnionMember internal constructor(
    public val type: String,
    public val value: JsonObject,
    private val json: Json,
) {
    /** Decodes this member's fields with [serializer]. */
    public fun <T> decode(serializer: KSerializer<T>): T = json.decodeFromJsonElement(serializer, value)
}
