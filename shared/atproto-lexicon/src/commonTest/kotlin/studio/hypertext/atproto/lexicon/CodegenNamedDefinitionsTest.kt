package studio.hypertext.atproto.lexicon

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A schema can name a string, integer, array or other non-object definition and refer to it, as
 * the community calendar schema does for an event's mode. Those names become type aliases.
 */
class CodegenNamedDefinitionsTest {
    @Test
    fun `named non-object definitions become type aliases that references use`() {
        val document =
            LexiconParser.parse(
                """
                {"lexicon":1,"id":"com.example.event","defs":{
                  "main":{"type":"object","properties":{
                    "mode":{"type":"ref","ref":"#mode"},
                    "tags":{"type":"ref","ref":"#tags"}
                  }},
                  "mode":{"type":"string","description":"How people attend.","knownValues":["inperson","virtual"]},
                  "tags":{"type":"array","items":{"type":"string"}},
                  "inperson":{"type":"token"}
                }}
                """.trimIndent(),
            )

        val generated = LexiconCodegen.generate(document)

        assertTrue(generated.contains("/** How people attend. */\npublic typealias EventMode = String\n"), generated)
        assertTrue(generated.contains("public typealias EventTags = List<String>\n"), generated)
        assertTrue(generated.contains("val mode: EventMode? = null,"), generated)
        assertTrue(!generated.contains("EventInperson"), "Tokens are names, not types")
    }
}
