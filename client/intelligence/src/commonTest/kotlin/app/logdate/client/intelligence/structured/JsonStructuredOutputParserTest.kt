package app.logdate.client.intelligence.structured

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies the robustness of the [JsonStructuredOutputParser] when extracting typed data from
 * AI model responses.
 *
 * These tests confirm that the parser correctly handles clean JSON, empty input, and malformed
 * strings. It also validates the "embedded JSON" feature, which allows the parser to
 * locate and extract a valid JSON block even when it is surrounded by unrelated conversational
 * text.
 */
class JsonStructuredOutputParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse with valid json returns success`() {
        val parser = JsonStructuredOutputParser(json, SamplePayload.serializer())
        val result = parser.parse("""{"name":"Alex","count":2}""")

        assertTrue(result is StructuredOutputResult.Success)
    }

    @Test
    fun `parse with empty string returns empty`() {
        val parser = JsonStructuredOutputParser(json, SamplePayload.serializer())
        val result = parser.parse("   ")

        assertTrue(result is StructuredOutputResult.Empty)
    }

    @Test
    fun `parse with invalid json returns invalid`() {
        val parser = JsonStructuredOutputParser(json, SamplePayload.serializer())
        val result = parser.parse("not-json")

        assertTrue(result is StructuredOutputResult.Invalid)
    }

    @Test
    fun `parse with embedded json and allow embedded parses payload`() {
        val parser = JsonStructuredOutputParser(json, SamplePayload.serializer(), allowEmbeddedJson = true)
        val result = parser.parse("prefix {\"name\":\"Jordan\",\"count\":3} suffix")

        assertTrue(result is StructuredOutputResult.Success)
    }

    @Serializable
    private data class SamplePayload(
        val name: String,
        val count: Int,
    )
}
