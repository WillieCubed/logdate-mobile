package studio.hypertext.atproto.lexicon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parsing and schema checks for records, unions, blobs, content links and bytes.
 */
class LexiconRecordsUnionsMediaTest {
    @Test
    fun `a record is parsed with its key rule and its object`() {
        val document = LexiconParser.parse(noteLexicon)
        val main = document.definitions.getValue("main")

        assertEquals(LexiconType.RECORD, main.type)
        assertEquals(LexiconRecordKey.Tid, main.recordKey)
        val record = assertNotNull(main.record)
        assertEquals(LexiconType.OBJECT, record.type)
        assertEquals(setOf("blocks", "createdAt"), record.properties.filterValues { it.required }.keys)
    }

    @Test
    fun `every record key rule is recognized`() {
        assertEquals(LexiconRecordKey.Tid, LexiconRecordKey.parse("tid"))
        assertEquals(LexiconRecordKey.Nsid, LexiconRecordKey.parse("nsid"))
        assertEquals(LexiconRecordKey.Any, LexiconRecordKey.parse("any"))
        assertEquals(LexiconRecordKey.Literal("self"), LexiconRecordKey.parse("literal:self"))
        assertNull(LexiconRecordKey.parse("literal:"))
        assertNull(LexiconRecordKey.parse("uuid"))
    }

    @Test
    fun `a union keeps its refs and whether it is closed`() {
        val document = LexiconParser.parse(noteLexicon)
        val blocks = assertNotNull(document.definitions.getValue("main").record).properties.getValue("blocks")
        val union = assertNotNull(blocks.items)

        assertEquals(LexiconType.UNION, union.type)
        assertEquals(listOf("#textBlock", "#imageBlock"), union.refs.map { "#${it.definitionName}" })
        assertEquals(false, union.closed)
    }

    @Test
    fun `blob bytes and content link limits are parsed`() {
        val document = LexiconParser.parse(noteLexicon)
        val image = document.definitions.getValue("imageBlock").properties
        val media = image.getValue("media")
        val thumbHash = image.getValue("thumbHash")
        val source = image.getValue("source")

        assertEquals(LexiconType.BLOB, media.type)
        assertEquals(listOf("image/*"), media.accept)
        assertEquals(1_000_000L, media.maxSize)
        assertEquals(LexiconType.BYTES, thumbHash.type)
        assertEquals(1L, thumbHash.minLength)
        assertEquals(64L, thumbHash.maxLength)
        assertEquals(LexiconType.CID_LINK, source.type)
    }

    @Test
    fun `a well formed schema using every new kind passes the checker`() {
        val result = LexiconValidator.validate(LexiconParser.parse(noteLexicon))

        assertTrue(result.isValid, result.issues.joinToString { "${it.path}: ${it.message}" })
    }

    @Test
    fun `a record without a usable key rule is reported`() {
        assertIssue(recordLexicon(key = null), "#main", "Records must declare a key")
        assertIssue(recordLexicon(key = "\"uuid\""), "#main", "Unknown record key rule 'uuid'")
    }

    @Test
    fun `a record whose record is not an object is reported`() {
        assertIssue(
            """{"lexicon":1,"id":"com.example.bad","defs":{"main":{"type":"record","key":"any","record":{"type":"string"}}}}""",
            "#main",
            "A record's record must be an object",
        )
    }

    @Test
    fun `a primary type defined outside main is reported`() {
        assertIssue(
            """
            {"lexicon":1,"id":"com.example.bad","defs":{
              "main":{"type":"object","properties":{}},
              "other":{"type":"record","key":"any","record":{"type":"object","properties":{}}}
            }}
            """.trimIndent(),
            "#other",
            "record definitions must be named main",
        )
    }

    @Test
    fun `a union ref that does not resolve or points at the wrong kind is reported`() {
        assertIssue(unionLexicon(refs = "\"#missing\""), "#main.choice", "Unresolved ref target com.example.union#missing")
        assertIssue(unionLexicon(refs = "\"#word\""), "#main.choice", "Union member com.example.union#word must be an object or a record")
    }

    @Test
    fun `an empty closed union is reported but an empty open one is not`() {
        assertIssue(unionLexicon(refs = "", closed = true), "#main.choice", "A closed union must list at least one ref")
        val open = LexiconValidator.validate(LexiconParser.parse(unionLexicon(refs = "")))
        assertTrue(open.isValid, open.issues.joinToString { it.message })
    }

    @Test
    fun `malformed blob and bytes limits are reported`() {
        assertIssue(blobLexicon(accept = "\"image\""), "#main.file", "Blob accept entry 'image' must be a MIME type, type/* or */*")
        assertIssue(blobLexicon(accept = "\"text/ht*\""), "#main.file", "Blob accept entry 'text/ht*' must be a MIME type, type/* or */*")
        assertIssue(blobLexicon(maxSize = -1), "#main.file", "maxSize must not be negative")
        assertIssue(bytesLexicon(minLength = 5, maxLength = 2), "#main.data", "minLength 5 is greater than maxLength 2")
        assertIssue(bytesLexicon(minLength = -1, maxLength = 2), "#main.data", "minLength must not be negative")
    }

    @Test
    fun `permission sets are recognized and an unrecognized kind is reported`() {
        val permissionSet =
            LexiconParser.parse(
                """{"lexicon":1,"id":"com.example.auth","defs":{"main":{"type":"permission-set","permissions":[]}}}""",
            )
        assertEquals(LexiconType.PERMISSION_SET, permissionSet.definitions.getValue("main").type)

        assertIssue(
            """{"lexicon":1,"id":"com.example.bad","defs":{"main":{"type":"object","properties":{"x":{"type":"float"}}}}}""",
            "#main.x",
            "Unsupported lexicon type 'float'",
        )
    }

    private fun assertIssue(
        source: String,
        pathSuffix: String,
        message: String,
    ) {
        val document = LexiconParser.parse(source)
        val issues = LexiconValidator.validate(document).issues
        assertTrue(
            issues.any { it.path == "${document.id}$pathSuffix" && it.message == message },
            "Expected '$message' at ${document.id}$pathSuffix, got: ${issues.joinToString { "${it.path}: ${it.message}" }}",
        )
    }

    private fun recordLexicon(key: String?): String {
        val keyField = key?.let { "\"key\":$it," }.orEmpty()
        val record = """"record":{"type":"object","properties":{}}"""
        return """{"lexicon":1,"id":"com.example.bad","defs":{"main":{"type":"record",$keyField$record}}}"""
    }

    private fun unionLexicon(
        refs: String,
        closed: Boolean = false,
    ): String =
        """
        {"lexicon":1,"id":"com.example.union","defs":{
          "main":{"type":"object","properties":{"choice":{"type":"union","closed":$closed,"refs":[$refs]}}},
          "word":{"type":"string"}
        }}
        """.trimIndent()

    private fun blobLexicon(
        accept: String = "\"image/png\"",
        maxSize: Long = 10,
    ): String =
        """{"lexicon":1,"id":"com.example.blob","defs":{"main":{"type":"object","properties":{"file":{"type":"blob","accept":[$accept],"maxSize":$maxSize}}}}}"""

    private fun bytesLexicon(
        minLength: Long,
        maxLength: Long,
    ): String =
        """{"lexicon":1,"id":"com.example.bytes","defs":{"main":{"type":"object","properties":{"data":{"type":"bytes","minLength":$minLength,"maxLength":$maxLength}}}}}"""

    private companion object {
        val noteLexicon =
            """
            {
              "lexicon": 1,
              "id": "com.example.note",
              "defs": {
                "main": {
                  "type": "record",
                  "key": "tid",
                  "record": {
                    "type": "object",
                    "required": ["blocks", "createdAt"],
                    "properties": {
                      "blocks": {"type": "array", "items": {"type": "union", "refs": ["#textBlock", "#imageBlock"]}},
                      "createdAt": {"type": "string", "format": "datetime"},
                      "extra": {"type": "unknown"}
                    }
                  }
                },
                "textBlock": {
                  "type": "object",
                  "required": ["text"],
                  "properties": {"text": {"type": "string"}}
                },
                "imageBlock": {
                  "type": "object",
                  "required": ["media"],
                  "properties": {
                    "media": {"type": "blob", "accept": ["image/*"], "maxSize": 1000000},
                    "thumbHash": {"type": "bytes", "minLength": 1, "maxLength": 64},
                    "source": {"type": "cid-link"}
                  }
                }
              }
            }
            """.trimIndent()
    }
}
