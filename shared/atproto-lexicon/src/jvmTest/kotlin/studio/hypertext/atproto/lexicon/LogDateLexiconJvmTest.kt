package studio.hypertext.atproto.lexicon

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogDateLexiconJvmTest {
    @Test
    fun `logdate lexicon documents validate and generated sources stay in sync`() {
        val documents =
            lexiconResources.map { resourcePath ->
                val source =
                    requireNotNull(javaClass.classLoader.getResource(resourcePath)) {
                        "Missing lexicon resource $resourcePath"
                    }.readText()
                LexiconParser.parse(source)
            }
        val registry = LexiconRegistry(documents)

        documents.forEach { document ->
            val validation = LexiconValidator.validate(document, registry)
            assertTrue(validation.isValid, validation.issues.joinToString { issue -> "${issue.path}: ${issue.message}" })

            val expected =
                moduleRoot()
                    .resolve("src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/logdate/${document.classFileName()}")
                    .readText()
                    .trimEnd()
            val generated = LexiconCodegen.generate(document, packageName = GENERATED_PACKAGE).trimEnd()

            assertEquals(expected, generated, "Generated source drifted for ${document.id}")
        }
    }

    @Test
    fun `codegen cli rewrites the checked in files without changing their contents`() {
        val outputDir =
            moduleRoot()
                .resolve("src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated/logdate")
        val before = outputDir.resolve("ContentLexicon.kt").readText().trimEnd()

        main(
            arrayOf(
                moduleRoot().resolve("src/commonMain/resources/studio/hypertext/logdate").invariantSeparatorsPathString,
                outputDir.invariantSeparatorsPathString,
                GENERATED_PACKAGE,
            ),
        )

        val after = outputDir.resolve("ContentLexicon.kt").readText().trimEnd()

        assertEquals(before, after)
    }

    private fun moduleRoot(): Path {
        var current: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (current != null) {
            if (Files.exists(current.resolve("build.gradle.kts")) && Files.isDirectory(current.resolve("src/commonMain"))) {
                return current
            }
            current = current.parent
        }
        error("Failed to locate shared/atproto-lexicon module root from ${System.getProperty("user.dir")}")
    }

    private fun LexiconDocument.classFileName(): String =
        "${id.toString().substringAfterLast('.').replaceFirstChar(Char::uppercaseChar)}Lexicon.kt"

    private companion object {
        private const val GENERATED_PACKAGE = "studio.hypertext.atproto.lexicon.generated.logdate"

        private val lexiconResources =
            listOf(
                "studio/hypertext/logdate/association.json",
                "studio/hypertext/logdate/content.json",
                "studio/hypertext/logdate/device.json",
                "studio/hypertext/logdate/entry.json",
                "studio/hypertext/logdate/journal.json",
                "studio/hypertext/logdate/media.json",
                "studio/hypertext/logdate/profile.json",
            )
    }
}
