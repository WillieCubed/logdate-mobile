package studio.hypertext.atproto.lexicon

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The schema index puts each lexicon document's JSON into generated Kotlin, so an app can check
 * records against the schemas at runtime without reading files it may not be able to reach.
 */
class SchemaIndexCodegenTest {
    @Test
    fun `the schema index holds every document as a Kotlin string, sorted by NSID`() {
        val index = LexiconCodegen.generateSchemaIndex(listOf(ENTRY, TRICKY), packageName = "com.example.models", objectName = "Schemas")

        assertTrue(index.contains("package com.example.models\n"))
        assertTrue(index.contains("public object Schemas {"))
        assertTrue(index.contains("public val documents: List<String>"))
        assertEquals(listOf(minified(TRICKY), minified(ENTRY)), literals(index))
    }

    @Test
    fun `every literal parses back into its document`() {
        val index = LexiconCodegen.generateSchemaIndex(listOf(TRICKY), packageName = "com.example.models", objectName = "Schemas")

        val document = LexiconParser.parse(literals(index).single())

        assertEquals("app.logdate.aaa", document.id.toString())
    }

    /** Reads each generated string literal back the way the Kotlin compiler would. */
    private fun literals(source: String): List<String> {
        val values = mutableListOf<String>()
        var index = source.indexOf("listOf(")
        while (true) {
            val start = source.indexOf('"', index)
            if (start < 0) return values
            val value = StringBuilder()
            var position = start + 1
            while (source[position] != '"') {
                val character = source[position]
                if (character == '\\') {
                    position++
                    value.append(
                        when (val escaped = source[position]) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> escaped
                        },
                    )
                } else {
                    check(character != '$') { "An unescaped $ would be read as a template" }
                    value.append(character)
                }
                position++
            }
            values += value.toString()
            index = position + 1
        }
    }

    private fun minified(json: String): String = Json.parseToJsonElement(json).toString()

    private companion object {
        val ENTRY =
            """
            {"lexicon":1,"id":"app.logdate.journal.entry","defs":{
              "main":{"type":"record","key":"tid","record":{"type":"object","properties":{"text":{"type":"string"}}}}
            }}
            """.trimIndent()

        // A description holding every character a Kotlin string literal treats specially.
        val TRICKY =
            "{\"lexicon\":1,\"id\":\"app.logdate.aaa\",\"description\":\"Costs \$5, says \\\"hi\\\" \\\\ there\\n\\tok\"," +
                "\"defs\":{\"main\":{\"type\":\"token\"}}}"
    }
}
