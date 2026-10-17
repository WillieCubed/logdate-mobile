package studio.hypertext.atproto.lexicon

import studio.hypertext.atproto.syntax.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Generating a whole schema family needs a package per namespace, because names like `defs`
 * repeat across documents. References across packages are then fully qualified.
 */
class LexiconPackageLayoutTest {
    private val layout = LexiconPackageLayout.byNamespace("com.example.models")

    @Test
    fun `a namespace layout puts each document in the package of its NSID namespace`() {
        assertEquals("com.example.models.app.logdate.journal", layout.packageFor(Nsid.require("app.logdate.journal.entry")))
        assertEquals("com.example.models.app.logdate", layout.packageFor(Nsid.require("app.logdate.defs")))
        assertEquals("com.example.flat", LexiconPackageLayout.flat("com.example.flat").packageFor(Nsid.require("app.logdate.defs")))
    }

    @Test
    fun `a namespace segment that isn't a Kotlin identifier is rejected`() {
        val error = assertFailsWith<IllegalArgumentException> { layout.packageFor(Nsid.require("com.object.thing")) }

        assertEquals("NSID segment 'object' of com.object.thing can't be a Kotlin package name", error.message)
    }

    @Test
    fun `references into another package are qualified and references within one are not`() {
        val entry = LexiconParser.parse(entryLexicon)

        val generated = LexiconCodegen.generate(entry, layout)

        assertTrue(
            generated.startsWith("@file:OptIn(ExperimentalSerializationApi::class)\n\npackage com.example.models.app.logdate.journal\n"),
        )
        assertTrue(generated.contains("val location: com.example.models.app.logdate.DefsLocation? = null,"))
        assertTrue(generated.contains("val value: com.example.models.app.logdate.journal.EntryTextBlock,"))
        assertTrue(generated.contains("EntryTextBlock.serializer(),"))
        assertFalse(generated.contains("com.example.models.app.logdate.journal.EntryTextBlock.serializer()"))
    }

    @Test
    fun `the record index lists every record type with its serializer`() {
        val documents = listOf(LexiconParser.parse(entryLexicon), LexiconParser.parse(defsLexicon))

        val index = LexiconCodegen.generateRecordIndex(documents, layout, packageName = "com.example.models", objectName = "Records")

        assertTrue(index.contains("package com.example.models\n"))
        assertTrue(index.contains("public object Records {"))
        assertTrue(index.contains("\"app.logdate.journal.entry\" to com.example.models.app.logdate.journal.Entry.serializer(),"))
        assertFalse(index.contains("app.logdate.defs\" to"), "defs has no record")
    }

    private companion object {
        val entryLexicon =
            """
            {"lexicon":1,"id":"app.logdate.journal.entry","defs":{
              "main":{"type":"record","key":"tid","record":{"type":"object","required":["blocks"],"properties":{
                "blocks":{"type":"array","items":{"type":"union","refs":["#textBlock"]}},
                "location":{"type":"ref","ref":"app.logdate.defs#location"}
              }}},
              "textBlock":{"type":"object","required":["text"],"properties":{"text":{"type":"string"}}}
            }}
            """.trimIndent()

        val defsLexicon =
            """
            {"lexicon":1,"id":"app.logdate.defs","defs":{
              "location":{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}
            }}
            """.trimIndent()
    }
}
