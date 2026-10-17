package studio.hypertext.atproto.lexicon

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
    fun `checked in test fixtures match what the generator writes`() {
        val lexicons = moduleRoot().resolve("../../lexicons").normalize()
        val inputs =
            listOf(
                lexicons.resolve("app/logdate/defs.json"),
                lexicons.resolve("app/logdate/journal/entry.json"),
                lexicons.resolve("app/logdate/crypto/sealed.json"),
                lexicons.resolve("community/lexicon/location/geo.json"),
                moduleRoot().resolve("src/commonTest/lexicons/kitchenSink.json"),
            )
        val checkedIn = moduleRoot().resolve("src/commonTest/kotlin/studio/hypertext/atproto/lexicon/fixtures")
        val regenerated = Files.createTempDirectory("lexicon-fixtures")

        main(
            arrayOf(
                inputs.joinToString(java.io.File.pathSeparator),
                regenerated.toString(),
                "studio.hypertext.atproto.lexicon.fixtures",
            ),
        )

        val expected = Files.list(checkedIn).use { files -> files.asSequence().associate { it.name to it.readText() } }
        val actual = Files.list(regenerated).use { files -> files.asSequence().associate { it.name to it.readText() } }
        assertEquals(expected, actual, "Run ./gradlew :shared:atproto-lexicon:generateLexiconTestFixtures")
    }

    private fun schemaFiles(): List<Path> {
        val root = moduleRoot().resolve("../../lexicons").normalize()
        return Files.walk(root).use { paths ->
            paths
                .asSequence()
                .filter { it.extension == "json" && it.name != "activity-streams.json" }
                .sorted()
                .toList()
        }
    }

    private fun moduleRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("src/commonMain/resources/com/atproto")) }
}
