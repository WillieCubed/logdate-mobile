package studio.hypertext.atproto.lexicon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import studio.hypertext.atproto.syntax.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Checking a record against its schema: every rule names the field it failed on, and data the
 * schema doesn't know about, from a newer version, is accepted.
 */
class LexiconRecordValidatorTest {
    private val validator = LexiconRecordValidator(LexiconRegistry(listOf(LexiconParser.parse(NOTE_LEXICON))))

    @Test
    fun `a record that follows its schema is valid`() {
        assertEquals(emptyList(), issues(VALID_NOTE))
    }

    @Test
    fun `fields and union members from a newer schema version are accepted`() {
        val newer =
            VALID_NOTE
                .replace("\"title\": \"Lake\",", "\"title\": \"Lake\", \"mood\": \"calm\",")
                .replace(
                    "\"blocks\": [",
                    "\"blocks\": [{\"\$type\": \"com.example.note#stickerBlock\", \"sticker\": \"party\"}, ",
                )

        assertEquals(emptyList(), issues(newer))
    }

    @Test
    fun `a missing required field is reported`() {
        assertEquals(
            listOf("createdAt: required field is missing"),
            issues(VALID_NOTE.without("\"createdAt\": \"2026-10-17T10:00:00.000Z\",")),
        )
    }

    @Test
    fun `a value of the wrong type is reported`() {
        assertEquals(listOf("rating: expected an integer"), issues(VALID_NOTE.replace("\"rating\": 3", "\"rating\": \"three\"")))
        assertEquals(listOf("title: expected a string"), issues(VALID_NOTE.replace("\"title\": \"Lake\"", "\"title\": 7")))
    }

    @Test
    fun `null is only accepted for nullable fields`() {
        assertEquals(emptyList(), issues(VALID_NOTE.replace("\"summary\": \"Short\"", "\"summary\": null")))
        assertEquals(listOf("title: null isn't allowed"), issues(VALID_NOTE.replace("\"title\": \"Lake\"", "\"title\": null")))
    }

    @Test
    fun `string length and grapheme limits are enforced`() {
        assertEquals(
            listOf("title: longer than 20 bytes"),
            issues(VALID_NOTE.replace("\"title\": \"Lake\"", "\"title\": \"${"a".repeat(21)}\"")),
        )
        // Five family emoji are five graphemes but many code points and bytes.
        val family = "👨‍👩‍👧"
        assertEquals(emptyList(), issues(VALID_NOTE.replace("\"tag\": \"ok\"", "\"tag\": \"${family.repeat(5)}\"")))
        assertEquals(
            listOf("tag: longer than 5 graphemes"),
            issues(VALID_NOTE.replace("\"tag\": \"ok\"", "\"tag\": \"${family.repeat(6)}\"")),
        )
    }

    @Test
    fun `string formats are checked`() {
        assertEquals(
            listOf("createdAt: not a valid datetime"),
            issues(VALID_NOTE.replace("2026-10-17T10:00:00.000Z", "yesterday")),
        )
        assertEquals(
            listOf("author: not a valid did"),
            issues(VALID_NOTE.replace("\"author\": \"did:plc:abc123\"", "\"author\": \"alice\"")),
        )
        assertEquals(
            listOf("source: not a valid at-uri"),
            issues(VALID_NOTE.replace("at://did:plc:abc123/com.example.note/3k2a", "https://example.com")),
        )
    }

    @Test
    fun `integer ranges and closed enums are enforced`() {
        assertEquals(listOf("rating: less than the minimum of 1"), issues(VALID_NOTE.replace("\"rating\": 3", "\"rating\": 0")))
        assertEquals(listOf("rating: more than the maximum of 5"), issues(VALID_NOTE.replace("\"rating\": 3", "\"rating\": 9")))
        assertEquals(
            listOf("visibility: \"everyone\" isn't one of the allowed values"),
            issues(VALID_NOTE.replace("\"visibility\": \"private\"", "\"visibility\": \"everyone\"")),
        )
    }

    @Test
    fun `array limits are enforced and each item is checked`() {
        assertEquals(
            listOf("blocks[0].text: expected a string"),
            issues(VALID_NOTE.replace("\"text\": \"Pancakes\"", "\"text\": 1")),
        )
        assertEquals(listOf("blocks: fewer than 1 items"), issues(VALID_NOTE.replace(Regex("\"blocks\": \\[.*?\\],"), "\"blocks\": [],")))
    }

    @Test
    fun `a union member needs a type and a closed union refuses others`() {
        assertEquals(
            listOf("blocks[0]: a union member must have a \$type"),
            issues(VALID_NOTE.replace("\"\$type\": \"com.example.note#textBlock\", ", "")),
        )
        assertEquals(
            listOf("pick: \$type com.example.note#other isn't allowed in this closed union"),
            issues(VALID_NOTE.replace("\"\$type\": \"com.example.note#place\"", "\"\$type\": \"com.example.note#other\"")),
        )
    }

    @Test
    fun `blobs are checked against their size and type limits`() {
        assertEquals(
            listOf("blocks[1].image: image/gif isn't an accepted type"),
            issues(VALID_NOTE.replace("\"mimeType\": \"image/jpeg\"", "\"mimeType\": \"image/gif\"")),
        )
        assertEquals(
            listOf("blocks[1].image: 5000 bytes is more than the maximum of 1000"),
            issues(VALID_NOTE.replace("\"size\": 512", "\"size\": 5000")),
        )
        assertEquals(
            listOf("blocks[1].image: not a valid blob"),
            issues(VALID_NOTE.replace("\"\$type\": \"blob\", ", "")),
        )
    }

    @Test
    fun `bytes and content links are checked`() {
        assertEquals(listOf("digest: longer than 4 bytes"), issues(VALID_NOTE.replace("\"AQID\"", "\"AQIDBAU=\"")))
        assertEquals(listOf("digest: not valid bytes"), issues(VALID_NOTE.replace("{\"\$bytes\": \"AQID\"}", "\"AQID\"")))
        assertEquals(
            listOf("link: not a valid content link"),
            issues(VALID_NOTE.replace("\"link\": {\"\$link\": \"bafkreie\"}", "\"link\": \"bafkreie\"")),
        )
    }

    @Test
    fun `a record whose type is wrong or unknown is reported`() {
        assertEquals(
            listOf("\$type: com.example.other doesn't match the collection com.example.note"),
            issues(VALID_NOTE.replace("\"\$type\": \"com.example.note\"", "\"\$type\": \"com.example.other\"")),
        )
        val unknown = validator.validateRecord(Nsid.require("com.example.missing"), JsonObject(emptyMap()))
        assertEquals(listOf(": com.example.missing isn't a known record type"), unknown.issues.map { "${it.path}: ${it.message}" })
    }

    private fun issues(record: String): List<String> =
        validator
            .validateRecord(Nsid.require("com.example.note"), Json.parseToJsonElement(record).jsonObject)
            .issues
            .map { "${it.path}: ${it.message}" }

    private fun String.without(text: String): String = replace(text, "")

    private companion object {
        val NOTE_LEXICON =
            """
            {"lexicon": 1, "id": "com.example.note", "defs": {
              "main": {"type": "record", "key": "tid", "record": {"type": "object",
                "required": ["title", "createdAt", "blocks"],
                "nullable": ["summary"],
                "properties": {
                  "title": {"type": "string", "maxLength": 20},
                  "summary": {"type": "string"},
                  "tag": {"type": "string", "maxGraphemes": 5},
                  "createdAt": {"type": "string", "format": "datetime"},
                  "author": {"type": "string", "format": "did"},
                  "source": {"type": "string", "format": "at-uri"},
                  "rating": {"type": "integer", "minimum": 1, "maximum": 5},
                  "visibility": {"type": "string", "enum": ["private", "circle"]},
                  "blocks": {"type": "array", "minLength": 1, "items": {"type": "union", "refs": ["#textBlock", "#imageBlock"]}},
                  "pick": {"type": "union", "closed": true, "refs": ["#place"]},
                  "digest": {"type": "bytes", "maxLength": 4},
                  "link": {"type": "cid-link"}
                }}},
              "textBlock": {"type": "object", "required": ["text"], "properties": {"text": {"type": "string"}}},
              "imageBlock": {"type": "object", "required": ["image"], "properties": {
                "image": {"type": "blob", "accept": ["image/jpeg", "image/png"], "maxSize": 1000}}},
              "place": {"type": "object", "properties": {"name": {"type": "string"}}}
            }}
            """.trimIndent()

        val VALID_NOTE =
            """
            {
              "${'$'}type": "com.example.note",
              "title": "Lake",
              "summary": "Short",
              "tag": "ok",
              "createdAt": "2026-10-17T10:00:00.000Z",
              "author": "did:plc:abc123",
              "source": "at://did:plc:abc123/com.example.note/3k2a",
              "rating": 3,
              "visibility": "private",
              "blocks": [{"${'$'}type": "com.example.note#textBlock", "text": "Pancakes"}, {"${'$'}type": "com.example.note#imageBlock", "image": {"${'$'}type": "blob", "ref": {"${'$'}link": "bafkreie"}, "mimeType": "image/jpeg", "size": 512}}],
              "pick": {"${'$'}type": "com.example.note#place", "name": "Home"},
              "digest": {"${'$'}bytes": "AQID"},
              "link": {"${'$'}link": "bafkreie"}
            }
            """.trimIndent()
    }
}
