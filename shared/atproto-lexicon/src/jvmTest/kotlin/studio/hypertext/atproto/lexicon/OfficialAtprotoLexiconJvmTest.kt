package studio.hypertext.atproto.lexicon

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialAtprotoLexiconJvmTest {
    @Test
    fun `official atproto lexicon documents validate and generated sources stay in sync`() {
        val documents =
            officialLexicons.map { entry ->
                val source =
                    requireNotNull(javaClass.classLoader.getResource(entry.resourcePath)) {
                        "Missing lexicon resource ${entry.resourcePath}"
                    }.readText()
                LexiconParser.parse(source)
            }
        val registry = LexiconRegistry(documents)

        documents.forEach { document ->
            val validation = LexiconValidator.validate(document, registry)
            assertTrue(validation.isValid, validation.issues.joinToString { issue -> "${issue.path}: ${issue.message}" })

            val expected = generatedOutputFile(document).readText().trimEnd()
            val generated = LexiconCodegen.generate(document, packageName = generatedPackageName(document)).trimEnd()

            assertEquals(expected, generated, "Generated source drifted for ${document.id}")
        }
    }

    @Test
    fun `codegen cli rewrites official checked in files without changing contents`() {
        val before =
            officialLexicons.associate {
                it.outputFileName to
                    it
                        .outputDirectory(moduleRoot())
                        .resolve(it.outputFileName)
                        .readText()
                        .trimEnd()
            }

        officialGenerationCases.forEach { case ->
            main(
                arrayOf(
                    case.inputDirectory(moduleRoot()).invariantSeparatorsPathString,
                    case.outputDirectory(moduleRoot()).invariantSeparatorsPathString,
                    case.packageName,
                ),
            )
        }

        val after =
            officialLexicons.associate {
                it.outputFileName to
                    it
                        .outputDirectory(moduleRoot())
                        .resolve(it.outputFileName)
                        .readText()
                        .trimEnd()
            }

        assertEquals(before, after)
    }

    private fun generatedOutputFile(document: LexiconDocument): Path {
        val entry = officialLexicons.firstOrNull { it.resourcePath == resourcePathFor(document) }
        requireNotNull(entry) { "No official lexicon mapping configured for ${document.id}" }
        return entry.outputDirectory(moduleRoot()).resolve(entry.outputFileName)
    }

    private fun generatedPackageName(document: LexiconDocument): String =
        when {
            document.id.toString().startsWith("com.atproto.identity.") -> "studio.hypertext.atproto.lexicon.generated.com.atproto.identity"
            document.id.toString().startsWith("com.atproto.server.") -> "studio.hypertext.atproto.lexicon.generated.com.atproto.server"
            document.id.toString().startsWith("com.atproto.repo.") -> "studio.hypertext.atproto.lexicon.generated.com.atproto.repo"
            document.id.toString().startsWith("com.atproto.sync.") -> "studio.hypertext.atproto.lexicon.generated.com.atproto.sync"
            else -> error("Unsupported official ATProto lexicon package for ${document.id}")
        }

    private fun resourcePathFor(document: LexiconDocument): String = document.id.toString().replace('.', '/') + ".json"

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

    private data class OfficialLexiconEntry(
        val resourcePath: String,
        val outputPackagePath: String,
    ) {
        val outputFileName: String =
            resourcePath
                .substringAfterLast('/')
                .removeSuffix(".json")
                .replaceFirstChar(Char::uppercaseChar) + "Lexicon.kt"

        fun outputDirectory(moduleRoot: Path): Path =
            moduleRoot.resolve("src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated").resolve(outputPackagePath)
    }

    private data class OfficialGenerationCase(
        val inputPath: String,
        val outputPackagePath: String,
        val packageName: String,
    ) {
        fun inputDirectory(moduleRoot: Path): Path = moduleRoot.resolve("src/commonMain/resources").resolve(inputPath)

        fun outputDirectory(moduleRoot: Path): Path =
            moduleRoot.resolve("src/commonMain/kotlin/studio/hypertext/atproto/lexicon/generated").resolve(outputPackagePath)
    }

    private companion object {
        val officialLexicons =
            listOf(
                OfficialLexiconEntry("com/atproto/identity/resolveHandle.json", "com/atproto/identity"),
                OfficialLexiconEntry("com/atproto/server/describeServer.json", "com/atproto/server"),
                OfficialLexiconEntry("com/atproto/repo/createRecord.json", "com/atproto/repo"),
                OfficialLexiconEntry("com/atproto/repo/defs.json", "com/atproto/repo"),
                OfficialLexiconEntry("com/atproto/repo/deleteRecord.json", "com/atproto/repo"),
                OfficialLexiconEntry("com/atproto/repo/describeRepo.json", "com/atproto/repo"),
                OfficialLexiconEntry("com/atproto/repo/getRecord.json", "com/atproto/repo"),
                OfficialLexiconEntry("com/atproto/repo/listRecords.json", "com/atproto/repo"),
                OfficialLexiconEntry("com/atproto/repo/putRecord.json", "com/atproto/repo"),
                OfficialLexiconEntry("com/atproto/repo/uploadBlob.json", "com/atproto/repo"),
                OfficialLexiconEntry("com/atproto/sync/getBlob.json", "com/atproto/sync"),
            )

        val officialGenerationCases =
            listOf(
                OfficialGenerationCase(
                    inputPath = "com/atproto/identity",
                    outputPackagePath = "com/atproto/identity",
                    packageName = "studio.hypertext.atproto.lexicon.generated.com.atproto.identity",
                ),
                OfficialGenerationCase(
                    inputPath = "com/atproto/server",
                    outputPackagePath = "com/atproto/server",
                    packageName = "studio.hypertext.atproto.lexicon.generated.com.atproto.server",
                ),
                OfficialGenerationCase(
                    inputPath = "com/atproto/repo",
                    outputPackagePath = "com/atproto/repo",
                    packageName = "studio.hypertext.atproto.lexicon.generated.com.atproto.repo",
                ),
                OfficialGenerationCase(
                    inputPath = "com/atproto/sync",
                    outputPackagePath = "com/atproto/sync",
                    packageName = "studio.hypertext.atproto.lexicon.generated.com.atproto.sync",
                ),
            )
    }
}
