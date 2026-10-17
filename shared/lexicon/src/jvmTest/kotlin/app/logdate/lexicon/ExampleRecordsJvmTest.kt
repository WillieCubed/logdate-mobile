package app.logdate.lexicon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.lexicon.data.LexiconData
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
 * The example records the schema lint validates are real LogDate data. Each must decode into its
 * generated model and encode back unchanged, through JSON and DAG-CBOR.
 */
class ExampleRecordsJvmTest {
    @Test
    fun `every example record round-trips through its generated model`() {
        val examples = jsonFiles(repositoryRoot().resolve("tools/lexicon-check/examples"))
        assertTrue(examples.isNotEmpty())

        examples.forEach { path ->
            val example = Json.parseToJsonElement(path.readText()).jsonObject
            val collection = example.getValue("collection").jsonPrimitive.content
            val record = example.getValue("record").jsonObject

            @Suppress("UNCHECKED_CAST")
            val serializer =
                requireNotNull(LogDateRecords.records[collection]) { "${path.name}: no generated model for $collection" }
                    as KSerializer<Any>

            val decoded = LexiconData.json.decodeFromJsonElement(serializer, record)

            assertEquals(record, LexiconData.json.encodeToJsonElement(serializer, decoded), "${path.name}: JSON round trip")
            assertEquals(
                decoded,
                LexiconData.decodeFromDagCbor(serializer, LexiconData.encodeToDagCbor(serializer, decoded)),
                "${path.name}: DAG-CBOR round trip",
            )
        }
    }

    @Test
    fun `the record index lists every record schema`() {
        val recordSchemas =
            listOf("lexicons/app/logdate", "lexicons/community")
                .flatMap { jsonFiles(repositoryRoot().resolve(it)) }
                .map { Json.parseToJsonElement(it.readText()).jsonObject }
                .filter { schema ->
                    schema["defs"]
                        ?.jsonObject
                        ?.get("main")
                        ?.jsonObject
                        ?.get("type")
                        ?.jsonPrimitive
                        ?.content == "record"
                }.map { it.getValue("id").jsonPrimitive.content }
                .toSet()

        assertEquals(recordSchemas, LogDateRecords.records.keys)
    }

    private fun jsonFiles(directory: Path): List<Path> =
        Files.walk(directory).use { paths ->
            paths
                .asSequence()
                .filter { it.extension == "json" }
                .sorted()
                .toList()
        }

    private fun repositoryRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.isDirectory(it.resolve("lexicons/app/logdate")) }
}
