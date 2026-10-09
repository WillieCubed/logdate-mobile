package app.logdate.ui.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParityFixtureTest {
    @Test
    fun `shared cross platform fixtures preserve source structure and semantic spans`() {
        val resource = checkNotNull(javaClass.getResourceAsStream("/markdown-parity.json"))
        val fixtures =
            resource
                .bufferedReader()
                .use { Json.parseToJsonElement(it.readText()).jsonObject }
                .getValue("fixtures")
                .jsonArray

        fixtures.forEach { fixture ->
            val expected = fixture.jsonObject
            val id = expected.getValue("id").jsonPrimitive.content
            val source = expected.getValue("source").jsonPrimitive.content
            val document = parseMarkdownDocument(source)

            assertEquals(source, document.source, id)
            assertEquals(expected.getValue("plainText").jsonPrimitive.content, document.plainText, id)
            assertEquals(
                expected.getValue("blockKinds").jsonArray.map { it.jsonPrimitive.content },
                document.blocks.map { it.kind.name },
                id,
            )
            expected.getValue("spans").jsonArray.forEach { span ->
                val values = span.jsonObject
                assertTrue(
                    document.spans.any {
                        it.kind.name == values.getValue("kind").jsonPrimitive.content &&
                            it.sourceStart == values.getValue("sourceStart").jsonPrimitive.int &&
                            it.sourceEnd == values.getValue("sourceEnd").jsonPrimitive.int &&
                            it.textStart == values.getValue("textStart").jsonPrimitive.int &&
                            it.textEnd == values.getValue("textEnd").jsonPrimitive.int
                    },
                    "$id: missing required span $span; got ${document.spans}",
                )
            }
        }
    }
}
