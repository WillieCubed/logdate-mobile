package studio.hypertext.atproto.lexicon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LexiconParserTest {
    @Test
    fun `parser reads object definitions refs and arrays`() {
        val document = LexiconParser.parse(sampleLexicon)

        assertEquals(1, document.lexicon)
        assertEquals("com.atproto.repo.createRecord", document.id.toString())
        assertEquals(LexiconType.OBJECT, document.definitions.getValue("main").type)
        assertEquals(
            LexiconType.REF,
            document.definitions
                .getValue("main")
                .properties
                .getValue("record")
                .type,
        )
        assertEquals(
            LexiconType.ARRAY,
            document.definitions
                .getValue("main")
                .properties
                .getValue("tags")
                .type,
        )
    }

    @Test
    fun `validator resolves local refs and flags invalid refs`() {
        val validDocument = LexiconParser.parse(sampleLexicon)
        val invalidDocument = LexiconParser.parse(invalidRefLexicon)

        val validResult = LexiconValidator.validate(validDocument)
        val invalidResult = LexiconValidator.validate(invalidDocument)

        assertTrue(validResult.isValid)
        assertFalse(invalidResult.isValid)
        assertTrue(
            invalidResult.issues
                .single()
                .message
                .contains("Unresolved ref target"),
        )
    }

    @Test
    fun `registry resolves cross document refs`() {
        val base = LexiconParser.parse(sampleLexicon)
        val external =
            LexiconParser.parse(
                """
                {
                  "lexicon": 1,
                  "id": "com.atproto.server.describeServer",
                  "defs": {
                    "main": {
                      "type": "object",
                      "properties": {
                        "repo": { "type": "ref", "ref": "com.atproto.repo.createRecord#record" }
                      }
                    }
                  }
                }
                """.trimIndent(),
            )
        val registry = LexiconRegistry(listOf(base))

        val result = LexiconValidator.validate(external, registry)

        assertTrue(result.isValid)
    }

    @Test
    fun `codegen is deterministic and uses typed fields`() {
        val document = LexiconParser.parse(sampleLexicon)

        val generated = LexiconCodegen.generate(document)

        assertEquals(generated, LexiconCodegen.generate(document))
        assertTrue(generated.contains("public object CreateRecordLexicon"))
        assertTrue(generated.contains("public data class CreateRecord("))
        assertTrue(generated.contains("val repo: String"))
        assertTrue(generated.contains("val record: CreateRecordRecord?"))
        assertTrue(generated.contains("val tags: List<String?>?"))
    }

    private companion object {
        val sampleLexicon: String =
            """
            {
              "lexicon": 1,
              "id": "com.atproto.repo.createRecord",
              "defs": {
                "main": {
                  "type": "object",
                  "required": ["repo"],
                  "properties": {
                    "repo": { "type": "string" },
                    "record": { "type": "ref", "ref": "#record" },
                    "tags": { "type": "array", "items": { "type": "string" } }
                  }
                },
                "record": {
                  "type": "object",
                  "required": ["text"],
                  "properties": {
                    "text": { "type": "string" },
                    "private": { "type": "boolean" }
                  }
                }
              }
            }
            """.trimIndent()

        val invalidRefLexicon: String =
            """
            {
              "lexicon": 1,
              "id": "com.atproto.repo.invalidRecord",
              "defs": {
                "main": {
                  "type": "object",
                  "properties": {
                    "record": { "type": "ref", "ref": "#missing" }
                  }
                }
              }
            }
            """.trimIndent()
    }
}
