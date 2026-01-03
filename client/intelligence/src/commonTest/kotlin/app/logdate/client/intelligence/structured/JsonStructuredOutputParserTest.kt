package app.logdate.client.intelligence.structured

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class JsonStructuredOutputParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parse_withValidJson_returnsSuccess() {
        val parser = JsonStructuredOutputParser(json, SamplePayload.serializer())
        val result = parser.parse("""{"name":"Alex","count":2}""")

        assertTrue(result is StructuredOutputResult.Success)
    }

    @Test
    fun parse_withEmptyString_returnsEmpty() {
        val parser = JsonStructuredOutputParser(json, SamplePayload.serializer())
        val result = parser.parse("   ")

        assertTrue(result is StructuredOutputResult.Empty)
    }

    @Test
    fun parse_withInvalidJson_returnsInvalid() {
        val parser = JsonStructuredOutputParser(json, SamplePayload.serializer())
        val result = parser.parse("not-json")

        assertTrue(result is StructuredOutputResult.Invalid)
    }

    @Test
    fun parse_withEmbeddedJson_andAllowEmbedded_parsesPayload() {
        val parser = JsonStructuredOutputParser(json, SamplePayload.serializer(), allowEmbeddedJson = true)
        val result = parser.parse("prefix {\"name\":\"Jordan\",\"count\":3} suffix")

        assertTrue(result is StructuredOutputResult.Success)
    }

    @Serializable
    private data class SamplePayload(
        val name: String,
        val count: Int
    )
}
