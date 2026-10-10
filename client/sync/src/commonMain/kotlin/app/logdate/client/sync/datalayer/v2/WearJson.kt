package app.logdate.client.sync.datalayer.v2

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** The outcome of reading a version 2 message. */
sealed interface Decoded<out T> {
    data class Supported<T>(
        val value: T,
    ) : Decoded<T>

    /**
     * Written by a newer version this one cannot read. The reader leaves what it already has
     * unchanged, logs that it skipped the message, and answers with `REJECTED_UNSUPPORTED` where the
     * message expects an answer.
     */
    data class Unsupported(
        val schema: Int,
        val reason: String,
    ) : Decoded<Nothing>

    /** Not a message of a schema this version knows. [reason] never repeats the message's contents. */
    data class Malformed(
        val reason: String,
    ) : Decoded<Nothing>
}

/**
 * The JSON every version 2 message uses.
 *
 * Unknown keys are ignored so a newer device can add fields within a schema; a newer schema is
 * reported as [Decoded.Unsupported] before anything else is read.
 */
object WearJson {
    private const val SCHEMA_KEY = "schema"
    private const val DEFAULT_SCHEMA = 1

    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    fun <T> encode(
        serializer: KSerializer<T>,
        value: T,
    ): String = json.encodeToString(serializer, value)

    fun <T> encodeToBytes(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray = encode(serializer, value).encodeToByteArray()

    fun <T> decode(
        bytes: ByteArray,
        supportedSchema: Int,
        serializer: KSerializer<T>,
    ): Decoded<T> = decode(bytes.decodeToString(), supportedSchema, serializer)

    fun <T> decode(
        text: String,
        supportedSchema: Int,
        serializer: KSerializer<T>,
    ): Decoded<T> {
        val name = serializer.descriptor.serialName
        val element =
            try {
                json.parseToJsonElement(text)
            } catch (_: IllegalArgumentException) {
                return Decoded.Malformed("$name is not JSON")
            }
        val document = element as? JsonObject ?: return Decoded.Malformed("$name is not a JSON object")
        val schemaElement = document[SCHEMA_KEY]
        val schema =
            if (schemaElement == null) {
                DEFAULT_SCHEMA
            } else {
                (schemaElement as? JsonPrimitive)?.intOrNull ?: return Decoded.Malformed("$name has a schema that is not a number")
            }
        if (schema > supportedSchema) {
            return Decoded.Unsupported(schema, "$name schema $schema is newer than $supportedSchema")
        }
        return try {
            Decoded.Supported(json.decodeFromJsonElement(serializer, document))
        } catch (_: IllegalArgumentException) {
            Decoded.Malformed("$name does not match schema $schema")
        }
    }
}
