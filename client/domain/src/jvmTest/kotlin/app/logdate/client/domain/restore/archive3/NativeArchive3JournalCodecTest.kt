package app.logdate.client.domain.restore.archive3

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NativeArchive3JournalCodecTest {
    private val fixture = requireNotNull(javaClass.getResource("/native-archive3/swift-journals.json")).readText()
    private val source = Json.parseToJsonElement(fixture)
    private val first =
        source.jsonObject
            .getValue("journals")
            .jsonArray
            .first()
            .jsonObject

    @Test
    fun `actual Swift journals round trip every typed value`() {
        val decoded = NativeArchive3JournalCodec.decode(fixture)
        assertEquals(7, decoded.size)
        assertEquals(source, Json.parseToJsonElement(NativeArchive3JournalCodec.encode(decoded)))
        assertEquals(Long.MIN_VALUE, decoded.single { it.nativeCreatedAtMilliseconds == Long.MIN_VALUE }.nativeCreatedAtMilliseconds)
        assertEquals(Long.MAX_VALUE, decoded.single { it.nativeCreatedAtMilliseconds == Long.MAX_VALUE }.nativeCreatedAtMilliseconds)
    }

    @Test
    fun `unknown source metadata stays distinct from known empty false and epoch zero`() {
        val decoded = NativeArchive3JournalCodec.decode(fixture)
        val unknown = decoded.single { it.title == "Unknown legacy metadata" }
        val known = decoded.single { it.title == "Known empty and epoch zero" }
        assertNull(unknown.description)
        assertNull(unknown.favorite)
        assertNull(unknown.createdAt)
        assertEquals("", known.description)
        assertEquals(false, known.favorite)
        assertEquals("1970-01-01T00:00:00.000000000Z", known.createdAt)
        assertEquals(0L, unknown.nativeCreatedAtMilliseconds)
        assertEquals(0L, known.nativeCreatedAtMilliseconds)
    }

    @Test
    fun `optional native creation can be absent but not explicitly null`() {
        val absent = JsonObject(first - "nativeCreatedAtMilliseconds")
        val decoded = NativeArchive3JournalCodec.decode(document(absent))
        assertNull(decoded.single().nativeCreatedAtMilliseconds)
        val encoded = Json.parseToJsonElement(NativeArchive3JournalCodec.encode(decoded))
        assertFalse(
            encoded.jsonObject
                .getValue("journals")
                .jsonArray
                .single()
                .jsonObject
                .containsKey("nativeCreatedAtMilliseconds"),
        )
        assertFailsWith<IllegalArgumentException> {
            NativeArchive3JournalCodec.decode(
                document(changed("nativeCreatedAtMilliseconds", JsonNull)),
            )
        }
    }

    @Test
    fun `required nullable source fields cannot disappear`() {
        for (field in listOf("description", "favorite", "createdAt", "updatedAt")) {
            assertFailsWith<IllegalArgumentException>(field) { NativeArchive3JournalCodec.decode(document(JsonObject(first - field))) }
        }
    }

    @Test
    fun `native creation refuses narrowing overflow strings and fractional values`() {
        for (value in listOf(JsonPrimitive("1"), JsonPrimitive(1.5), Json.parseToJsonElement("9223372036854775808"))) {
            assertFailsWith<IllegalArgumentException> {
                NativeArchive3JournalCodec.decode(
                    document(changed("nativeCreatedAtMilliseconds", value)),
                )
            }
        }
    }

    @Test
    fun `source timestamp retains its exact offset and nanoseconds`() {
        val raw = "2026-09-18T03:30:05.123456789-07:00"
        val input = document(changed("createdAt", JsonPrimitive(raw)))
        val decoded = NativeArchive3JournalCodec.decode(input)
        assertEquals(raw, decoded.single().createdAt)
        assertEquals(Json.parseToJsonElement(input), Json.parseToJsonElement(NativeArchive3JournalCodec.encode(decoded)))
    }

    @Test
    fun `invalid Gregorian dates unknown offsets and leap seconds are refused`() {
        for (raw in listOf(
            "2025-02-29T01:00:00Z",
            "2026-01-01T24:00:00Z",
            "2026-01-01T00:00:60Z",
            "2026-01-01T00:00:00-00:00",
            "2026-01-01T00:00:00+24:00",
        )) {
            assertFailsWith<IllegalArgumentException>(
                raw,
            ) { NativeArchive3JournalCodec.decode(document(changed("createdAt", JsonPrimitive(raw)))) }
        }
    }

    @Test
    fun `known RFC3339 maximum offsets remain representable without normalization`() {
        val raw = "0000-02-29T00:00:00.000000001+23:59"
        assertEquals(raw, NativeArchive3JournalCodec.decode(document(changed("createdAt", JsonPrimitive(raw)))).single().createdAt)
    }

    @Test
    fun `unknown fields duplicate keys and duplicate identities are refused`() {
        assertFailsWith<IllegalArgumentException> { NativeArchive3JournalCodec.decode(document(changed("future", JsonPrimitive(true)))) }
        assertFailsWith<IllegalArgumentException> { NativeArchive3JournalCodec.decode("""{"journals":[],"journals":[]}""") }
        assertFailsWith<IllegalArgumentException> { NativeArchive3JournalCodec.decode(document(first, first)) }
        assertFailsWith<IllegalArgumentException> { NativeArchive3JournalCodec.decode("""{"journals":[],"future":true}""") }
    }

    @Test
    fun `canonical identity and field types are checked before exposing records`() {
        for ((field, value) in listOf(
            "id" to JsonPrimitive("16EB2C36-BC80-4B3F-950D-BB2F10A6913E"),
            "title" to JsonNull,
            "favorite" to JsonPrimitive("false"),
            "description" to JsonPrimitive(1),
        )) {
            assertFailsWith<IllegalArgumentException> { NativeArchive3JournalCodec.decode(document(changed(field, value))) }
        }
    }

    private fun changed(
        field: String,
        value: JsonElement,
    ) = JsonObject(first + (field to value))

    private fun document(vararg journals: JsonObject) = JsonObject(mapOf("journals" to JsonArray(journals.toList()))).toString()
}
