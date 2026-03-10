package studio.hypertext.atproto.lexicon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LexiconParserTest {
    @Test
    fun `parser reads query params and output schemas`() {
        val document = LexiconParser.parse(resolveHandleLexicon)
        val mainDefinition = document.definitions.getValue("main")
        val parameters = assertNotNull(mainDefinition.parameters)
        val outputSchema = assertNotNull(assertNotNull(mainDefinition.output).schema)

        assertEquals(1, document.lexicon)
        assertEquals("com.atproto.identity.resolveHandle", document.id.toString())
        assertEquals(LexiconType.QUERY, mainDefinition.type)
        assertEquals(LexiconType.PARAMS, parameters.type)
        assertEquals(LexiconType.STRING, parameters.properties.getValue("handle").type)
        assertEquals(LexiconType.OBJECT, outputSchema.type)
    }

    @Test
    fun `validator resolves local and cross document refs through query and procedure schemas`() {
        val defs = LexiconParser.parse(repoDefsLexicon)
        val validDocument = LexiconParser.parse(createRecordLexicon)
        val invalidDocument = LexiconParser.parse(invalidRefLexicon)
        val registry = LexiconRegistry(listOf(defs))

        val validResult = LexiconValidator.validate(validDocument, registry)
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
    fun `codegen emits params input output and cross document ref types`() {
        val document = LexiconParser.parse(createRecordLexicon)

        val generated = LexiconCodegen.generate(document)

        assertEquals(generated, LexiconCodegen.generate(document))
        assertTrue(generated.contains("public object CreateRecordLexicon"))
        assertTrue(generated.contains("public data class CreateRecordInput("))
        assertTrue(generated.contains("public data class CreateRecordOutput("))
        assertTrue(generated.contains("val repo: String"))
        assertTrue(generated.contains("val record: JsonElement"))
        assertTrue(generated.contains("val commit: DefsCommitMeta?"))
        assertTrue(generated.contains("val validationStatus: String?"))
    }

    @Test
    fun `parser reads blob field types and codegen stays deterministic`() {
        val document = LexiconParser.parse(uploadBlobLexicon)
        val outputSchema = assertNotNull(assertNotNull(document.definitions.getValue("main").output).schema)
        val generated = LexiconCodegen.generate(document)

        assertEquals(LexiconType.BLOB, outputSchema.properties.getValue("blob").type)
        assertTrue(generated.contains("public object UploadBlobLexicon"))
        assertTrue(generated.contains("public data class UploadBlobOutput("))
        assertTrue(generated.contains("val blob: JsonElement"))
    }

    private companion object {
        val resolveHandleLexicon: String =
            """
            {
              "lexicon": 1,
              "id": "com.atproto.identity.resolveHandle",
              "defs": {
                "main": {
                  "type": "query",
                  "parameters": {
                    "type": "params",
                    "required": ["handle"],
                    "properties": {
                      "handle": { "type": "string" }
                    }
                  },
                  "output": {
                    "encoding": "application/json",
                    "schema": {
                      "type": "object",
                      "required": ["did"],
                      "properties": {
                        "did": { "type": "string" }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val repoDefsLexicon: String =
            """
            {
              "lexicon": 1,
              "id": "com.atproto.repo.defs",
              "defs": {
                "commitMeta": {
                  "type": "object",
                  "required": ["cid", "rev"],
                  "properties": {
                    "cid": { "type": "string" },
                    "rev": { "type": "string" }
                  }
                }
              }
            }
            """.trimIndent()

        val createRecordLexicon: String =
            """
            {
              "lexicon": 1,
              "id": "com.atproto.repo.createRecord",
              "defs": {
                "main": {
                  "type": "procedure",
                  "input": {
                    "encoding": "application/json",
                    "schema": {
                      "type": "object",
                      "required": ["repo", "collection", "record"],
                      "properties": {
                        "repo": { "type": "string" },
                        "collection": { "type": "string" },
                        "rkey": { "type": "string" },
                        "record": { "type": "unknown" }
                      }
                    }
                  },
                  "output": {
                    "encoding": "application/json",
                    "schema": {
                      "type": "object",
                      "required": ["uri", "cid"],
                      "properties": {
                        "uri": { "type": "string" },
                        "cid": { "type": "string" },
                        "commit": { "type": "ref", "ref": "com.atproto.repo.defs#commitMeta" },
                        "validationStatus": { "type": "string", "knownValues": ["valid", "unknown"] }
                      }
                    }
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
                  "type": "query",
                  "output": {
                    "encoding": "application/json",
                    "schema": {
                      "type": "object",
                      "properties": {
                        "record": { "type": "ref", "ref": "#missing" }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val uploadBlobLexicon: String =
            """
            {
              "lexicon": 1,
              "id": "com.atproto.repo.uploadBlob",
              "defs": {
                "main": {
                  "type": "procedure",
                  "input": {
                    "encoding": "*/*"
                  },
                  "output": {
                    "encoding": "application/json",
                    "schema": {
                      "type": "object",
                      "required": ["blob"],
                      "properties": {
                        "blob": { "type": "blob" }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()
    }
}
