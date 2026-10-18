package studio.hypertext.atproto.lexicon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.syntax.Nsid
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * LogDate's schema family and the community schemas it reuses are the real input the generator
 * has to handle, so every one of them must parse and pass the checker.
 */
class LogDateLexiconFamilyJvmTest {
    @Test
    fun `every LogDate and community schema parses and passes the checker`() {
        val documents = schemaFiles().map { LexiconParser.parse(it.readText()) }
        val registry = LexiconRegistry(documents)

        val issues =
            documents.flatMap { document ->
                LexiconValidator.validate(document, registry).issues.map { "${it.path}: ${it.message}" }
            }

        assertTrue(documents.size > 40, "Expected the whole family, found ${documents.size} schemas")
        assertEquals(emptyList(), issues)
    }

    @Test
    fun `every LogDate record type is parsed as a record with a key rule`() {
        val records =
            schemaFiles()
                .map { LexiconParser.parse(it.readText()) }
                .filter { it.id.toString().startsWith("app.logdate.") }
                .mapNotNull { document -> document.definitions["main"]?.takeIf { it.type == LexiconType.RECORD } }

        assertTrue(records.isNotEmpty())
        assertTrue(records.all { it.recordKey != null && it.record?.type == LexiconType.OBJECT })
    }

    @Test
    fun `every example record passes the record validator and a corrupted copy fails`() {
        val validator = LexiconRecordValidator(LexiconRegistry(schemaFiles().map { LexiconParser.parse(it.readText()) }))
        val examples =
            Files.walk(moduleRoot().resolve("../../tools/lexicon-check/examples").normalize()).use { paths ->
                paths
                    .asSequence()
                    .filter { it.extension == "json" }
                    .sorted()
                    .toList()
            }
        assertTrue(examples.isNotEmpty())

        examples.forEach { path ->
            val example = Json.parseToJsonElement(path.readText()).jsonObject
            val collection = Nsid.require(example.getValue("collection").jsonPrimitive.content)
            val record = example.getValue("record").jsonObject

            assertEquals(emptyList(), validator.validateRecord(collection, record).issues, path.name)

            val corrupted = JsonObject(record + ("\$type" to JsonPrimitive("app.logdate.not.this")))
            assertTrue(validator.validateRecord(collection, corrupted).issues.isNotEmpty(), "${path.name} with a wrong \$type")
        }
    }

    private fun schemaFiles(): List<Path> {
        val root = moduleRoot().resolve("../../lexicons").normalize()
        return Files.walk(root).use { paths ->
            paths
                .asSequence()
                .filter { it.extension == "json" && it.name !in setOf("activity-streams.json", "published.json") }
                .sorted()
                .toList()
        }
    }

    private fun moduleRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("src/commonMain/resources/com/atproto")) }
}
